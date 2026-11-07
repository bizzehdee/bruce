Extends `.learnings/litert-lm-gpu-model-dependent-correctness.md` with a third model
(`litert-community/gemma-4-E4B-it-litert-lm`) and a GPU-*specific* export variant, same Pixel 11
PowerVR GPU, same `LiteRtGpuReferenceOutputDeviceTest.kt`, prompt "What is the capital of
France?", temperature 0:

| File | GPU (`Backend.GPU()`, OpenCL) | CPU |
|---|---|---|
| `gemma-4-E4B-it.litertlm` (plain) | "The capital of France is Paris." — correct | correct |
| `gemma-4-E4B-it-gpu.litertlm` (GPU-tuned export, `--enable_gpu_dynamic_prefill`/`--enable_gpu_dynamic_cache`) | "The information you' are is a famous and increasingly complex fact that it is a true, and that it is a." — fluent-sounding but wrong, does not answer the question | fails outright: `tf_lite_prefill_decode not found in the model` — this export has no CPU-executable section at all, the same structural pattern AOT NPU files show (a backend-specific export is a different file shape, not a tuning flag within one file) |

Counterintuitive conclusion: the **GPU-optimized** export is less reliable on this GPU than the
plain, non-GPU-tuned export of the same model, which works correctly on both GPU and CPU. The
GPU-tuned build's extra flags (dynamic prefill/cache shapes) apparently interact badly with this
PowerVR driver, producing a third distinct failure mode — not degenerate repeated-token garbage
(the 270M case) and not a hang/crash (llama.cpp's GGUF path) but fluent, grammatically normal,
semantically wrong text. A naive "use the officially GPU-tuned file for GPU" policy would pick
the worse of the two files here.

Read before: choosing which `.litertlm` variant (plain vs `-gpu`-suffixed) to use for a GPU
backend, or trusting a vendor-labelled "GPU-optimized" build without its own output check.
