# Hub API: chat templates in search results

Retrieved 2026-09-29 from https://huggingface.co/api/models (live responses, not documentation).

- `GET /api/models?filter=gguf&...&expand[]=gguf` returns, for each repository, a `gguf` object with
  `chat_template`, `bos_token`, `eos_token`, `architecture`, `total`, `context_length` and
  `totalFileSize`: one request for the whole result list, no request per repository.
- It is one template per repository (from one of its files).
- Example, search "llama-3.2-1b-instruct", 50 results sorted by downloads: 45 carry Meta's
  3,827-character template (tools supported); hugging-quants Q4_K_M and Q8_0 (348 characters),
  lmstudio-community (346), bartowski/FuseChat (389) and one featherless-ai-quants copy (291)
  carry role-and-text templates without tool support.
- A 50-result response with templates was about 460 KB.
