# How small models ask for a skill

Established: 2026-09-28, TASK-033, llama.cpp v0.5.0. Decision: `docs/adr/0001-tool-calling-format.md`.
Evidence: `research/experiments/tool-calling-2026-09-28/README.md`.

- **Each model's own tool format wins across families** (93% of 252 cases over 7 models);
  one Bruce-defined format with a lazy grammar reached 84%. Some families ignore any other
  format: LFM2.5 always writes `[tool()]` (10/36 in Bruce's format, 32/36 native); granite
  writes the call as bare JSON after prose, so a tag-triggered grammar never starts.
- **The dangerous failure is a missed call:** the model answers itself, confidently wrong
  (invented times and dates, wrong arithmetic). Grammars cannot prevent it.
- **Grammar cost is negligible:** −2% to +1% generation speed.
- **A one-line skill list halves the prompt** (about 420 to 230 tokens for six skills) for
  0–3 of 36 cases per model. Measured with Bruce's format only.
- **llama-server needs `preserved_tokens`** when a grammar trigger word is a single special
  token (for example `<tool_call>` in Qwen): otherwise HTTP 400 "Grammar trigger word should
  be marked as preserved token".
- **llama.cpp's `common` links cpp-httplib** (download code); Bruce builds only the chat,
  template, parser and grammar sources so no native network code sits outside the network mode.
