# Signing in to Hugging Face from a native app

Established: 2026-09-28. Sources: `research/sources/hf-oauth-2026-09-28/`.

- Endpoints: `https://huggingface.co/oauth/authorize`, `/oauth/token`, `/oauth/userinfo`.
  PKCE with `S256` is supported.
- A native app must use a **public** OAuth app (no client secret): create it without a
  secret, or delete the secret in the app's settings. A secret shipped in an APK is
  extractable, so Bruce never embeds one. The client ID is public.
- Custom-scheme redirect URIs must match the registered URI exactly. Bruce uses
  `com.bizzeh.bruce:/oauth/huggingface`.
- Scopes: `read-repos` covers only the user's private repos; public **gated** models need
  `gated-repos`. Bruce requests `openid profile read-repos gated-repos`.
- Access tokens last 8 hours by default (`expires_in: 28800`). The docs do not say that the
  authorisation-code flow returns a refresh token, so Bruce stores the expiry and asks the
  user to sign in again after it.
- Resolve (download) URLs redirect to a CDN. The `Authorization` header must not follow that
  redirect; Bruce follows redirects itself when a token is attached and drops the header
  when the host changes.
