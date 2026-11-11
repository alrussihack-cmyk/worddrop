const PROJECT_ID = "wordpop-control";
const FIRESTORE_DOC_URL =
  `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/appConfig/aiRuntime`;
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Firebase-AppCheck",
  "Access-Control-Max-Age": "86400",
};

// Cloudflare's native binding is the production limiter. The local map is only a
// development fallback when running without the binding.
const fallbackBuckets = new Map();
const RATE_LIMIT_WINDOW_MS = 60_000;
const FALLBACK_RATE_LIMIT = 12;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });

    if (url.pathname === "/health" && request.method === "GET") {
      const config = await readRuntimeConfig().catch(() => null);
      return json({
        ok: true,
        aiConfigured: Boolean(config?.encryptedKey && config?.iv && config?.enabled),
        ttsConfigured: Boolean((config?.encryptedTtsKey && config?.ivTts) || env.EASYVOICE_API_KEY),
      }, 200);
    }

    if (url.pathname === "/admin/config" && request.method === "POST") {
      if (!(await allowRequest(request, env, "admin"))) return rateLimited();
      return saveAdminConfig(request, env);
    }

    if (url.pathname === "/ai/chat" && request.method === "POST") {
      if (!(await allowRequest(request, env, "ai"))) return rateLimited();
      if (!(await verifyAppCheckRequest(request, env))) return appCheckRequired();
      return generateAiResponse(request, env);
    }

    // Existing EasyVoice endpoint remains compatible with older WordPop builds.
    if (url.pathname === "/" && request.method === "POST") {
      if (!(await allowRequest(request, env, "tts"))) return rateLimited();
      if (!(await verifyAppCheckRequest(request, env))) return appCheckRequired();
      return generateSpeech(request, env);
    }

    return json({ error: "Not found" }, 404);
  },
};


let appCheckJwksCache = { keys: [], expiresAt: 0 };

async function verifyAppCheckRequest(request, env) {
  const token = request.headers.get("X-Firebase-AppCheck")?.trim() || "";
  const projectNumber = String(env.FIREBASE_PROJECT_NUMBER || "").trim();
  const expectedAppId = String(env.FIREBASE_APP_ID || "").trim();
  if (!token || !/^\d+$/.test(projectNumber) || !expectedAppId) return false;

  try {
    const parts = token.split(".");
    if (parts.length !== 3 || parts.some((part) => !part)) return false;

    const header = decodeBase64UrlJson(parts[0]);
    const claims = decodeBase64UrlJson(parts[1]);
    if (header.alg !== "RS256" || header.typ !== "JWT" || typeof header.kid !== "string") {
      return false;
    }

    const now = Math.floor(Date.now() / 1000);
    const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
    if (
      claims.iss !== `https://firebaseappcheck.googleapis.com/${projectNumber}` ||
      !audiences.includes(`projects/${projectNumber}`) ||
      claims.sub !== expectedAppId ||
      !Number.isFinite(claims.exp) || claims.exp <= now ||
      !Number.isFinite(claims.iat) || claims.iat > now + 60 ||
      (Number.isFinite(claims.nbf) && claims.nbf > now + 60)
    ) {
      return false;
    }

    let jwks = await getAppCheckJwks();
    let jwk = jwks.keys.find((key) => key.kid === header.kid);
    if (!jwk) {
      jwks = await getAppCheckJwks(true);
      jwk = jwks.keys.find((key) => key.kid === header.kid);
    }
    if (!jwk || jwk.kty !== "RSA" || jwk.use !== "sig") return false;

    const publicKey = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"],
    );
    return await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      publicKey,
      decodeBase64Url(parts[2]),
      new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
    );
  } catch {
    // Fail closed: unavailable keys, malformed tokens, and crypto errors are rejected.
    return false;
  }
}

async function getAppCheckJwks(forceRefresh = false) {
  if (!forceRefresh && appCheckJwksCache.expiresAt > Date.now()) {
    return appCheckJwksCache;
  }

  const response = await fetch("https://firebaseappcheck.googleapis.com/v1/jwks", {
    headers: { "Cache-Control": "no-cache" },
  });
  if (!response.ok) throw new Error("Unable to retrieve Firebase App Check signing keys.");
  const body = await response.json();
  if (!Array.isArray(body.keys) || body.keys.length === 0) {
    throw new Error("Firebase App Check signing keys are unavailable.");
  }

  appCheckJwksCache = {
    keys: body.keys,
    // Firebase recommends caching its rotating public keys for up to six hours.
    expiresAt: Date.now() + 6 * 60 * 60 * 1000,
  };
  return appCheckJwksCache;
}

function decodeBase64Url(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  const binary = atob(padded);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function decodeBase64UrlJson(value) {
  return JSON.parse(new TextDecoder().decode(decodeBase64Url(value)));
}

function appCheckRequired() {
  return json({ error: "تعذر التحقق من أمان التطبيق. حدّث WordPop وحاول مرة أخرى." }, 401);
}

async function saveAdminConfig(request, env) {
  const token = bearerToken(request);
  if (!token) return json({ error: "Administrator sign-in is required." }, 401);
  const configSecret = getConfigSecret(env);
  if (configSecret.length < 32) {
    return json({ error: "AI configuration encryption is not configured on the Worker." }, 503);
  }

  let body;
  try { body = await request.json(); }
  catch { return json({ error: "Invalid JSON body." }, 400); }

  const workerUrl = typeof body.workerUrl === "string" ? body.workerUrl.trim().replace(/\/+$/, "") : "";
  const model = typeof body.model === "string" ? body.model.trim() : "";
  const apiKey = typeof body.apiKey === "string" ? body.apiKey.trim() : "";
  const easyVoiceApiKey = typeof body.easyVoiceApiKey === "string" ? body.easyVoiceApiKey.trim() : "";
  const enabled = body.enabled === true;

  try {
    const parsedUrl = new URL(workerUrl);
    if (parsedUrl.protocol !== "https:" || !parsedUrl.hostname) {
      return json({ error: "Worker URL must be a valid HTTPS URL." }, 400);
    }
  } catch {
    return json({ error: "Enter the HTTPS Cloudflare Worker URL." }, 400);
  }

  if (!model || model.length > 120 || !/^[a-zA-Z0-9._:/-]+$/.test(model)) {
    return json({ error: "Enter a valid OpenRouter model ID." }, 400);
  }
  if (apiKey && (apiKey.length < 16 || apiKey.length > 500)) {
    return json({ error: "The OpenRouter key does not look valid." }, 400);
  }
  if (easyVoiceApiKey && (easyVoiceApiKey.length < 12 || easyVoiceApiKey.length > 500)) {
    return json({ error: "The EasyVoice key does not look valid." }, 400);
  }

  const previous = await readRuntimeConfig().catch(() => null);
  let encryptedKey = previous?.encryptedKey || "";
  let iv = previous?.iv || "";
  let keyHint = previous?.keyHint || "";
  let encryptedTtsKey = previous?.encryptedTtsKey || "";
  let ivTts = previous?.ivTts || "";
  let ttsKeyHint = previous?.ttsKeyHint || "";

  if (apiKey) {
    const encrypted = await encryptSecret(apiKey, configSecret);
    encryptedKey = encrypted.ciphertext;
    iv = encrypted.iv;
    keyHint = "••••" + apiKey.slice(-4);
  }
  if (easyVoiceApiKey) {
    const encrypted = await encryptSecret(easyVoiceApiKey, configSecret);
    encryptedTtsKey = encrypted.ciphertext;
    ivTts = encrypted.iv;
    ttsKeyHint = "••••" + easyVoiceApiKey.slice(-4);
  }
  if (!encryptedKey || !iv) {
    return json({ error: "Add the OpenRouter API key before saving for the first time." }, 400);
  }

  // Firestore verifies the supplied Firebase ID token and enforces isActiveAdmin()
  // in firestore.rules. The Worker never trusts a client-provided admin flag.
  const fields = {
    encryptedKey: { stringValue: encryptedKey },
    iv: { stringValue: iv },
    keyHint: { stringValue: keyHint },
    encryptedTtsKey: { stringValue: encryptedTtsKey },
    ivTts: { stringValue: ivTts },
    ttsKeyHint: { stringValue: ttsKeyHint },
    model: { stringValue: model },
    workerUrl: { stringValue: workerUrl },
    enabled: { booleanValue: enabled },
    updatedAt: { timestampValue: new Date().toISOString() },
  };

  const response = await fetch(FIRESTORE_DOC_URL, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fields }),
  });

  if (!response.ok) {
    const detail = (await response.text()).slice(0, 700);
    const status = response.status === 401 || response.status === 403 ? 403 : response.status;
    return json({
      error: status === 403
        ? "Firebase rejected this account. Sign in with an active WordPop administrator."
        : "Could not save AI settings to Firebase.",
      detail,
    }, status);
  }

  return json({
    ok: true,
    keyConfigured: true,
    keyHint,
    ttsKeyConfigured: Boolean(encryptedTtsKey && ivTts),
    ttsKeyHint,
    model,
    workerUrl,
    enabled,
  }, 200);
}

async function generateAiResponse(request, env) {
  const configSecret = getConfigSecret(env);
  if (configSecret.length < 32) {
    return json({ error: "خدمة الذكاء الاصطناعي غير مهيأة على الخادم." }, 503);
  }

  let body;
  try { body = await request.json(); }
  catch { return json({ error: "Invalid JSON body." }, 400); }

  const prompt = typeof body.prompt === "string" ? body.prompt.trim() : "";
  const maxTokens = Number.isInteger(body.maxTokens) ? body.maxTokens : 1200;
  if (!prompt) return json({ error: "prompt is required." }, 400);
  if (prompt.length > 18000) return json({ error: "The prompt is too long." }, 413);
  if (maxTokens < 100 || maxTokens > 8000) return json({ error: "Invalid maxTokens value." }, 400);

  const config = await readRuntimeConfig().catch(() => null);
  if (!config?.enabled || !config.encryptedKey || !config.iv || !config.model) {
    return json({ error: "خدمة الذكاء الاصطناعي غير مفعّلة من WordPop Control." }, 503);
  }

  try {
    const apiKey = await decryptSecret(config.encryptedKey, config.iv, configSecret);
    const upstream = await fetch(OPENROUTER_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        "X-Title": "WordPop",
      },
      body: JSON.stringify({
        model: config.model,
        temperature: 0.8,
        max_tokens: maxTokens,
        messages: [{ role: "user", content: prompt }],
      }),
    });

    const text = await upstream.text();
    if (!upstream.ok) {
      return json({
        error: friendlyOpenRouterError(upstream.status, text),
        status: upstream.status,
      }, upstream.status);
    }

    return new Response(text, {
      status: 200,
      headers: {
        ...cors,
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch (error) {
    return json({ error: error?.message || "Unable to reach the AI service." }, 502);
  }
}

async function generateSpeech(request, env) {
  const runtime = await readRuntimeConfig().catch(() => null);
  let easyVoiceKey = env.EASYVOICE_API_KEY || "";
  const configSecret = getConfigSecret(env);
  if (runtime?.encryptedTtsKey && runtime?.ivTts && configSecret.length >= 32) {
    try {
      easyVoiceKey = await decryptSecret(runtime.encryptedTtsKey, runtime.ivTts, configSecret);
    } catch {
      return json({ error: "EasyVoice key could not be decrypted. Re-enter it in WordPop Control." }, 503);
    }
  }
  if (!easyVoiceKey) {
    return json({ error: "TTS service is not configured in WordPop Control." }, 503);
  }

  let body;
  try { body = await request.json(); }
  catch { return json({ error: "Invalid JSON body." }, 400); }

  const input = typeof body.input === "string" ? body.input.trim() : "";
  const voice = typeof body.voice === "string" ? body.voice : "af_nova";
  const allowedVoices = new Set([
    "af_aoede", "af_heart", "af_bella", "af_nova", "af_sky",
    "am_adam", "am_eric", "am_michael", "bf_emma", "bm_daniel",
  ]);
  if (!input) return json({ error: "input is required." }, 400);
  if (input.length > 5000) return json({ error: "Text exceeds the 5,000 character request limit." }, 413);
  if (!allowedVoices.has(voice)) return json({ error: "Voice is not enabled on this Worker." }, 400);

  try {
    const upstream = await fetch("https://easyvoice.ae/api/v1/audio/speech", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${easyVoiceKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "kokoro-82m",
        input,
        voice,
        response_format: "mp3",
        speed: Math.min(2, Math.max(0.5, Number(body.speed) || 1)),
      }),
    });
    if (!upstream.ok) {
      const detail = await upstream.text();
      return json({ error: "EasyVoice request failed.", status: upstream.status, detail: detail.slice(0, 1000) }, upstream.status);
    }
    const headers = new Headers(cors);
    headers.set("Content-Type", upstream.headers.get("Content-Type") || "audio/mpeg");
    headers.set("Cache-Control", "no-store");
    headers.set("X-Content-Type-Options", "nosniff");
    return new Response(upstream.body, { status: 200, headers });
  } catch {
    return json({ error: "Unable to reach EasyVoice." }, 502);
  }
}

async function readRuntimeConfig() {
  const response = await fetch(FIRESTORE_DOC_URL, { headers: { "Cache-Control": "no-store" } });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error("Could not read WordPop AI configuration.");
  const doc = await response.json();
  const fields = doc.fields || {};
  return {
    encryptedKey: fields.encryptedKey?.stringValue || "",
    iv: fields.iv?.stringValue || "",
    keyHint: fields.keyHint?.stringValue || "",
    encryptedTtsKey: fields.encryptedTtsKey?.stringValue || "",
    ivTts: fields.ivTts?.stringValue || "",
    ttsKeyHint: fields.ttsKeyHint?.stringValue || "",
    model: fields.model?.stringValue || "",
    workerUrl: fields.workerUrl?.stringValue || "",
    enabled: fields.enabled?.booleanValue === true,
  };
}

async function encryptSecret(value, secret) {
  const key = await deriveKey(secret, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    key,
    new TextEncoder().encode(value),
  );
  return { ciphertext: toBase64(new Uint8Array(encrypted)), iv: toBase64(iv) };
}

async function decryptSecret(ciphertext, encodedIv, secret) {
  const key = await deriveKey(secret, ["decrypt"]);
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: fromBase64(encodedIv) },
    key,
    fromBase64(ciphertext),
  );
  return new TextDecoder().decode(decrypted);
}

async function deriveKey(secret, usages) {
  const raw = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret));
  return crypto.subtle.importKey("raw", raw, { name: "AES-GCM" }, false, usages);
}

function toBase64(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value) {
  return Uint8Array.from(atob(value), (character) => character.charCodeAt(0));
}

// WORDPOP_CONFIG_SECRET is the preferred name. CONFIG_ENCRYPTION_KEY is
// accepted as a compatibility alias for an existing Cloudflare secret.
function getConfigSecret(env) {
  return env.WORDPOP_CONFIG_SECRET || env.CONFIG_ENCRYPTION_KEY || "";
}

function bearerToken(request) {
  const header = request.headers.get("Authorization") || "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  return match ? match[1].trim() : "";
}

async function allowRequest(request, env, route) {
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const key = `${route}:${ip}`;
  const limiter = env.WORDPOP_RATE_LIMITER;

  if (limiter && typeof limiter.limit === "function") {
    try {
      const result = await limiter.limit({ key });
      return result?.success === true;
    } catch {
      // Fail closed if the configured production limiter is unavailable.
      return false;
    }
  }

  return allowFallbackRequest(key);
}

function allowFallbackRequest(key) {
  const now = Date.now();
  const current = fallbackBuckets.get(key);
  if (current && now - current.startedAt < RATE_LIMIT_WINDOW_MS) {
    if (current.count >= FALLBACK_RATE_LIMIT) return false;
    current.count += 1;
    return true;
  }

  if (fallbackBuckets.size >= 2_000) {
    for (const [bucketKey, bucket] of fallbackBuckets) {
      if (now - bucket.startedAt >= RATE_LIMIT_WINDOW_MS) fallbackBuckets.delete(bucketKey);
    }
    if (fallbackBuckets.size >= 2_000) return false;
  }

  fallbackBuckets.set(key, { startedAt: now, count: 1 });
  return true;
}

function rateLimited() {
  const response = json({ error: "طلبات كتير في وقت قصير. استنى دقيقة وجرب تاني." }, 429);
  response.headers.set("Retry-After", "60");
  return response;
}

function friendlyOpenRouterError(status, text) {
  if (status === 401 || status === 403) return "مفتاح OpenRouter غير صالح أو غير مسموح له باستخدام الموديل.";
  if (status === 402) return "رصيد OpenRouter غير كافٍ.";
  if (status === 429) return "تم تجاوز حد الطلبات. حاول مرة أخرى بعد قليل.";
  return `OpenRouter request failed (${status}): ${text.slice(0, 300)}`;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...cors,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
