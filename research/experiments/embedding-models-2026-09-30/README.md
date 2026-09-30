# Choosing the embedding model for local file search (TASK-096)

Date: 2026-09-30. llama.cpp 0.5.0 `llama-server --embedding` on the host (Fedora, 8 threads) and on
the Xperia 1 II (Snapdragon 865, 4 threads, build from `build/scratch/tc/android-x1ii`). Sources and
repository metadata: `research/sources/embedding-models-2026-09-30/`.

Test: `eval.py` — 30 note-like passages, 20 queries worded differently from their answer; 15 in
English, 5 in French, German or Spanish asking about English notes. Each model gets its own query
and document prefixes. Recall@1 is the share of queries whose right note ranked first.

| Model (Q8_0) | File | Dims | Recall@1 | English | Other languages | MRR | Host s / 30 | Xperia s / 30 | Xperia RSS |
|---|---|---|---|---|---|---|---|---|---|
| all-MiniLM-L6-v2 (Apache-2.0) | 25 MB | 384 | 0.90 | 1.00 | 0.60 | 0.916 | 0.05 | 0.21 | 74 MB |
| bge-small-en-v1.5 (MIT) | 37 MB | 384 | 0.75 | 0.87 | 0.40 | 0.821 | 0.09 | — | — |
| nomic-embed-text-v1.5 (Apache-2.0) | 146 MB | 768 | 0.90 | 1.00 | 0.60 | 0.913 | 0.53 | 0.84 | 305 MB |
| EmbeddingGemma-300M (Gemma terms) | 334 MB | 768 | **1.00** | 1.00 | **1.00** | **1.000** | 0.47 | 1.18–1.22 | 833–838 MB |
| Qwen3-Embedding-0.6B (Apache-2.0) | 639 MB | 1024 | 0.90 | 0.93 | 0.80 | 0.950 | 1.81 | — | — |

EmbeddingGemma's resident memory barely changed between 2048- and 512-token batches (838 → 833 MB):
it is the weights (a 262k-token vocabulary) plus the CPU's repacked copy
(`.learnings/phase0-cpu-benchmarks.md`), not compute buffers.

A first host run was invalid: every model reported 384 dimensions because `setsid` hid the first
server's PID, it kept the port, and every later run measured it. Rerun checking `/props`.

## Choice

EmbeddingGemma-300M Q8_0 from ggml-org/embeddinggemma-300M-GGUF (not gated; sha256
b5ce9d77…0490d63). It found every note, in every language tried, where the others missed
cross-language questions; about 25 passages a second on the Xperia is enough to index a folder of
notes in minutes. Its 0.8 GB is loaded only while indexing or searching, and unloaded after. It is
under Google's Gemma Terms of Use, which Bruce shows before the user downloads it; Bruce ships no
model. A small set, so the ranking between the English-only models is not meaningful.
