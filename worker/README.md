# WordPop Shared API Worker

This Cloudflare Worker is the single server-side gateway for WordPop AI and EasyVoice.

- OpenRouter API keys are encrypted with AES-GCM before being stored in the public-readable Firestore runtime document.
- The encryption secret exists only as a Cloudflare Worker secret.
- WordPop Control is the only app that can update the runtime document. Firestore rules verify the Firebase administrator account.
- The learner app receives only the Worker URL and service status. It never receives the OpenRouter API key.
- The root POST endpoint remains compatible with existing EasyVoice TTS builds.

## One-time setup

1. Deploy this Worker to the existing `wordpop-tts` Cloudflare Worker.
2. In Cloudflare Dashboard, open **Workers & Pages → wordpop-tts → Settings → Variables and Secrets**.
3. Add a secret named `WORDPOP_CONFIG_SECRET`. Use a randomly generated value of at least 32 characters and keep it private. Do not put it in GitHub, Firebase, or either APK. For compatibility with an already-created secret, the Worker also accepts `CONFIG_ENCRYPTION_KEY`.
4. Keep the existing `EASYVOICE_API_KEY` secret if you use EasyVoice.
5. In Firebase Console, publish the updated `firestore.rules` from the WordPop Control repository.
6. Open WordPop Control → **AI & API Settings**. Enter the Worker HTTPS URL, OpenRouter API key, and model, then save.
7. Optionally enter the EasyVoice API key in the same Control screen. If you leave it blank, the existing EASYVOICE_API_KEY Worker secret remains the fallback.
8. Turn on the AI service. Both API keys are encrypted by the Worker and are never sent back to the apps.

## Endpoints

### Admin configuration

`POST /admin/config`

Requires `Authorization: Bearer <Firebase ID token>` from a signed-in active WordPop administrator.

Body:
```json
{
  "apiKey": "sk-or-...",
  "easyVoiceApiKey": "optional EasyVoice key",
  "model": "openai/gpt-4o-mini",
  "workerUrl": "https://wordpop-tts.example.workers.dev",
  "enabled": true
}
```

The API key may be omitted on later updates to retain the current key. The Worker encrypts it with AES-GCM and writes only ciphertext to Firestore. Firestore security rules enforce active-admin access.

### AI generation

`POST /ai/chat`

Body:
```json
{ "prompt": "Return JSON only: ...", "maxTokens": 1200 }
```

The Worker selects the model and key from the protected runtime configuration. The learner app cannot choose or receive either value.

### Health

`GET /health`

Returns service readiness flags without exposing credentials.

### EasyVoice TTS

`POST /`

Body:
```json
{ "input": "Hello there", "voice": "af_nova", "speed": 1.0 }
```

Returns MP3 bytes. The existing EasyVoice key remains a Cloudflare secret.

## Protection notes

- AI, TTS, and admin routes use the Cloudflare Workers Rate Limiting binding `WORDPOP_RATE_LIMITER`: 12 requests per 60 seconds per client IP and route.
- The native binding is configured in `wrangler.toml`. Deploy with Wrangler 4.36.0 or later so the binding is attached to the Worker. The local in-memory fallback is for development only.
- Cloudflare's built-in Worker limiter is approximate and scoped to each Cloudflare location; it is not a strict global quota. For a strict global limit, use a Durable Object-backed limiter. Monitor 429 responses and upstream OpenRouter/EasyVoice usage.
- Run the Worker checks with `npm --prefix worker test`.
- Never put `WORDPOP_CONFIG_SECRET`, `CONFIG_ENCRYPTION_KEY`, or an OpenRouter key in source control or an APK.
- If the encryption secret is rotated, re-enter the OpenRouter key in WordPop Control so the Worker can encrypt it with the new secret.
