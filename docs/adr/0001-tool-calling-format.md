# ADR 0001: How small models ask Bruce to use a skill

- Status: Accepted by the owner, 2026-09-28 (both points: the model's own format with a narrow build; full skill descriptions until a prompt budget)
- Task: TASK-033; closes the plan.md open question "Tool-calling format for small models"
- Evidence: `research/experiments/tool-calling-2026-09-28/README.md`

## Context

Bruce runs whatever GGUF model the user chooses, mostly 1–4B models on a phone. For skills, the
model must say which skill to run and with which arguments, and Bruce must parse that reliably.
Two questions: which format the model writes, and whether every skill is described up front or
only a short list first. Prompt length matters on a phone: the XZ Premium evaluates prompts at
about 23 tokens per second, so every 100 prompt tokens costs about 4 seconds per turn.

Bruce today links only `libllama`; llama.cpp's `common` library (templates with tool support,
per-family tool-call parsers, JSON-schema-to-grammar) is not built into the app.

## Options measured

Seven 0.8–4.7B models from five families, 36 cases (26 needing a skill), greedy, on the
development machine; a 12-case subset on both Sony phones.

| Option | Correct (all models) | Prompt tokens |
|---|---|---|
| A. Each model's own tool format (llama.cpp `common/chat`, lazy grammar and parser) | 235/252 (93%) | 312–607, by template |
| B. One Bruce format, described in the prompt | 192/252 (76%) | about 420 |
| C. Bruce format plus a lazy grammar | 211/252 (84%) | about 420 |
| D. As C, with a one-line list of skills instead of full schemas | 207/252 (82%) | about 230 |

- A is the only option that works across families. B–D fail almost completely on LFM2.5, which
  only writes its own format (10–11/36 against 32/36 native), and on granite without a grammar.
- C and D beat A only on Qwen3.5-2B (35 against 30), where native sometimes answers itself.
- D halves the prompt for a cost of 0–3 cases per model (granite gains 4).
- The common failure in every option is a missed call: the model answers itself, confidently
  and sometimes wrongly (invented times, wrong products).

## Decision

1. **Use each model's own tool format** through llama.cpp's chat code (option A), with its lazy
   grammar, for every model whose chat template supports tools.
2. **Fall back to Bruce's format with a lazy grammar** (option C) for models whose template has
   no tool support, and when the chat code cannot parse a template.
3. **Build only what that needs from `common`**: the chat, template (`jinja`), parser, JSON and
   JSON-schema-to-grammar sources, compiled into Bruce's native library by Bruce's own CMake
   target. Not the download, HTTP (cpp-httplib), argument-parsing or console code: Bruce's
   network mode is enforced in Kotlin, and native network code in the app would sit outside it.
   The exact file list is settled in TASK-036 with a test that fails if a llama.cpp upgrade makes
   the target need more.
4. **Describe skills in full while there are few** (six automatic skills), and switch to the
   one-line list plus loading a skill's full description on use (option D's approach) once the
   full descriptions pass a prompt budget that TASK-036 sets from phone timings. Measured with
   Bruce's format; with the native format the list form is untested and must be measured first.
5. **Treat a missed call as the main risk.** The system prompt tells the model to use a skill for
   anything it can look up or calculate. Detecting a reply that should have used a skill is out
   of scope for now.

## Consequences

- Linking part of `common` is an architectural change (owner approval needed): more native code
  to build and keep in step with llama.cpp upgrades, a vendored JSON library (nlohmann, MIT), and
  template execution (Jinja) on the phone for every turn.
- Tool support depends on the model's own template; Bruce needs the fallback path for the rest.
- Grammar-constrained generation costs little: generation speed changed by −2% to +1% across
  the seven models on the development machine (Bruce's format with and without the grammar);
  phone figures follow.

## Alternatives rejected

- **Bruce's own format only (B–D):** simpler and no `common`, but fails outright on families that
  insist on their own format, and Bruce cannot choose the user's model.
- **Linking all of `common`:** simplest build, but brings in native HTTP and download code that
  Bruce never needs and that its network mode could not govern.
