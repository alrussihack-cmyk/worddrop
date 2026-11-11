import test from "node:test";
import assert from "node:assert/strict";
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
