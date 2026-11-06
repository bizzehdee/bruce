An AOT-compiled-for-Tensor `.litertlm` file has no CPU execution path. Tried
on a real Pixel 11 (Tensor G6): `litert-community/Gemma3-1B-IT-Tensor-NPU`'s
`Gemma3-1B-IT_int8_aot_ekv4096_G6.litertlm` failed on the NPU delegate itself
(native "Failed to allocate tensors", root cause unconfirmed — possibly a
runtime/compile version mismatch), and the automatic CPU fallback on the
*same file* then failed too, with: `INVALID_ARGUMENT: Main backend
constraint mismatch. Model requires one of [npu] but Main backend is CPU`.

Unlike llama.cpp's GGUF, where one file runs on any ggml backend,
Tensor-AOT-compiled LiteRT-LM models are a **separate file** from the
CPU/GPU-capable one in the same Hugging Face repository (e.g.
`gemma-4-E2B-it.litertlm` vs `gemma-4-E2B-it_Google_Tensor_G6.litertlm`).
`LiteRtBackendSelection`'s "try NPU, fall back to CPU" shape — copied
directly from llama.cpp's own backend-selection pattern — does not fit this:
retrying the same file on CPU can never work for an AOT NPU file. A real
fallback would need to load a *different file*, which is a model-selection
(TASK-120) concern, not something the single-file load loop in
`LiteRtNative` can do alone. Not yet decided how (or whether) Bruce should
pick the matching file per device and fall back to the plain file on NPU
failure.

Also found and fixed in the same session: `Engine.close()` requires
`isInitialized()` and throws if called after a failed `initialize()` —
guard it, or the real failure reason gets masked by this secondary
exception. Add `Log.w` on each failed backend attempt; `loadModel` otherwise
gives no detail beyond `Loaded`/`Failed`.

Reproduced on two independently-compiled models — a community build
(`litert-community/Gemma3-1B-IT-Tensor-NPU`) and Google's own official
release (`litert-community/gemma-4-E2B-it-litert-lm`'s
`_Google_Tensor_G6.litertlm`) — both fail identically at native tensor
allocation on the same Pixel 11 (Tensor G6, Android 17, build
`CD1A.261005.003`). Pinning `litert-npu-runtime-google-tensor` to `2.3.0`
(the exact version the community build's README says it was compiled with)
made no difference; reverted to `2.3.2`. Two authors, two recipes, same
failure — likely a firmware/dispatch-library ABI mismatch on this specific
retail build (the community README documents exactly this class of problem
happening before and being fixed by an SDK version bump), not a bad file.
Not resolved; likely needs Google's own Tensor SDK Beta tooling or a
support escalation rather than further version-guessing.

Also: a non-AOT (`gemma-4-E2B-it.litertlm`) model "succeeds" when loaded
with `LiteRtBackendPreference.NPU` — no error, backend reported as NPU — but
this is not proof of real NPU execution. Google's own docs say the Tensor
SDK has no on-device JIT compilation yet, so a model with no AOT-compiled
NPU subgraph most likely runs entirely on CPU regardless of the requested
backend; Bruce's own `backend` field only reflects which choice was
attempted, not which hardware actually ran it. Only a model specifically
AOT-compiled for the chip exercises the real NPU path — and both such files
tried here failed before reaching execution.

Read before: any further Tensor NPU work (completing TASK-119, or TASK-120's
model-selection logic deciding which file to download/load per device). Full
detail: `research/sources/litert-npu-device-test-2026-11-06/`.
