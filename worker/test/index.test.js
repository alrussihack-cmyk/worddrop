import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import worker from "../src/index.js";

function post(path, ip = "203.0.113.8") {
  return new Request(`https://wordpop-tts.example.workers.dev${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "CF-Connecting-IP": ip,
    },
    body: "{}",
  });
}

function blockedLimiter(keys) {
  return {
    async limit({ key }) {
      keys.push(key);
      return { success: false };
    },
  };
}

test("AI requests are stopped by the Cloudflare limiter before upstream work", async () => {
  const keys = [];
  const response = await worker.fetch(post("/ai/chat"), {
    WORDPOP_RATE_LIMITER: blockedLimiter(keys),
  });

  assert.equal(response.status, 429);
  assert.equal(response.headers.get("Retry-After"), "60");
  assert.deepEqual(keys, ["ai:203.0.113.8"]);
});

test("TTS requests are rate limited before contacting EasyVoice", async () => {
  const keys = [];
  const response = await worker.fetch(post("/"), {
    WORDPOP_RATE_LIMITER: blockedLimiter(keys),
  });

  assert.equal(response.status, 429);
  assert.deepEqual(keys, ["tts:203.0.113.8"]);
});

test("admin configuration writes are rate limited before Firebase access", async () => {
  const keys = [];
  const response = await worker.fetch(post("/admin/config"), {
    WORDPOP_RATE_LIMITER: blockedLimiter(keys),
  });

  assert.equal(response.status, 429);
  assert.deepEqual(keys, ["admin:203.0.113.8"]);
});

test("preflight requests do not consume the rate-limit allowance", async () => {
  let calls = 0;
  const response = await worker.fetch(new Request(
    "https://wordpop-tts.example.workers.dev/ai/chat",
    { method: "OPTIONS" }
  ), {
    WORDPOP_RATE_LIMITER: {
      async limit() {
        calls += 1;
        return { success: false };
      },
    },
  });

  assert.equal(response.status, 200);
  assert.equal(calls, 0);
});

test("AI requests without Firebase App Check are rejected before upstream work", async () => {
  const response = await worker.fetch(post("/ai/chat"), {
    FIREBASE_PROJECT_NUMBER: "922302031640",
    FIREBASE_APP_ID: "1:922302031640:android:373289c5e99f1e19dc206d",
  });

  assert.equal(response.status, 401);
  assert.match((await response.json()).error, /أمان التطبيق/);
});

test("TTS requests without Firebase App Check are rejected before contacting EasyVoice", async () => {
  const response = await worker.fetch(post("/"), {
    FIREBASE_PROJECT_NUMBER: "922302031640",
    FIREBASE_APP_ID: "1:922302031640:android:373289c5e99f1e19dc206d",
  });

  assert.equal(response.status, 401);
});

test("malformed Firebase App Check tokens are rejected", async () => {
  const request = post("/ai/chat");
  request.headers.set("X-Firebase-AppCheck", "not.a.valid-token");

  const response = await worker.fetch(request, {
    FIREBASE_PROJECT_NUMBER: "922302031640",
    FIREBASE_APP_ID: "1:922302031640:android:373289c5e99f1e19dc206d",
  });

  assert.equal(response.status, 401);
});

test("a correctly signed Firebase App Check token passes verification", async () => {
  const projectNumber = "922302031640";
  const appId = "1:922302031640:android:373289c5e99f1e19dc206d";
  const now = Math.floor(Date.now() / 1000);
  const pair = await webcrypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: Uint8Array.from([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  );
  const jwk = await webcrypto.subtle.exportKey("jwk", pair.publicKey);
  Object.assign(jwk, { kid: "wordpop-test-key", use: "sig", alg: "RS256" });

  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const unsigned = [
    encode({ alg: "RS256", typ: "JWT", kid: "wordpop-test-key" }),
    encode({
      iss: `https://firebaseappcheck.googleapis.com/${projectNumber}`,
      aud: [`projects/${projectNumber}`],
      sub: appId,
      iat: now,
      exp: now + 3600,
    }),
  ].join(".");
  const signature = await webcrypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    pair.privateKey,
    new TextEncoder().encode(unsigned),
  );
  const token = `${unsigned}.${Buffer.from(signature).toString("base64url")}`;

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (input) => {
    if (String(input).includes("firebaseappcheck.googleapis.com/v1/jwks")) {
      return new Response(JSON.stringify({ keys: [jwk] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }
    throw new Error("Unexpected network request during valid-token test.");
  };

  try {
    const request = post("/ai/chat", "203.0.113.99");
    request.headers.set("X-Firebase-AppCheck", token);
    const response = await worker.fetch(request, {
      FIREBASE_PROJECT_NUMBER: projectNumber,
      FIREBASE_APP_ID: appId,
    });
    // The request passed App Check and reached the next server-side config check.
    assert.equal(response.status, 503);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
