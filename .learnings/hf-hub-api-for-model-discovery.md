# What the Hugging Face Hub API gives Bruce for model discovery

Established: 2026-09-27, anonymous requests (no account or token); filters, sizes and rate
limits added 2026-09-28. Archived responses: `research/sources/hf-hub-api-2026-09-27/` and
`research/sources/hf-hub-api-2026-09-28-filters/` (see `research/index.md`).

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
- Android's `HttpURLConnection` keeps the `Range` header when it follows that redirect
  (checked on the Xperia 1 II, 2026-09-28, `HubClientOnlineTest`). Caution for signed-in
  requests: do not let an `Authorization` header follow the redirect to the CDN.
- **Server-side filters (2026-09-28, TASK-031):** `pipeline_tag=text-generation` and
  `num_parameters=min:<n>,max:<n>` (units like `1B`, `500M`) both work on search, and combine
  with `filter=gguf`. `num_parameters` filters on `gguf.total`, which describes one file the
  Hub parsed, not the repository: some large repositories report a small draft (MTP) file,
  for example a 27B repository listed at 0.46B. Treat it as a hint.
- **No file sizes in search.** `expand[]=siblings` lists file names only, even with
  `blobs=true`; `usedStorage` is not an allowed expand. `gguf.totalFileSize` is one file's
  size, not a per-file list. Sizes need the per-repository tree call, so Bruce estimates
  size in lists from parameter count and the quantisation in the file name, and shows the
  exact size once a repository is opened (user decision, 2026-09-28).
- **Draft models in the list:** some repositories are listed by a speculative-decoding draft
  file (architecture `dflash` or `eagle3`, around 2B parameters for a 27B model). Bruce treats
  those architectures as not chat models, so they are never recommended.
- **Size estimate accuracy:** parameters × bits per weight (from llama.cpp's
  `tools/quantize/quantize.cpp` Llama-3-8B sizes) runs 0–10% low on real files, because
  embeddings keep higher precision; +5% puts seven real 0.8–4.7B files within 6%
  (`SizeEstimate`).
- **Rate limit:** anonymous API calls are a fixed window of 500 requests per 300 seconds
  (`ratelimit-policy: "fixed window";"api";q=500;w=300`).
- Search is paged with a `Link: <…cursor=…>; rel="next"` header.

Not yet checked: how gated repositories respond without a token.
