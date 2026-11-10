# WordPop shared AI / EasyVoice Worker

This Cloudflare Worker is the secure backend for WordPop Control. It stores OpenRouter and EasyVoice credentials as AES-GCM ciphertext in the `appConfig/aiRuntime` Firestore document. The encryption key stays in a Cloudflare Worker secret and is never sent to Android.

## Configure once

1. Set the Firebase project ID in `wrangler.toml` (currently `wordpop-control`).
2. Generate a random 32-byte encryption key locally:
   ```bash
   openssl rand -base64 32
   ```
   Keep the output private. Do not put it in GitHub, the Android app, or Firestore.
3. From this folder, add it as a Cloudflare Worker secret:
   ```bash
   npx wrangler secret put CONFIG_ENCRYPTION_KEY
   ```
   Paste the generated base64 value when prompted.
4. Optionally keep the legacy EasyVoice fallback secret:
   ```bash
   npx wrangler secret put EASYVOICE_API_KEY
   ```
   New EasyVoice keys can instead be saved through WordPop Control.
5. Deploy:
   ```bash
   npx wrangler deploy
   ```

The Firebase Firestore rules in the `wordpop-control` repository must also be deployed. They allow an active administrator to write `appConfig/aiRuntime`; the Worker passes the administrator's Firebase ID token to Firestore so the rules enforce the admin role.

## Endpoints

### Health check

`GET /health` returns HTTP 200 and reports whether encrypted OpenRouter / EasyVoice settings are configured.

### Save shared API settings

`POST /admin/config` accepts a Firebase ID token in the Authorization header and JSON:

```json
{
  "apiKey": "sk-or-...",
  "easyVoiceApiKey": "your-easyvoice-key",
  "model": "openai/gpt-4o-mini",
  "workerUrl": "https://wordpop-tts.<your-subdomain>.workers.dev",
  "enabled": true
}
```

The OpenRouter key is required on first setup. Leave either key blank on later saves to retain its existing encrypted value. The endpoint never returns the raw keys.

### Text-to-speech

`POST /` with JSON:

```json
{ "input": "Hello there", "voice": "af_nova", "speed": 1.0 }
```

Returns MP3 bytes. It uses the EasyVoice key saved by WordPop Control, falling back to the legacy `EASYVOICE_API_KEY` Worker secret if no shared key has been saved. The Worker currently enables documented free English voices only. The free EasyVoice plan has a shared daily character quota and rate limits; it is not unlimited.

## Security notes

- Never commit API keys or `CONFIG_ENCRYPTION_KEY` to the repository.
- The Firebase ID token is checked by Firestore Security Rules against the active administrator record.
- Only encrypted key material and short masked hints are stored in Firestore.
- OpenRouter requests from the learner app still need to be routed through a server-side proxy before the shared OpenRouter key can be used without exposing it to the APK.
