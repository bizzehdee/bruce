# Learnings index

- [robolectric-jdk-module-export.md](robolectric-jdk-module-export.md) — Robolectric fails on JDK 17+ without a module export. Read when Robolectric tests fail at setup or when changing the test JVM.
- [robolectric-max-sdk.md](robolectric-max-sdk.md) — Why `targetSdk` is 36. Read before raising `targetSdk` or upgrading Robolectric.
- [kover-compose-generated-branches.md](kover-compose-generated-branches.md) — Why `@Composable` functions are excluded from the branch gate. Read when writing UI code or changing Kover filters.
- [llama-context-padding.md](llama-context-padding.md) — The real context is larger than requested. Read when sizing contexts or estimating memory.
- [llama-decode-aborts-on-oversized-batch.md](llama-decode-aborts-on-oversized-batch.md) — Why prompts are decoded in chunks. Read before changing native decode code.
- [jni-text-as-utf8-bytes.md](jni-text-as-utf8-bytes.md) — Why text crosses JNI as bytes. Read before adding JNI functions that take or return text.
- [ggml-cpu-has-is-compile-time.md](ggml-cpu-has-is-compile-time.md) — Why Bruce detects CPU features itself, and how CPU variants are chosen. Read before any CPU performance work.
- [gpu-backends-on-test-phones.md](gpu-backends-on-test-phones.md) — Why Vulkan and OpenCL are unusable on both test phones, and the Vulkan 1.2 rule. Read before GPU backend work.
- [llama-uses-every-gpu-unless-told.md](llama-uses-every-gpu-unless-told.md) — Why model loads pass an explicit device list. Read before TASK-009 or changing loadModel.
- [ggml-backend-loading-is-silent.md](ggml-backend-loading-is-silent.md) — How to diagnose a backend that does not appear. Read when a backend is missing.
- [ndk-glslc-too-old.md](ndk-glslc-too-old.md) — Why the build needs a host glslc. Read when the Vulkan shader build fails or when upgrading the NDK.
- [vtracer-python-binding-crashes.md](vtracer-python-binding-crashes.md) — How the logo was traced, and why with the vtracer CLI. Read before re-tracing artwork.
