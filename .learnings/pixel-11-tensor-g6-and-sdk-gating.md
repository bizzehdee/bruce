The Pixel 11 test device is a Google Tensor G6 with 12 GB RAM — nobody had
checked before TASK-119. Confirmed via `adb shell getprop ro.soc.model`
(returns `"Tensor G6"` verbatim, the same system property `Build.SOC_MODEL`
reads at API 31+), `ro.soc.manufacturer` (`"Google"`), and `/proc/meminfo`
(~11.9 GB usable, i.e. 12 GB nominal); the owner confirmed the same
independently. Its GPU is unchanged from earlier benchmarks: Vulkan reports
`PowerVR C-Series CXTP-48-1536 MC1`, vendor ID `0x1010` — the existing
PowerVR/Vulkan exclusion (`.learnings/gpu-backends-on-test-phones.md`) still
applies; Tensor G6 pairs a non-Imagination NPU/TPU with that same GPU IP.

This means `Build.SOC_MODEL` is a usable, literal detection string
(`"Tensor G5"`/`"Tensor G6"`), not a cryptic codename — no need to hardcode an
unverified leak like "laguna" (an unconfirmed pre-launch Tensor G5 codename
found only in a speculative article, never used).

The Google Tensor SDK Beta signup
(`https://developers.google.com/edge/litert/next/tensor-sdk`) gates the AOT
model-compilation toolchain (Bazel/`litert-torch --aot_backend=GOOGLE`) and
optional remote access to a Pixel the developer doesn't own ("does not apply
to Pixel 10 devices") — not the runtime. Confirmed directly:
`https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-npu-runtime-google-tensor/maven-metadata.xml`
and the `litertlm-android` equivalent both return HTTP 200 with no
authentication, i.e. plain public Maven artifacts. Pre-built Tensor-targeted
`.litertlm` files already exist on Hugging Face
(`litert-community/gemma-4-E2B-it-litert-lm`'s `_Google_Tensor_G5.litertlm` /
`_G6.litertlm`), so neither the beta nor a custom AOT compile is needed to
build and run the NPU delegate against an existing model.

Read before: TASK-119's Tensor NPU half, or anything assuming a Google AI
Edge SDK's "beta signup" gates its Maven artifacts just because the docs page
for the SDK is itself behind a signup form — check what the sign-up form
text actually says it unlocks, and check `dl.google.com`'s maven-metadata.xml
directly, rather than assuming.
