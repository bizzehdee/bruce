Completes TASK-122: the two previously-untested `tools/litertlm-convert/convert.py` variants
(`--variant gpu`, `--variant tensor-g5`) both run end to end, verified 2026-11-07 against
`google/gemma-3-270m-it` on the same machine/pipeline that already proved standard/CPU and
Tensor G6 (see TASK-119's entries in `TASK.md`).

**GPU variant works, and fixes a correctness bug the plain export has.** The *plain* export of
`gemma-3-270m-it` gives garbage output on the Pixel 11's GPU
(`.learnings/litert-lm-gpu-model-dependent-correctness.md`). The same model compiled with
`--variant gpu` (`--enable_gpu_dynamic_prefill=True --enable_gpu_dynamic_cache=True`) loads and
answers correctly on the same GPU. Not yet generalised to other models — this is one data point,
not proof the GPU export flags fix wrong-output GPU behaviour in general — but it rules out "the
GPU export path itself is broken," the opposite of what `.learnings/litert-lm-gpu-tuned-export-worse.md`
found for Gemma 4 E4B's `-gpu` file (which was *worse* than plain). The two results together mean
GPU-tuned exports are not uniformly better or worse than plain ones — per-model testing is still
required either way.

**Tensor G5 is reachable**, resolving TASK-121's "still not confirmed reachable" open question.
`litert-torch export_hf --aot_backend=GOOGLE --aot_soc_model=Tensor_G5` (plus the same
`--split_cache --externalize_embedder`/`weight_only_wi8_afp32`/`google_tensor_enable_large_model_support`
flags TASK-119 found required for G6) compiles cleanly, no failure line, in about the same time as
G6. Could not be validated by actual execution — this dev's only Tensor test device is a G6, with
no G5 hardware — but loading the G5 file on the G6 Pixel 11 fails identically to the G6
self-build (`Failed to allocate tensors` on NPU, `Input positions not found` on CPU fallback),
which is the expected "wrong chip" + already-known-fallback-bug shape, not a new, different kind
of failure — evidence the file itself is well-formed, just untestable on this hardware.

Read before: TASK-122 follow-up work, or claiming the GPU export path is either safe or unsafe
in general from a single model's result.
