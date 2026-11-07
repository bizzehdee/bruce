Full GPU-correctness sweep (2026-11-07) of `litert-community`'s plain (non-AOT, non-`-gpu`-
suffixed) `.litertlm` chat models, bounded to phone-sized (~≤4B param) models per owner decision
— excludes vision classifiers, embeddings, TTS/ASR/OCR, and 8B+ models, none of which Bruce
would ever load as a chat model. Same Pixel 11 PowerVR GPU, same prompt ("What is the capital of
France?"), via `LiteRtGpuReferenceOutputDeviceTest.kt`'s `cpuOutput`/`gpuOutput` methods (split
so one backend's crash doesn't lose the other's already-logged result), each GPU run guarded by
a polling watchdog (force-stop after 60-80s of flat RSS) per `.learnings/vulkan-powervr-gemma4-hangs.md`'s
lesson.

| Model | Size | CPU | GPU |
|---|---|---|---|
| gemma-3-270m-it | 0.3 GB | correct | **garbage** (prior result, `.learnings/litert-lm-gpu-model-dependent-correctness.md`) |
| gemma-4-E2B-it | 2.6 GB | correct | correct (prior result) |
| gemma-4-E4B-it | 3.7 GB | correct | correct (prior result) |
| Qwen3.5-0.8B_int8 | 1.0 GB | correct | **failed, fell back to CPU** (~230s before fallback; final answer correct via the CPU fallback) |
| Qwen3.5-2B_int8 | 2.1 GB | correct | **crashed** (~110s in, no diagnosable error — no tombstone, no OOM log found) |
| MiniCPM5-1B_dynamic_wi8_afp32 | 1.1 GB | correct | correct |
| MiniCPM5-2B_int8 | 2.5 GB | correct | correct |
| Qwen2.5-1.5B-Instruct (q8) | 1.6 GB | correct | correct |
| Qwen3-0.6B | 0.6 GB | correct | correct |
| DeepSeek-R1-Distill-Qwen-1.5B (q8) | 1.8 GB | correct (coherent reasoning, truncated at 64 tokens) | correct (coherent reasoning, same trajectory, truncated at 64 tokens) |
| granite-4.0-350m (q8) | 0.5 GB | correct | correct |
| Phi-4-mini-instruct (q8, 3.8B) | 3.9 GB | correct | **crashed** (~60s in, no diagnosable error) |

Not tested: `litert-community/Gemma3-1B-IT` is a gated repo (requires Hugging Face
authentication) — skipped, not a technical finding. `SmolLM-135M-Instruct`, `TinyLlama-1.1B-Chat-v1.0`,
`Qwen2.5-0.5B-Instruct`, `Gemma2-2B-IT`, `Llama-3.2-1B` have no plain `.litertlm` file published
in their repos (only GGUF, `.task`, or GPU/chip-specific `.litertlm` variants) — skipped, nothing
to test.

**Pattern: size, not model family, predicts GPU failure here.** Every model up to ~1.8 GB
(file size) that has a plain export succeeded on GPU. Both failures beyond that
(Qwen3.5-2B at 2.1 GB, Phi-4-mini at 3.9 GB) crashed outright with no diagnosable cause, and
Qwen3.5-0.8B's own GPU attempt (only 1.0 GB) failed and fell back to CPU despite being smaller
than several models that succeeded — so size alone is not a clean predictor either, just a
rough correlation. gemma-3-270m-it (0.3 GB, smallest tested) is the one exception that breaks
any simple "bigger fails, smaller succeeds" story — it is both small and wrong, with a
*different* failure character (degenerate token garbage, not a crash).

**First observed real GPU→CPU fallback for a plain model.** Qwen3.5-0.8B is the first case
where a plain model's GPU attempt genuinely failed to load (not just produced wrong output) and
`LiteRtNative.loadModel`'s built-in fallback correctly moved to CPU — the same mechanism already
proven for NPU (`.learnings/litert-lm-npu-no-cpu-fallback.md`), now also confirmed for GPU.

Net conclusion: there is no clean allowlist rule ("models under N GB are GPU-safe") from this
data alone — behaviour ranges from correct, through a proven fallback, to a silent crash, with
one small model giving wrong output outright. Any future "enable GPU for known-good models"
feature needs either a per-file verified list (not a size/family heuristic) or a live
correctness probe, not a simple threshold.

Read before: designing any policy that auto-selects GPU for a LiteRT-LM model on this device
class, or trusting GPU for an untested model/size.
