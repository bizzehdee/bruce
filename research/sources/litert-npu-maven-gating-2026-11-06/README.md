# LiteRT-LM / Tensor NPU runtime: Maven artifacts are not gated by the Tensor SDK Beta

Retrieved 2026-11-06. TASK-119 research: whether the Google Tensor SDK Beta
signup (`https://developers.google.com/edge/litert/next/tensor-sdk`) actually
gates the runtime libraries needed to build and ship the Tensor NPU delegate,
or only something else.

## Direct Maven metadata checks

```
$ curl -s -o /dev/null -w "%{http_code}\n" https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-npu-runtime-google-tensor/maven-metadata.xml
200
$ curl -s https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-npu-runtime-google-tensor/maven-metadata.xml
<metadata>
  <groupId>com.google.ai.edge.litert</groupId>
  <artifactId>litert-npu-runtime-google-tensor</artifactId>
  <versioning>
    <latest>2.3.2</latest>
    <release>2.3.2</release>
    <versions><version>2.3.0</version><version>2.3.2</version></versions>
    <lastUpdated>20261026204500</lastUpdated>
  </versioning>
</metadata>

$ curl -s -o /dev/null -w "%{http_code}\n" https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml
200
```

Both resolve with no authentication: plain public Maven artifacts on Google's
own repository, the same one `settings.gradle.kts` already points at.

## Pages read, re-checked for what the beta signup actually gates

- `https://developers.google.com/edge/litert-lm/` — no access/beta language anywhere.
- `https://developers.google.com/edge/litert` — unguarded install snippets (`com.google.ai.edge.litert:litert:2.3.0`, `litert-gpu:2.3.0`, `litertlm-android:latest.release`).
- `https://developers.google.com/edge/litert/next/tensor-sdk` — heading "Sign up for access to Google Tensor SDK Beta." Description: "This SDK provides a comprehensive suite of tools that help developers access curated open-source models in Model Garden. This kit also enables **the compilation of models into TPU-compatible formats**." The only explicit gate in the prerequisites: "(Optional) A Google Cloud Project (GCP) that has been granted access to remote Pixel devices by the Tensor SDK team. ... This prerequisite does not apply to Pixel 10 devices." The rest of the page describes a Bazel/Linux dev environment for AOT compilation, not a runtime artifact.
- `https://developers.google.com/edge/litert/next/npu` — "Beta: Google Tensor SDK doesn't yet support on-device (JIT) compilation" (Tensor is AOT-only; other vendors support both). States plainly: "Starting from release 2.3.0, NPU libraries are available in Maven packages. For example, Google Tensor libraries are available in `com.google.ai.edge.litert:litert-npu-runtime-google-tensor:<VERSION>`." Delivery via Google Play's On-device AI / Play Feature Delivery is described as a recommended distribution path, not an access gate.
- `https://developers.google.com/edge/litert/next/litert_lm_npu` — the only Tensor-specific gate mentioned is a Hugging Face model-download login form ("you may need to login and acknowledge the form to obtain permission to download the file") — a Hugging Face gate, unrelated to the Tensor SDK Beta.
- `https://developers.google.com/edge/litert-lm/android` — Kotlin usage: `backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)`. No access language. Does not name which Maven artifact supplies the Tensor dispatch library (gap: not confirmed that `litert-npu-runtime-google-tensor` alone is sufficient for `litertlm-android`'s NPU backend to find it).
- `https://developers.google.com/edge/mediapipe/solutions` — a different, older product line (MediaPipe Tasks), CPU/GPU only, no NPU, not relevant to this question.

## Conclusion

The beta signup is for the **AOT model-compilation toolchain** and **optional
remote access to a Pixel device the developer does not own**. It does not
gate the published runtime Maven artifacts, and pre-built Tensor-targeted
`.litertlm` files already exist on Hugging Face
(`litert-community/gemma-4-E2B-it-litert-lm`), so adding the NPU delegate to
Bruce and testing it against an existing model needs neither the beta nor a
custom compile step. What remains unconfirmed: whether `litertlm-android`'s
`Backend.NPU(...)` finds the Tensor dispatch library correctly from just
`litert-npu-runtime-google-tensor` on the classpath, or needs something else
documented only in the Bazel/`adb push` native path
(`litert_lm_main --backend=npu`) — to be confirmed empirically on the Pixel
11 test device (now confirmed Tensor G6,
`.learnings/pixel-11-tensor-g6-and-sdk-gating.md`).
