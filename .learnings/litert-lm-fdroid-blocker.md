LiteRT-LM's Android path ships prebuilt binaries, not source. The getting
started doc says outright: "You do **not** need to build this project from
source to use it in your apps"; the GPU delegate is checked-in prebuilt
`.so` files fetched by `git lfs pull`, and the Maven AAR
(`com.google.ai.edge.litertlm:litertlm-android`) is a prebuilt artifact too.
This conflicts with the existing constraint "F-Droid requires the app to
build from source with no proprietary dependencies" (`plan.md`), independent
of LiteRT-LM's licence (Apache-2.0, otherwise GPL-3.0-or-later-compatible).

The Tensor G5/G6 TPU path is worse: it needs a gated "Google Tensor SDK Beta"
signup, an unverified external `@litert` Bazel dependency
(`litert/vendors/google_tensor/dispatch:dispatch_api_so`), a login-gated model
download, and Google documents delivery of the NPU runtime library via
"Google Play Feature Delivery" rather than plain bundling — a distribution
model that doesn't obviously carry over to F-Droid or a signed GitHub
Releases APK. The NPU overview page also states the SDK "doesn't yet support
on-device (JIT) compilation," meaning Tensor-targeted models must be
precompiled by Google ahead of time (matches the separate `_Google_Tensor_G5`
/ `_Google_Tensor_G6` file variants seen on Hugging Face).

No page read (Android getting-started, GitHub README/LICENSE/build doc,
Tensor SDK page, two NPU pages) mentions AICore or Gemini Nano. That's an
absence of evidence the LiteRT pipeline depends on AICore, not a confirmed
absence — the closed Tensor runtime libraries' internals weren't inspected.

Read before: any work on Bruce's planned LiteRT-LM pipeline (TASK-117
onward), or before assuming a Google AI Edge / LiteRT library is plain
source-buildable just because its repo licence is permissive. Full detail:
`research/sources/litert-lm-android-tensor-sdk-2026-11-06/README.md`.
