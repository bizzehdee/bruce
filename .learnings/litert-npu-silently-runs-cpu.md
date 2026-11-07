Requesting `Backend.NPU(nativeLibraryDir)` for a plain (non-AOT) `.litertlm` model
"succeeds" — `LiteRtLoadResult.backend == NPU`, no exception — but confirmed via full
logcat (not just inferred from Google's docs, as `.learnings/litert-lm-npu-no-cpu-fallback.md`
left it) that it actually runs on CPU the whole time:

```
NPU backend requested, but TF_LITE_AUX is not packaged in the model. Using the generic
LiteRT compiler-plugin path.
...
Vendor library exports legacy symbol 'LiteRtCreateCompilerPlugin' instead of
'LiteRtCompilerPluginQueryInterface'. Please recompile vendor library against the
versioned LiteRT Compiler Plugin C ABI.
Failed to apply compiler plugins: No compiler plugin found
...
Created TensorFlow Lite XNNPACK delegate for CPU.
Replacing 688 out of 842 node(s) with delegate (TfLiteXNNPackDelegate)
```

Two distinct facts, not one:

1. **No on-device JIT (expected, per Google's docs):** a plain model has no AOT NPU subgraph,
   so the runtime falls back to a "generic compiler-plugin path" rather than refusing outright.
2. **That fallback path is itself broken on the current artifact pairing (new finding):** it
   tries to load the bundled `libLiteRtCompilerPlugin_google_tensor.so` (from
   `litert-npu-runtime-google-tensor:2.3.2`) as a compiler plugin, but that `.so` exports the
   legacy `LiteRtCreateCompilerPlugin` symbol, not the `LiteRtCompilerPluginQueryInterface` the
   installed `litertlm-android:0.19.1` runtime looks for. This is an artifact-version mismatch
   between two separately-versioned Maven dependencies, independent of (1) — even if Google
   shipped on-device JIT tomorrow, this specific pairing would still fail to find a usable
   compiler plugin. Falls through cleanly to XNNPACK CPU, so no crash, just silent wrong
   reporting.

Consequence for Bruce: `LiteRtBackendChoice.NPU` in `LiteRtLoadResult` is not trustworthy as
"this turn actually ran on the NPU" for a plain model — only an AOT-compiled file proves real
NPU dispatch is attempted (and on this Pixel 11, that then hits the separate
`Failed to allocate tensors` wall from `.learnings/litert-lm-npu-no-cpu-fallback.md`). Anywhere
Bruce surfaces "backend: NPU" to the user or a benchmark, that claim is unverified for a plain
model and should be treated as "requested NPU, ran on CPU" until proven otherwise.

Read before: trusting `LiteRtLoadResult.backend` for telemetry/UI, or any further Tensor NPU
diagnosis work.
