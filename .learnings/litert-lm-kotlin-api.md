LiteRT-LM's real Kotlin API (`com.google.ai.edge.litertlm:litertlm-android:0.19.1`)
differs from what the public docs and sample show, in ways that matter for
compiling against it. Found by `javap`-ing the actual AAR's `classes.jar`
(`~/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litertlm/litertlm-android/0.19.1/`),
not by reading documentation, after the docs and a GitHub sample read during
TASK-117/118 research turned out incomplete or slightly wrong:

- `Message` has no `.text` property. Its content lives in `.contents.contents`,
  a `List<Content>`; extract text with
  `contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }`.
- `Message.Companion` has `String`-accepting convenience overloads for
  `system`, `user` and `model` (not just the `Contents`/`List<ToolCall>`/
  `Map` ones the docs imply) — `Message.user("hi")` works directly.
- LiteRT-LM's own `ToolCall.arguments` is a `Map<String, Any>`, not a JSON
  string; it also carries no call id at all. Bruce's own `ToolCall` needs
  `JSONObject(arguments).toString()` one way and a hand-rolled
  `JSONObject(json).keys().associateWith { ... }` the other way; the missing
  id is fine because `BruceRuntime` already numbers tool calls that arrive
  without one (`StepEvent.Done`'s own doc comment says so).
- `SamplerConfig(topK: Int, topP: Double, temperature: Double, seed: Int = 0)`
  — `topK` and `topP` are required, not defaulted; `temperature` is `Double`,
  not `Float`.
- `ConversationConfig.tools` takes `List<ToolProvider>`, built from an
  `OpenApiTool` (or `ToolSet`) via the top-level `tool(...)` function in
  `ToolKt` — you cannot pass an `OpenApiTool` list directly.
- `EngineConfig`/`ConversationConfig`/`ThinkingConfig` all have real Kotlin
  default parameters (confirmed by the generated `$default` synthetic methods
  and multiple public constructor overloads), so named-argument construction
  skipping trailing parameters works as expected from plain Kotlin, even
  though `javap` makes this easy to misread as "everything is required."
- `Backend.CPU()` has a public no-arg constructor (threadCount/numOfThreads
  both default null); `Backend.GPU()` and `Backend.NPU(nativeLibraryDir)`
  likewise are real public nested classes under the sealed `Backend`.
- `Conversation.cancelProcess()` and `Conversation.getTokenCount()` exist
  (matching their KDoc-described intent from the GitHub source read in
  TASK-117), but `getTokenCount()` is the conversation's current KV-cache
  size, not a per-string token count — there is no tokenizer/count-for-string
  API anywhere in `Engine`, `Conversation`, `Session` or `ModelInfo`.
- `Conversation.sendMessageAsync(Message): Flow<Message>` exists with a full
  set of default-filled overloads; whether each emitted `Message` is an
  incremental delta or the whole cumulative message is still unconfirmed —
  neither `javap` nor the docs settle this, only a real device run would.

Read before: any further LiteRT-LM Kotlin work (TASK-119 GPU/NPU delegates,
TASK-120 pipeline selection). Full implementation:
`app/src/full/java/com/bizzeh/bruce/litert/LiteRtNative.kt`. If the pinned
version changes, re-run `javap` against the new AAR rather than trusting this
file or the public docs to still match.
