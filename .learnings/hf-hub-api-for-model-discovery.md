# What the Hugging Face Hub API gives Bruce for model discovery

Established: 2026-09-27, anonymous requests (no account or token). Archived responses:
`research/sources/hf-hub-api-2026-09-27/` (see `research/index.md`).

- **Search GGUF repositories:** `GET /api/models?search=<text>&filter=gguf&sort=downloads&direction=-1&limit=<n>`
  returns repository ids, download counts and tags.
- **Metadata without downloading:** adding `expand[]=gguf` returns, per repository,
  `gguf.architecture`, `gguf.total` (parameter count) and `gguf.context_length`.
  `expand[]=cardData` adds the licence and base model; `expand[]=gated` says whether access
  is gated. The `gguf` object can include a very long `chat_template`.
- **Files, sizes and hashes:** `GET /api/models/<repo>/tree/<revision>?recursive=true` lists
  each file with its size and, for large files, `lfs.oid` = SHA-256. Without `recursive=true`
  only the top level is listed (checked 2026-09-28: 1 of 23 GGUF files in `ggml-org/models`).
- **Renamed repositories** answer API calls with `307 Temporary Redirect` to the new name
  (seen 2026-09-28 for `ggml-org/models`); clients must follow redirects.
- **Partial downloads:** `GET /<repo>/resolve/<revision>/<file>` with a `Range` header
  redirects (302) to a CDN that answers `206` with `Content-Range`. The redirect response
  carries `X-Linked-Size` and `X-Linked-ETag` (the SHA-256). So Bruce can read only a
  file's GGUF header to get its layer shape for the memory estimate, and can resume a
  download and verify it.
- The redirect's `Location` contains a signed, time-limited query string. Never log it.

Not yet checked: anonymous rate limits, and how gated repositories respond without a
token.
