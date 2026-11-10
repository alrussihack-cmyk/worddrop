const FIRESTORE_FIELDS = [
  "encryptedKey", "iv", "keyHint", "encryptedTtsKey", "ivTts",
  "ttsKeyHint", "model", "workerUrl", "enabled", "updatedAt"
];

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Access-Control-Max-Age": "86400",
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });

    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return handleHealth(env, cors);
    }
    if (request.method === "POST" && url.pathname === "/admin/config") {
      return handleSaveConfig(request, env, cors);
    }
    if (request.method === "POST" && url.pathname === "/") {
      return handleSpeech(request, env, cors);
    }
    return json({ error: "Not found" }, 404, cors);
  },
};

async function handleHealth(env, cors) {
  try {
    const config = await readRuntimeConfig(env);
    return json({
      ok: true,
      service: "wordpop-tts",
      status: "healthy",
      aiConfigured: Boolean(config.encryptedKey && config.iv),
      ttsConfigured: Boolean(
        (config.encryptedTtsKey && config.ivTts) || env.EASYVOICE_API_KEY
      ),
    }, 200, cors);
  } catch {
    // The Worker itself is reachable even if Firestore is temporarily unavailable.
    return json({
      ok: true,
      service: "wordpop-tts",
      status: "healthy",
      aiConfigured: false,
      ttsConfigured: Boolean(env.EASYVOICE_API_KEY),
      configStoreAvailable: false,
    }, 200, cors);
  }
}

async function handleSaveConfig(request, env, cors) {
  const token = request.headers.get("Authorization")?.replace(/^Bearer\s+/i, "").trim();
  if (!token) return json({ error: "Administrator sign-in is required." }, 401, cors);
  if (!env.CONFIG_ENCRYPTION_KEY) {
    return json({ error: "Worker encryption is not configured. Add CONFIG_ENCRYPTION_KEY as a Worker secret." }, 503, cors);
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "Invalid JSON body." }, 400, cors);
  }

  const workerUrl = typeof body.workerUrl === "string" ? body.workerUrl.trim().replace(/\/+$/, "") : "";
  const model = typeof body.model === "string" ? body.model.trim() : "";
  const apiKey = typeof body.apiKey === "string" ? body.apiKey.trim() : "";
  const easyVoiceApiKey = typeof body.easyVoiceApiKey === "string" ? body.easyVoiceApiKey.trim() : "";
  const enabled = body.enabled === true;

  try {
    const parsedUrl = new URL(workerUrl);
    if (parsedUrl.protocol !== "https:") {
      return json({ error: "Worker URL must use HTTPS." }, 400, cors);
    }
  } catch {
    return json({ error: "Enter a valid HTTPS Worker URL." }, 400, cors);
  }
  if (!model || model.length > 120) return json({ error: "Enter a valid OpenRouter model ID (maximum 120 characters)." }, 400, cors);
  if (apiKey.length > 4000 || easyVoiceApiKey.length > 4000) {
    return json({ error: "An API key is too long." }, 413, cors);
  }

  try {
    // Firestore rules validate this Firebase ID token against admins/{uid}.
    const previous = await readRuntimeConfig(env, token);
    let encryptedKey = previous.encryptedKey || "";
    let iv = previous.iv || "";
    let keyHint = previous.keyHint || "";
    let encryptedTtsKey = previous.encryptedTtsKey || "";
    let ivTts = previous.ivTts || "";
    let ttsKeyHint = previous.ttsKeyHint || "";

    if (apiKey) {
      const encrypted = await encryptSecret(apiKey, env.CONFIG_ENCRYPTION_KEY);
      encryptedKey = encrypted.ciphertext;
      iv = encrypted.iv;
      keyHint = maskHint(apiKey);
    } else if (!encryptedKey || !iv) {
      return json({ error: "OpenRouter API key is required for the first setup." }, 400, cors);
    }

    if (easyVoiceApiKey) {
      const encrypted = await encryptSecret(easyVoiceApiKey, env.CONFIG_ENCRYPTION_KEY);
      encryptedTtsKey = encrypted.ciphertext;
      ivTts = encrypted.iv;
      ttsKeyHint = maskHint(easyVoiceApiKey);
    }

    const saved = {
      encryptedKey,
      iv,
      keyHint,
      encryptedTtsKey,
      ivTts,
      ttsKeyHint,
      model,
      workerUrl,
      enabled,
    };

    await writeRuntimeConfig(env, token, saved);
    return json({
      ok: true,
      workerUrl,
      model,
      keyHint,
      keyConfigured: Boolean(encryptedKey && iv),
      ttsKeyConfigured: Boolean(encryptedTtsKey && ivTts),
      ttsKeyHint,
    }, 200, cors);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Could not save API settings.";
    const status = message.includes("403") || message.includes("PERMISSION_DENIED") ? 403
      : message.includes("401") || message.includes("UNAUTHENTICATED") ? 401
      : 502;
    return json({
      error: status === 403
        ? "Administrator access was denied. Check the Firebase admin record and Firestore rules."
        : status === 401
          ? "Your administrator session expired. Sign in again."
          : message,
    }, status, cors);
  }
}

async function handleSpeech(request, env, cors) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400, cors);
  }

  const input = typeof body.input === "string" ? body.input.trim() : "";
  const voice = typeof body.voice === "string" ? body.voice : "af_nova";
  const allowedVoices = new Set([
    "af_aoede", "af_heart", "af_bella", "af_nova", "af_sky",
    "am_adam", "am_eric", "am_michael", "bf_emma", "bm_daniel"
  ]);
  if (!input) return json({ error: "input is required" }, 400, cors);
  if (input.length > 5000) return json({ error: "Text exceeds the free-tier 5,000 character request limit" }, 413, cors);
  if (!allowedVoices.has(voice)) return json({ error: "Voice is not enabled on this Worker" }, 400, cors);

  try {
    const config = await readRuntimeConfig(env);
    const apiKey = config.encryptedTtsKey && config.ivTts
      ? await decryptSecret(config.encryptedTtsKey, config.ivTts, env.CONFIG_ENCRYPTION_KEY)
      : env.EASYVOICE_API_KEY;

    if (!apiKey) return json({ error: "TTS service is not configured. Add an EasyVoice key in WordPop Control." }, 503, cors);

    const upstream = await fetch("https://easyvoice.ae/api/v1/audio/speech", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
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
      return json({ error: "EasyVoice request failed", status: upstream.status, detail: detail.slice(0, 1200) }, upstream.status, cors);
    }
    const headers = new Headers(cors);
    headers.set("Content-Type", upstream.headers.get("Content-Type") || "audio/mpeg");
    headers.set("Cache-Control", "no-store");
    headers.set("X-Content-Type-Options", "nosniff");
    return new Response(upstream.body, { status: 200, headers });
  } catch (error) {
    const message = error instanceof Error ? error.message : "";
    return json({ error: message || "Unable to reach EasyVoice" }, 502, cors);
  }
}

function firestoreBase(env) {
  const projectId = env.FIREBASE_PROJECT_ID || "wordpop-control";
  return `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents`;
}

async function readRuntimeConfig(env, token = "") {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`${firestoreBase(env)}/appConfig/aiRuntime`, {
    method: "GET",
    headers,
  });
  if (response.status === 404) return {};
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Firestore read failed (HTTP ${response.status}): ${detail.slice(0, 300)}`);
  }
  const document = await response.json();
  const fields = document.fields || {};
  const getString = (name) => typeof fields[name]?.stringValue === "string" ? fields[name].stringValue : "";
  return {
    encryptedKey: getString("encryptedKey"),
    iv: getString("iv"),
    keyHint: getString("keyHint"),
    encryptedTtsKey: getString("encryptedTtsKey"),
    ivTts: getString("ivTts"),
    ttsKeyHint: getString("ttsKeyHint"),
    model: getString("model"),
    workerUrl: getString("workerUrl"),
    enabled: fields.enabled?.booleanValue === true,
  };
}

async function writeRuntimeConfig(env, token, config) {
  const fields = {
    encryptedKey: { stringValue: config.encryptedKey },
    iv: { stringValue: config.iv },
    keyHint: { stringValue: config.keyHint },
    encryptedTtsKey: { stringValue: config.encryptedTtsKey },
    ivTts: { stringValue: config.ivTts },
    ttsKeyHint: { stringValue: config.ttsKeyHint },
    model: { stringValue: config.model },
    workerUrl: { stringValue: config.workerUrl },
    enabled: { booleanValue: config.enabled },
    updatedAt: { timestampValue: new Date().toISOString() },
  };
  const endpoint = new URL(`${firestoreBase(env)}/appConfig/aiRuntime`);
  for (const field of FIRESTORE_FIELDS) endpoint.searchParams.append("updateMask.fieldPaths", field);

  const response = await fetch(endpoint.toString(), {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fields }),
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Firestore write failed (HTTP ${response.status}): ${detail.slice(0, 500)}`);
  }
  return response.json();
}

async function encryptionKey(secret) {
  if (!secret) throw new Error("CONFIG_ENCRYPTION_KEY is missing.");
  let raw;
  try {
    raw = Uint8Array.from(atob(secret.trim()), (character) => character.charCodeAt(0));
  } catch {
    throw new Error("CONFIG_ENCRYPTION_KEY must be a base64-encoded 32-byte key.");
  }
  if (raw.length !== 32) throw new Error("CONFIG_ENCRYPTION_KEY must decode to exactly 32 bytes.");
  return crypto.subtle.importKey("raw", raw, { name: "AES-GCM" }, false, ["encrypt", "decrypt"]);
}

async function encryptSecret(value, secret) {
  const key = await encryptionKey(secret);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    key,
    new TextEncoder().encode(value)
  );
  return {
    ciphertext: toBase64(new Uint8Array(ciphertext)),
    iv: toBase64(iv),
  };
}

async function decryptSecret(ciphertext, ivValue, secret) {
  const key = await encryptionKey(secret);
  const clear = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: fromBase64(ivValue) },
    key,
    fromBase64(ciphertext)
  );
  return new TextDecoder().decode(clear);
}

function toBase64(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value) {
  return Uint8Array.from(atob(value), (character) => character.charCodeAt(0));
}

function maskHint(value) {
  return `••••${value.slice(-4)}`.slice(0, 12);
}

function json(data, status, cors) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...cors,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
