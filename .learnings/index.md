# Learnings index

- [robolectric-jdk-module-export.md](robolectric-jdk-module-export.md) — Robolectric fails on JDK 17+ without a module export. Read when Robolectric tests fail at setup or when changing the test JVM.
- [robolectric-max-sdk.md](robolectric-max-sdk.md) — Why `targetSdk` is 36. Read before raising `targetSdk` or upgrading Robolectric.
- [kover-compose-generated-branches.md](kover-compose-generated-branches.md) — Why `@Composable` functions are excluded from the branch gate. Read when writing UI code or changing Kover filters.
- [llama-context-padding.md](llama-context-padding.md) — The real context is larger than requested. Read when sizing contexts or estimating memory.
- [llama-decode-aborts-on-oversized-batch.md](llama-decode-aborts-on-oversized-batch.md) — Why prompts are decoded in chunks. Read before changing native decode code.
- [jni-text-as-utf8-bytes.md](jni-text-as-utf8-bytes.md) — Why text crosses JNI as bytes. Read before adding JNI functions that take or return text.
- [ggml-cpu-has-is-compile-time.md](ggml-cpu-has-is-compile-time.md) — Why Bruce detects CPU features itself, and why the current build is baseline ARMv8. Read before TASK-008/TASK-009 or any CPU performance work.
