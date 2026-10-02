# Gemma 4 E4B: regular and QAT mobile checkpoints (retrieved 2026-10-02)

Files: model cards (`*.README.md`), configs, file trees and API records for
google/gemma-4-E4B-it, google/gemma-4-E4B-it-qat-mobile-ct, google/gemma-4-E4B-it-qat-mobile-transformers
and unsloth/gemma-4-E4B-it-qat-mobile-GGUF, from `https://huggingface.co/<repo>/raw/main/README.md`,
`…/raw/main/config.json`, `https://huggingface.co/api/models/<repo>/tree/main` and
`https://huggingface.co/api/models/<repo>`; Google's E4B QAT repositories from
`https://huggingface.co/api/models?author=google&search=gemma-4-E4B-it-qat`.

Extract:
- QAT card: "optimized with Quantization-Aware Training (QAT), which allows preserving similar quality to
  bfloat16 while dramatically reducing the memory requirements". Four versions: unquantized QAT (Q4_0),
  GGUF (Q4_0), mobile-optimized (wNa8o8: "targeted 2-bit decoding layers, optimized KV caches, and static
  activations"; E2B and E4B only), compressed tensors (w4a16, for vLLM).
- google/gemma-4-E4B-it: one bf16 `model.safetensors`, 15.99 GB, no quantization config.
- …-qat-mobile-transformers: 3.53 GB; `quant_method: gemma`; embed_tokens, embed_tokens_per_layer,
  lm_head and the audio tower 2-bit; attention and MLP 4-bit; per-layer gate and projection and the
  vision tower 8-bit.
- …-qat-mobile-ct: 3.73 GB, compressed-tensors `pack-quantized`: embeddings and lm_head 2-bit per
  channel; other groups with static int8 input and output activations.
- Google's E4B QAT repositories: qat-q4_0-gguf, qat-q4_0-unquantized, qat-mobile-ct, qat-w4a16-ct,
  qat-q4_0-unquantized-assistant, qat-mobile-transformers.
- unsloth/gemma-4-E4B-it-qat-mobile-GGUF: one model file, `gemma-4-E4B-it-qat-UD-Q2_K_XL.gguf` (3.22 GB),
  plus mmproj and an MTP drafter.
