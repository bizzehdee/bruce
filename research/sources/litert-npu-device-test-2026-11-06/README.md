# Tensor NPU delegate: real-device runs, Pixel 11 (Tensor G6)

Retrieved 2026-11-06. TASK-119: attempts to actually load a Tensor-AOT-compiled
`.litertlm` model on the NPU delegate on real hardware (the owner's Pixel 11,
confirmed Tensor G6, `.learnings/pixel-11-tensor-g6-and-sdk-gating.md`).
Three models tried, two independently compiled (a community build and
Google's own official release); the generic/non-AOT model and a version-pin
experiment are included below for completeness.

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

## Finding 3: the generic (non-AOT) model "succeeds" on the NPU label, but likely runs on CPU regardless

`gemma-4-E2B-it.litertlm` (the plain, non-AOT variant from the same official
`litert-community/gemma-4-E2B-it-litert-lm` repo) loaded successfully with
`LiteRtBackendPreference.NPU` requested — `LiteRtNative.loadModel` returned
`Loaded(backend = NPU, ...)` with no error, in 11.7 seconds, no `litert`-tagged
native log lines at all (a clean load produces none; the earlier failures'
`[compiled_model.cc:1735]` warnings are an error-path artifact, not a sign of
activity). Google's own docs say Tensor SDK "doesn't yet support on-device
(JIT) compilation," so a model with no AOT-compiled NPU subgraph has nothing
for the NPU delegate to actually claim — the most likely explanation is that
every op silently ran on the CPU kernel underneath, with the Kotlin-level
`backend` field only reflecting which choice `LiteRtBackendSelection.plan()`
tried, not which hardware actually executed. **Not confirmed either way**:
no throughput/token-rate measurement was taken (the test only loads, it does
not generate), and no log distinguished "ran on NPU" from "ran on CPU
anyway." Do not treat a successful load with `backend = NPU` as proof of real
NPU execution for a non-AOT model; only a `.litertlm` file compiled
specifically for the chip (`_Google_Tensor_G6.litertlm`-style) exercises the
real NPU delegate path at all — and both such files tried here failed before
reaching execution.

## Finding 4: reproduced on both an independent community build and Google's own official release

The original run above used a community (non-Google) build,
`litert-community/Gemma3-1B-IT-Tensor-NPU`'s `Gemma3-1B-IT_int8_aot_ekv4096_G6.litertlm`.
Its own README is unusually detailed and names the exact toolchain it was
compiled with: "Tensor SDK v2.1: `ai-edge-litert 2.3.0` +
`ai-edge-litert-sdk-google-tensor 2.3.0` (compiler BuildID `f4df3004…`)" — a
minor-version mismatch against the `litert-npu-runtime-google-tensor:2.3.2`
Bruce had pinned looked like a plausible cause, so the pin was temporarily
set to `2.3.0` (exact match) and retested: **no change**, identical
`Failed to allocate tensors` failure. The pin was reverted to `2.3.2`.

To rule out a bad file from one small-scale author, the same test was then
run against Google's own official release,
`litert-community/gemma-4-E2B-it-litert-lm`'s
`gemma-4-E2B-it_Google_Tensor_G6.litertlm` (3,453,960,202 bytes, confirmed
exact match after `adb push`). **Identical failure**: `Failed to allocate
tensors` on the NPU attempt. The CPU fallback on this file failed with a
different, but equally conclusive, message: `Signature not found: decode` —
confirming Finding 2 again from a different angle: the AOT file does not
even contain a "decode" execution signature outside the NPU path, it is
structurally NPU-only, not just permission-gated.

Two independently compiled models, by different authors, using different
int4/int8 recipes, both fail identically at native tensor allocation on this
exact Pixel 11 (Tensor G6, Android 17, build `CD1A.261005.003`). This is a
real signal: either (a) a firmware/dispatch-library ABI mismatch on this
specific retail device/Android build — the community README documents
exactly this class of problem happening before ("The earlier int4 G5/G6
bundles... failed warmup on a retail Pixel 11; it was built with the older
beta SDK, whose G6 firmware ABI didn't match retail silicon; SDK v2.1 fixed
that"), or (b) a more general incompatibility in the current pinned Maven
runtime versions that a different combination (not yet tried) might resolve.
Not diagnosed further in this pass — likely needs either Google's own
Tensor SDK Beta tooling (which the owner now has access to, primarily
intended for AOT compilation but may include firmware/compatibility
guidance) or a support escalation, rather than more version-guessing.
