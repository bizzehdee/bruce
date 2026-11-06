# Tensor NPU delegate: first real-device run, Pixel 11 (Tensor G6)

Retrieved 2026-11-06. TASK-119: first attempt to actually load a Tensor-AOT-compiled
`.litertlm` model on the NPU delegate on real hardware (the owner's Pixel 11,
confirmed Tensor G6, `.learnings/pixel-11-tensor-g6-and-sdk-gating.md`).

## Setup

- Model: `litert-community/Gemma3-1B-IT-Tensor-NPU`'s
  `Gemma3-1B-IT_int8_aot_ekv4096_G6.litertlm` (2,110,160,272 bytes, confirmed
  exact match after `adb push`), a dedicated AOT-compiled-for-Tensor-G6 repo.
- Runtime: `com.google.ai.edge.litertlm:litertlm-android:0.19.1` +
  `com.google.ai.edge.litert:litert-npu-runtime-google-tensor:2.3.2` (pinned
  exact versions, both plain public Maven artifacts).
- Test: `app/src/androidTestFull/java/com/bizzeh/bruce/litert/LiteRtNpuDeviceTest.kt`,
  calling `LiteRtNative.loadModel(file, 4096, LiteRtBackendPreference.NPU, nativeLibraryDir)`
  directly, run via `adb shell am instrument`.

## Bug found and fixed first: `Engine.close()` masked the real error

`LiteRtNative.openOn`'s catch block called `engine.close()` unconditionally
on any failure. `Engine.close()` itself calls `checkInitialized()` and throws
`IllegalStateException("Engine is not initialized.")` if `initialize()` never
succeeded — so the very first run reported that exception instead of the
real cause. Fixed: guard with `if (engine.isInitialized()) engine.close()`.
Also added `Log.w` logging of the real exception on each failed attempt,
since `loadModel` only returns `Loaded`/`Failed` with no detail otherwise.

## Finding 1: NPU delegate reaches real native code, fails at tensor allocation

With the fix in place, the real NPU failure (`Log.w` tag `LiteRtNative`):

```
LiteRtLmJniException: Failed to create engine: UNKNOWN: ERROR: [.../llm_litert_npu_compiled_model_executor.cc:3458]
└ ERROR: [.../llm_litert_npu_compiled_model_executor.cc:3187]
└ ERROR: [.../llm_litert_npu_compiled_model_executor.cc:3118]
└ ERROR: [.../llm_litert_npu_compiled_model_executor.cc:324]
└ ERROR: [.../litert_compiled_model.h:2190]
└ ERROR: [.../litert_compiled_model.h:578]
└ Failed to allocate tensors
```

Preceded in `logcat` (tag `litert`) by repeated:
`[compiled_model.cc:1735] Failed to get buffer requirements for tensor 'mask_global'.`
(and `mask_local`, `pos_emb_cos`, `pos_emb_sin`, `pos_emb_local_cos`,
`pos_emb_local_sin`, `embeddings`), then `tflite: Node number 1 (DELEGATE)
failed to prepare.`

This reaches the real native NPU accelerator (`accelerator_registry.cc` logs
creating and destroying a `NpuAccelerator`, alongside `CpuAccelerator` and
`LiteRT GPU`) — so the Kotlin/Gradle/manifest wiring is not the problem. The
failure is inside native tensor-buffer allocation for this specific compiled
model against this specific runtime build. Root cause unconfirmed: could be
a version mismatch between how this Hugging Face file was AOT-compiled and
the pinned `litert-npu-runtime-google-tensor:2.3.2`/`litertlm-android:0.19.1`,
or a genuine pre-1.0 library defect. Not diagnosed further; needs either a
different pinned runtime version, a different source model, or escalation.

## Finding 2: AOT NPU models have no CPU fallback — architectural, not a bug

After the NPU attempt failed, `LiteRtBackendSelection.plan(NPU)`'s fallback
tried the **same file** on `Backend.CPU()`. That also failed, with a
completely different and unambiguous error:

```
LiteRtLmJniException: Failed to create engine: INVALID_ARGUMENT: Main backend constraint mismatch. Model requires one of [npu] but Main backend is CPU
```

This confirms: an AOT-compiled-for-Tensor `.litertlm` file is **NPU-only**.
It has no CPU execution path baked in — unlike llama.cpp's GGUF, where one
file runs on any ggml backend. The CPU/GPU-capable model and the Tensor-AOT
model are **different files** in the same Hugging Face repository (e.g.
`gemma-4-E2B-it.litertlm` vs `gemma-4-E2B-it_Google_Tensor_G6.litertlm`).

**Implication for Bruce's architecture**: `LiteRtBackendSelection`'s
"NPU with CPU fallback" model, copied from llama.cpp's own backend-selection
shape, does not fit LiteRT-LM's AOT NPU models. The right fallback, if a
Tensor-AOT file fails to initialize, is not "retry the same file on CPU" —
it would need to fall back to loading a *different file* (the plain/CPU
variant), which is a TASK-120 (pipeline/model-selection) concern, not
something `LiteRtNative`'s single-file load loop can do on its own. Whether
and how Bruce should auto-select the matching variant file per device (and
fall back to the plain file if the Tensor one fails) is an open design
question, not yet decided.
