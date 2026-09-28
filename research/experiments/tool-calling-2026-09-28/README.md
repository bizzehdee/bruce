# Tool-calling format for small models (TASK-033)

Run 2026-09-28. Question: how should a 1–4B model on a phone ask Bruce to use a skill, and should
Bruce describe every skill up front or a short list first?

## Method

- **Harness:** `run.py` (Python standard library only) against `llama-server` built from the
  pinned llama.cpp submodule (v0.5.0, `7fe450e19305`): on the development machine (x86, 8 threads)
  and, statically linked for Android, on the Xperia 1 II (armv8.2-a+dotprod+fp16, 4 threads) and
  the XZ Premium (armv8-a, 4 threads). CPU only, context 4096, greedy (temperature 0, seed 1), no
  prompt cache, thinking disabled (`enable_thinking: false`), replies capped at 160 tokens.
- **Skills:** the six automatic skills (date/time, calculator, battery, device info, storage,
  network), defined in `cases.json`.
- **Cases:** 36 prompts, 26 needing a skill and 10 not (`cases.json`); the 12 marked `phone` are
  the on-device subset. A calculator call counts as correct only if its expression evaluates
  (safe AST evaluator in `run.py`) to the expected value.
- **Variants:**
  - `native`: the model's own chat-template tool format through `/v1/chat/completions` with
    `tools` (llama.cpp's `common/chat`: template-specific prompt, lazy grammar and parser).
  - `prompt`: one Bruce-defined format, `<tool_call>{"name":…,"arguments":{…}}</tool_call>`,
    described in the system prompt with each skill's full JSON schema; no grammar.
  - `grammar`: as `prompt`, plus a lazy GBNF grammar that switches on at `<tool_call>`.
  - `index`: as `grammar`, but each skill is one line (`name(arguments): description`), the short
    list Bruce would load up front before loading a skill in full.
- **Outcomes:** `ok`, `missed` (answered without the needed skill), `unwanted` (called a skill
  when none was needed), `wrong_tool`, `bad_args`, `malformed`.
- **Scripts not committed** (in `build/scratch/tc`): builds, server start-up, model download.

## Models

All pinned by repository revision and checked by SHA-256; metadata archived in
`research/sources/tool-calling-models-2026-09-28/`.

| File | Repository @ revision | SHA-256 | Licence |
|---|---|---|---|
| Qwen3.5-0.8B-Q8_0.gguf | ggml-org/Qwen3.5-0.8B-GGUF @ 8fea6208 | 37ae482d…814f | apache-2.0 |
| LFM2.5-1.2B-Instruct-Q4_K_M.gguf | LiquidAI/LFM2.5-1.2B-Instruct-GGUF @ 8ed28802 | b1b3de11…b4f5 | other (lfm1.0) |
| Qwen3-1.7B-Q4_K_M.gguf | ggml-org/Qwen3-1.7B-GGUF @ daeb8e2d | d2387ca2…b7b5 | apache-2.0 |
| Qwen3.5-2B-Q4_K_M.gguf | unsloth/Qwen3.5-2B-GGUF @ f6d5376b | aaf42c8b…9223 | apache-2.0 |
| granite-4.2-3b-Q4_K_M.gguf | ibm-granite/granite-4.2-3b-GGUF @ c40945d7 | e0406663…e7d5 | apache-2.0 |
| Qwen3.5-4B-Q4_K_M.gguf | unsloth/Qwen3.5-4B-GGUF @ e87f1764 | 00fe7986…11a4 | apache-2.0 |
| gemma-4-E2B-it-Q4_K_M.gguf | unsloth/gemma-4-E2B-it-GGUF @ 0314792d | 740185b2…34b8 | apache-2.0 |

## Results: development machine, all 36 cases

`python3 analyse.py results/host.jsonl`. Correct out of 36:

| Model | native | prompt | grammar | index |
|---|---|---|---|---|
| Qwen3.5-0.8B Q8_0 | 33 | 31 | 31 | 29 |
| LFM2.5-1.2B | 32 | 10 | 10 | 11 |
| Qwen3-1.7B | 33 | 34 | 34 | 32 |
| Qwen3.5-2B | 30 | 35 | 35 | 33 |
| granite-4.2-3b | 35 | 10 | 29 | 33 |
| Qwen3.5-4B | 36 | 36 | 36 | 36 |
| gemma-4-E2B | 36 | 36 | 36 | 33 |
| **All** | **235/252** | 192/252 | 211/252 | 207/252 |

Prompt size (tokens, mean): native 312–607 depending on the template, Bruce's format with full
schemas about 410–440, the one-line index about 220–250.

What the failures were:

- **LFM2.5** ignores Bruce's format and writes its own (`[get_datetime()]`), so every Bruce
  variant misses; its native format works.
- **granite** without a grammar writes broken JSON inside the tag (19 malformed); with the grammar
  it often writes the call as bare JSON after an explanation, without the tag, so the lazy
  grammar never triggers (7 missed). Native works.
- **Qwen3.5-2B** in native format sometimes answers itself (5 missed), including invented times
  and wrong arithmetic ("1234 × 5678 = 7,017,652"; the answer is 7,006,652).
- **Missed calls are the dangerous failure:** the model answers confidently and wrongly.

## Results: phones (12-case subset)

Pending: `results/phone-xperia1ii.jsonl`, `results/phone-xzpremium.jsonl`. An earlier phone run
was discarded: a stale server kept serving the first model to every later one
(`.learnings/adb-shell-background-process-hangs.md`); the invalid files are not kept.
