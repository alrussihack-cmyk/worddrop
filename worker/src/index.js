export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Access-Control-Max-Age": "86400",
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });

    // Lightweight health check used by the WordPop settings screen.
    // Keep this separate from POST /, which generates speech audio.
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "wordpop-tts", status: "healthy" }, 200, cors);
    }

    if (request.method !== "POST") return json({ error: "Method not allowed" }, 405, cors);
    if (!env.EASYVOICE_API_KEY) return json({ error: "TTS service is not configured" }, 503, cors);

    let body;
    try { body = await request.json(); }
    catch { return json({ error: "Invalid JSON body" }, 400, cors); }

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
      const upstream = await fetch("https://easyvoice.ae/api/v1/audio/speech", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.EASYVOICE_API_KEY}`,
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
    } catch {
      return json({ error: "Unable to reach EasyVoice" }, 502, cors);
    }
  },
};

function json(data, status, cors) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...cors, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
