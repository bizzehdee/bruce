# Using llama.cpp's chat code inside Bruce

Established: 2026-09-28, TASK-036, llama.cpp v0.5.0. Decision: `docs/adr/0001-tool-calling-format.md`.

- **Building only what the chat code needs:** `common` minus `arg`, `download`, `hf-cache`,
  `subproc`, `preset`, `console` and `llguidance` links on its own (target `bruce_chat` in
  `app/src/main/cpp/CMakeLists.txt`). `common.cpp` pulls in `sampling`, `speculative`, `fit`,
  `ngram-*` and `reasoning-budget`, but no network code. `build-info.cpp` is generated from
  `common/build-info.cpp.in` with Bruce's own values, because the git-derived ones live in
  llama.cpp's own `common` CMake, which is off.
- **Special tokens must be rendered as text for the parser.** Bruce rendered pieces with
  `special = false`, so a Qwen `<tool_call>` special token became empty text and the parser saw
  no call. llama-server renders a token as text when it is a preserved token; Bruce now does the
  same (`Generation::preserved` in `llama_jni.cpp`).
- **A trigger word that is one special token must be preserved,** or the lazy grammar is
  refused (llama-server returns HTTP 400 for the same case). Bruce's own format lists
  `<tool_call>` and `</tool_call>` as preserved for this reason.
- **json-schema-to-grammar names its top rule `root`.** For Bruce's format the schema grammar's
  `root` is renamed and a new `root` adds the `<tool_call>` tags around it
  (`toolCallGrammar` in `chat_tools.cpp`).
- **An app's instrumentation test cannot read `/data/local/tmp`**; copy test files into the app's
  own storage with `run-as` (`docs/building.md`).
