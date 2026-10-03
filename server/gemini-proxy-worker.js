// Cloudflare Worker: holds the Gemini API key so it never ships inside the APK.
// Deploy:  wrangler secret put GEMINI_API_KEY   (and optionally APP_TOKEN, ALLOWED_MODELS)
// App contract:  POST {model, messages:[{role,content}]}  ->  {text}
// NOT tested here (no network/sandbox access). Model names are never trusted from the client: they must be in ALLOWED_MODELS.
export default {
  async fetch(request, env) {
    if (request.method !== "POST") return new Response("Method Not Allowed", { status: 405 });
    if (env.APP_TOKEN && request.headers.get("X-App-Token") !== env.APP_TOKEN) return new Response("Unauthorized", { status: 401 });
    let body;
    try { body = await request.json(); } catch { return new Response("Bad JSON", { status: 400 }); }
    const allowed = (env.ALLOWED_MODELS || "gemini-3.7-flash").split(",").map(s => s.trim());
    if (!allowed.includes(body.model)) return new Response("Model not allowed", { status: 400 });
    const msgs = Array.isArray(body.messages) ? body.messages.slice(-12) : [];
    if (JSON.stringify(msgs).length > 20000) return new Response("Too large", { status: 413 });
    const system = msgs.filter(m => m.role === "system").map(m => m.content).join("\n");
    const contents = msgs.filter(m => m.role !== "system").map(m => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: String(m.content) }] }));
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(body.model)}:generateContent`;
    const r = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body: JSON.stringify({ systemInstruction: { parts: [{ text: system }] }, contents, generationConfig: { maxOutputTokens: 600, temperature: 0.2 } }),
    });
    if (!r.ok) return new Response("Upstream " + r.status, { status: 502 });
    const j = await r.json();
    const text = j?.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("") || "";
    return new Response(JSON.stringify({ text }), { headers: { "Content-Type": "application/json" } });
  },
};
