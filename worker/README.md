# WordPop EasyVoice Worker

This Cloudflare Worker keeps the EasyVoice API key on the server. The Android app sends only the text and selected voice to this Worker.

## Deploy

1. Install Node.js, then run `npm install -g wrangler`.
2. From this folder, run `npx wrangler login`.
3. Add your EasyVoice key as a Cloudflare secret (do not commit it):
   ```bash
   npx wrangler secret put EASYVOICE_API_KEY
   ```
   Paste the key created in EasyVoice when prompted.
4. Deploy:
   ```bash
   npx wrangler deploy
   ```
5. Copy the Worker URL, usually `https://wordpop-tts.<your-subdomain>.workers.dev`.
6. Paste that URL in WordPop > Settings > Speech > Cloudflare Worker URL.

## API

POST `/` with JSON:
```json
{ "input": "Hello there", "voice": "af_nova", "speed": 1.0 }
```
Returns MP3 bytes. The Worker currently enables documented free English voices only. The free EasyVoice plan has a shared daily character quota and rate limits; it is not unlimited.
