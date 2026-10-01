# Binning small models per memory tier (TASK-103 to TASK-108)

Question: which popular small chat models should Bruce recommend, per memory tier (4 GB, 8 GB,
12 GB)? This folder holds the model list (TASK-103); the cases, host screen, phone runs and bins
follow in TASK-104, TASK-108, TASK-105 and TASK-106.

## Model selection (TASK-103, 2026-09-30)

Scripts in `selection/` (Python standard library; run from `build/scratch/bins`, on disk).
Hub answers archived in `research/sources/hf-hub-api-2026-09-30-model-bins/`.

1. `fetch.py`: the 3,000 most downloaded GGUF repositories with `num_parameters=min:0,max:3B`, no
   task filter, with the `downloads`, `gated`, `cardData`, `gguf`, `siblings`, `pipeline_tag`
   expansions (three pages of 1,000).
2. `select.py`: kept if not gated, the architecture is one Bruce's llama.cpp loads as a chat model
   (`LlamaArchitectures.supportsChat`; `qwen3tts`, `pockettts` and `llama-embed` were added to its
   exclusions after this run showed them), a parameter count is present and the GGUF has a chat template.
   1,862 of 3,000 kept (502 had no chat template, 395 an architecture llama.cpp lacks, 227 were not
   chat models, 9 gated, 5 had no parameter count).
3. `rank.py`: dropped repositories whose task tag or name marks them as not a chat model
   (embedding, reranking, speech, OCR, translation, base models; 184) and those whose Hub
   parameter count comes from a part of a larger model (the base model's name gives more than
   3.5B; 66). Grouped by base model (`cardData.base_model`, `-GGUF` removed), downloads summed over
   its copies. **A base model needs copies from at least two publishers** (958 dropped): a single
   repository's download count is easy to inflate and says little about use.
4. `pick.py`: each base model's main copy is its most downloaded repository whose name is the
   model's own (not a fine-tune), pinned to its current revision; files and SHA-256 from the tree.
5. `files.py`: the file Bruce's `ModelFit.rank` would choose for each phone, from exact file sizes:
   fit, then speed band, then parameters, quantised before float, then the larger file. Usable
   memory assumed at 1.3 GB (XZ Premium), 3.9 GB (Xperia 1 II) and 6.0 GB (Pixel 11), from
   `MemAvailable` on the phones that day; Bruce measures it at run time.

Buckets as in the model browser: under 1B is below 1,000,000,000 parameters (so
gemma-3-1b-it, at 999,885,952, is in it); 1B to 3B is below 3,000,000,000.

`models.json` holds, per model: bucket, role (`candidate` for the first 10 per bucket, `reserve`
for the next 3), base model, downloads over all copies, publisher count, repository, revision,
architecture, parameters, licence, task tag, and per phone the file, its size, SHA-256 and whether
it fits.

### Candidates

| Bucket | Base model | Repository | Params (B) | Licence | XZ Premium file | Xperia 1 II and Pixel 11 file |
|---|---|---|---|---|---|---|
| under 1B | Qwen/Qwen3-0.6B | Qwen/Qwen3-0.6B-GGUF | 0.60 | apache-2.0 | Q8_0 | Q8_0 |
| under 1B | Qwen/Qwen3.5-0.8B | unsloth/Qwen3.5-0.8B-GGUF | 0.75 | apache-2.0 | UD-IQ2_M | UD-Q8_K_XL |
| under 1B | LiquidAI/LFM2.5-230M | LiquidAI/LFM2.5-230M-GGUF | 0.23 | other (lfm) | Q8_0 | Q8_0 |
| under 1B | google/gemma-3-1b-it | ggml-org/gemma-3-1b-it-GGUF | 1.00 | gemma | Q4_K_M | Q8_0 |
| under 1B | Qwen/Qwen2.5-0.5B-Instruct | Qwen/Qwen2.5-0.5B-Instruct-GGUF | 0.63 | apache-2.0 | q8_0 | q8_0 |
| under 1B | HuggingFaceTB/SmolLM2-135M-Instruct | unsloth/SmolLM2-135M-Instruct-GGUF | 0.13 | apache-2.0 | Q8_0 | Q8_0 |
| under 1B | LiquidAI/LFM2.5-350M | LiquidAI/LFM2.5-350M-GGUF | 0.35 | other (lfm) | Q8_0 | Q8_0 |
| under 1B | google/gemma-3-270m-it | unsloth/gemma-3-270m-it-GGUF | 0.27 | gemma | Q8_0 | UD-Q8_K_XL |
| under 1B | HuggingFaceTB/SmolLM2-360M-Instruct | HuggingFaceTB/SmolLM2-360M-Instruct-GGUF | 0.36 | apache-2.0 | q8_0 | q8_0 |
| under 1B | Qwen/Qwen2.5-Coder-0.5B-Instruct | Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF | 0.63 | apache-2.0 | q8_0 | q8_0 |
| 1B to 3B | CMSManhattan/JiRackUltra_1b | CMSManhattan/JiRackUltra_1b | 1.78 | mit | Q3_K_M | Q6_K |
| 1B to 3B | LiquidAI/LFM2.5-2.6B | LiquidAI/LFM2.5-2.6B-GGUF | 2.70 | other (lfm) | does not fit | QAD-Q4_0 |
| 1B to 3B | Qwen/Qwen3-1.7B | MaziyarPanahi/Qwen3-1.7B-GGUF | 2.03 | not stated (apache-2.0 upstream) | Q2_K | Q5_K_M |
| 1B to 3B | Qwen/Qwen3.5-2B | unsloth/Qwen3.5-2B-GGUF | 1.88 | apache-2.0 | Q3_K_S | Q6_K |
| 1B to 3B | Qwen/Qwen2.5-1.5B-Instruct | Qwen/Qwen2.5-1.5B-Instruct-GGUF | 1.78 | apache-2.0 | q3_k_m | q6_k |
| 1B to 3B | LiquidAI/LFM2.5-1.2B-Instruct | LiquidAI/LFM2.5-1.2B-Instruct-GGUF | 1.17 | other (lfm) | Q6_K | Q8_0 |
| 1B to 3B | meta-llama/Llama-3.2-1B-Instruct | MaziyarPanahi/Llama-3.2-1B-Instruct-GGUF | 1.24 | not stated (llama3.2 upstream) | Q6_K | Q8_0 |
| 1B to 3B | openbmb/MiniCPM5-2B | openbmb/MiniCPM5-2B-GGUF | 2.52 | apache-2.0 | does not fit | Q4_K_M |
| 1B to 3B | Qwen/Qwen2.5-Coder-1.5B-Instruct | Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF | 1.54 | apache-2.0 | q3_k_m | q6_k |
| 1B to 3B | google/gemma-2-2b-it | MaziyarPanahi/gemma-2-2b-it-GGUF | 2.61 | not stated (gemma upstream) | IQ2_XS | IQ4_XS |

**Owner, 2026-09-30:** JiRackUltra_1b dropped (single author, downloads nearly all on its own
repository) and Qwen/Qwen3-VL-2B-Instruct (reserve) takes its place; 2- and 3-bit files on the
low-memory phone are acceptable. All 30 files for the candidates downloaded to
`build/scratch/bins/models` and matched their published SHA-256.

Reserves: under 1B, openbmb/MiniCPM-V-4.6, Qwen/Qwen2-0.5B-Instruct,
DavidAU/Qwen3-Zero-Coder-Reasoning-V2-0.8B; 1B to 3B, Qwen/Qwen3-VL-2B-Instruct,
01-ai/Yi-Coder-1.5B-Chat, XHToken/Spark-X2.5-1.7B.

31 distinct files, 29.7 GB, for the candidates on the phones they fit. The Pixel 11 would pick
the same file as the Xperia 1 II for every candidate.

### What the selection showed

- **CMSManhattan/JiRackUltra_1b** ranks first in its bucket on 2.5 million downloads, nearly all
  on its own repository (one other publisher). It is a single author's ternary (BitNet-style)
  rework of a Qwen2.5 model; the repository also holds a pickled `model.pt` (not downloaded; only
  GGUF files are). Kept by the rule, flagged for the owner.
- **The XZ Premium gets 2- and 3-bit files.** Without DOTPROD, Bruce's speed estimate calls a file
  fast (15 tokens a second) only up to about 0.39 GB, and the ranking puts the speed band before
  precision. So a 0.8B model gets IQ2_M (0.37 GB) rather than Q8_0, and 1.5–2B models get Q2_K,
  Q3_K or IQ2_XS. The host screen tests these files as well as the Xperia's, which shows what
  the choice costs in answers.
- **"fp16" in file names was not recognised** (Qwen's own repositories use it), so those float
  files counted as quantised and outranked Q8_0 on the 8 GB phone. Fixed in `ModelFit` with a
  regression test before these picks were made.

## Cases and harnesses (TASK-104, 2026-09-30)

- `cases.json`: 41 cases in eight groups (instructions, knowledge, reasoning, text, grounding,
  conversation, skill, no-skill), each run with skills on and off, plus the long-chat "needle"
  for the context sweep. The Wi-Fi case was dropped: a fresh install is offline, so the network
  skill is locked.
- **Skills on** means a fresh install (offline, nothing granted) with the battery, device and
  storage skills turned on, since cases ask for them. That offers exactly the six automatic skills
  less network: `get_datetime`, `calculate`, `get_battery_status`, `get_device_info`,
  `get_storage_status`; every other skill is declined by default. 583 fixed prompt tokens with
  LFM2.5-230M (235 with skills off).
- `bruce-prompt.json`: the system prompt and skill definitions the app sends, captured on the
  Xperia 1 II; the host screen sends the same.
- **Phone:** `ModelBinDeviceTest` (manual-only device test) drives `BruceRuntime` with the model
  in `files/bins`, greedy, at context 4096 (`mode=cases`), or per context length (`mode=sweep`:
  load time, resident memory, first-token time cold and with the prompt reused, and the long chat
  filled to 70% of the window, below the runtime's prompt limit so nothing is dropped). It waits
  while the battery is above 40 °C. App data is kept.
- **Host:** `host.py` against `llama-server` from the pinned llama.cpp (7fe450e19), the model's
  own tool format, thinking off, greedy; fixed skill results in Bruce's envelope; the calculator
  evaluates for real; Bruce's repeated-call handling copied.
- **Grading:** `grade.py summary|report` (tests: `python3 -m unittest test_grade`). A reply fails
  if it declines a task it could do ("I don't have access to a tool…"), calls a skill where none
  is needed, misses or misuses a needed one, or fails the case's checks. With skills off, a case
  that needs a skill must say it cannot check and must not invent a value.

### Harness check: LFM2.5-230M Q8_0 (`results/harness-check/`)

| Platform | General, skills on | General, skills off | Skill cases | Honest, skills off | Unwanted calls | Missed calls |
|---|---|---|---|---|---|---|
| Xperia 1 II | 0.17 | 0.70 | 0.64 | 1.00 | 4 | 2 |
| Host | 0.17 | 0.70 | 0.64 | 1.00 | 4 | 2 |

- **Host and phone agree** on every rate, although only 46 of 82 replies are word-for-word the
  same (different skill values, and small numeric differences between x86 and ARM kernels).
- **Offering skills can wreck a small model.** With the five skills offered, LFM2.5-230M declined
  most ordinary questions ("I don't have access to a tool that can…", even for the capital of
  Australia); without them it answered 70% correctly. This is the effect the bins must capture.
- Context sweep on the Xperia 1 II: loads at every length up to 16384 in about 0.4 s; the long
  chat's answer was wrong at every length (this model does not recall the fact).

## Host screen (TASK-108, 2026-09-30 to 2026-10-01)

All 30 candidate files on the development machine, 41 cases with skills on and off each.
Results: `host/results.jsonl.gz`; scores: `host/summary.md`; every reply, per model:
`host/replies/<model>.md`. Grade again with `python3 grade.py summary host/results.jsonl.gz`.

Run notes:
- Templates that cannot express tool calls (`chat_template_caps.supports_tool_calls` false, the
  app's own test) get Bruce's `<tool_call>` format with its lazy grammar, as the app sends it;
  the first pass did not, so those files' skills-on cases were run again (`host-rerun.sh`).
  Fallback format: gemma-3 (both), gemma-2-2b, SmolLM2 (both), LFM2.5-350M and LFM2.5-1.2B.
- Both Qwen3.5-0.8B files lost their server mid-run at 21:43 on the first pass (no crash or
  out-of-memory record); run again in full without a problem. Cause not found.
- 2- and 3-bit files fall apart: Qwen3.5-0.8B IQ2_M and Qwen3-1.7B Q2_K score 0.10–0.17 and often
  write until the 4096-token reply limit.
- LFM2.5-1.2B through llama.cpp's own tool path instead (forced, not what the app does): skill
  cases 0.73 instead of 0.00, but 15 unwanted calls and 0.27 on general cases with skills on,
  against 0.90 with the fallback.

Each model's best file (general = cases needing no skill; invented = of the 7 skills-off cases
that need a skill):

| Model | Best file | General, skills on | General, skills off | Skill cases | Invented | Unwanted calls |
|---|---|---|---|---|---|---|
| LFM2.5-2.6B | QAD-Q4_0 | 0.90 | 0.97 | 1.00 | 0 | 3 |
| Qwen3-1.7B | Q5_K_M | 0.90 | 0.97 | 0.91 | 5 | 2 |
| Qwen3-VL-2B-Instruct | Q4_K_M | 0.63 | 0.90 | 0.91 | 6 | 9 |
| MiniCPM5-2B | Q4_K_M | 0.83 | 0.97 | 0.82 | 1 | 2 |
| Qwen3.5-2B | Q3_K_S | 0.77 | 1.00 | 0.64 | 1 | 1 |
| Qwen2.5-1.5B-Instruct | q6_k | 0.93 | 0.90 | 0.55 | 3 | 0 |
| Qwen3.5-0.8B | UD-Q8_K_XL | 0.60 | 0.83 | 0.55 | 0 | 7 |
| LFM2.5-230M | Q8_0 | 0.17 | 0.70 | 0.64 | 0 | 4 |
| gemma-2-2b-it | IQ4_XS | 0.87 | 0.83 | 0.45 | 6 | 0 |
| Qwen3-0.6B | Q8_0 | 0.73 | 0.80 | 0.45 | 3 | 0 |
| Qwen2.5-0.5B-Instruct | q8_0 | 0.40 | 0.67 | 0.45 | 4 | 8 |
| LFM2.5-1.2B-Instruct | Q8_0 | 0.90 | 0.93 | 0.00 | 3 | 0 |
| gemma-3-1b-it | Q8_0 | 0.30 | 0.73 | 0.18 | 7 | 18 |
| Llama-3.2-1B-Instruct | Q8_0 | 0.07 | 0.87 | 0.00 | 6 | 1 |
| SmolLM2-360M-Instruct | q8_0 | 0.60 | 0.67 | 0.18 | 3 | 0 |
| Qwen2.5-Coder-1.5B-Instruct | q3_k_m | 0.67 | 0.83 | 0.00 | 3 | 0 |
| LFM2.5-350M | Q8_0 | 0.57 | 0.63 | 0.00 | 3 | 0 |
| Qwen2.5-Coder-0.5B-Instruct | q8_0 | 0.50 | 0.60 | 0.00 | 6 | 0 |
| SmolLM2-135M-Instruct | Q8_0 | 0.47 | 0.47 | 0.09 | 4 | 0 |
| gemma-3-270m-it | UD-Q8_K_XL | 0.40 | 0.33 | 0.00 | 1 | 3 |

4-bit files for the 4 GB phone (owner, 2026-10-01), on the host:

General cases with skills on / off, then skill cases:

| Model | 4 GB phone's pick | Pick: on / off / skills | Q4_K_M: on / off / skills |
|---|---|---|---|
| Qwen3.5-0.8B | UD-IQ2_M | 0.10 / 0.17 / 0.00 | 0.50 / 0.87 / 0.55 |
| Qwen3-1.7B | Q2_K | 0.17 / 0.17 / 0.00 | 0.80 / 0.90 / 1.00 |
| Qwen3.5-2B | Q3_K_S | 0.77 / 1.00 / 0.64 | 0.37 / 0.83 / 0.91 |
| Qwen2.5-1.5B-Instruct | q3_k_m | 0.90 / 0.90 / 0.00 | 0.93 / 0.90 / 0.27 |
| Qwen2.5-Coder-1.5B-Instruct | q3_k_m | 0.67 / 0.83 / 0.00 | 0.73 / 0.83 / 0.00 |

### Gemma 4 E2B (owner, 2026-10-01)

unsloth/gemma-4-E2B-it-GGUF @ 0314792d (apache-2.0), Q3_K_M (2.54 GB) and Q4_K_M (3.11 GB), on
the host. 4.6B parameters with its per-layer embeddings, so outside both buckets; too large for
the 4 GB phone.

| File | General, skills on | General, skills off | Skill cases | Invented | Unwanted calls |
|---|---|---|---|---|---|
| Q4_K_M | 0.97 | 1.00 | 0.82 | 0 | 0 |
| Q3_K_M | 0.93 | 0.97 | 0.91 | 0 | 2 |

The best general scores of any model tested, and no invented answers. With skills off it does
not say it cannot check: it writes made-up tool code (`<tool_code> print(phone.get_battery_level())
</tool_code>`), because Bruce's system prompt tells it to use a tool even when none is offered.

### Skills off, after the prompt fix (c3ba23b, 2026-10-01)

Every host file's skills-off cases were run again with the new skills-off system prompt
(`host-rerun-off.sh`; earlier results kept in `host/results-before-off-fix.jsonl.gz`). Over all 37
files, of the 259 skills-off cases that need a skill: honest answers rose from 65 to 103, invented
ones fell from 114 to 90. Gemma 4 E2B (0.00 to 0.86–1.00), LFM2.5-2.6B (0.00 to 1.00) and
Qwen3.5-2B Q6_K (0.43 to 1.00) gained most; Llama 3.2 1B, Qwen2.5-Coder and gemma-3 still
invent. General scores with skills off moved by at most 0.17 either way.
