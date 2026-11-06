# The `.litertlm` conversion toolchain: current (not deprecated) names, confirmed recipes

Retrieved 2026-11-06. TASK-121: resolving what remained open after the first
pass (`research/sources/litert-torch-conversion-2026-11-06/`) — the exact
Int4 recipe name, and the real `litert-torch` CLI subcommand spelling.

## `ai-edge-quantizer` is deprecated — use `litert_quantizer`

`https://github.com/google-ai-edge/ai-edge-quantizer`'s README opens with:
"AI Edge Quantizer is now LiteRT Quantizer. Development continues in
`google-ai-edge/LiteRT/litert_quantizer`. This repository is no longer
maintained, and `ai-edge-quantizer` will not receive further updates."
The CLI itself was renamed too: "the command-line tool from `aeq` to
`litert-quantizer`." Any conversion script Bruce builds (TASK-122) should
target `litert_quantizer` (package), `litert-quantizer` (CLI), not the old
names — building against the deprecated repo would mean starting already
out of date.

Confirmed the successor keeps the same recipe names (fetched
`google-ai-edge/LiteRT/litert_quantizer/litert_quantizer/recipe.py`, 397
lines, full source): "Apart from the names below, the API is unchanged" per
the migration README, and the rename list covers only the package/CLI names,
not individual recipes.

## Quantisation recipe names, confirmed in source (both the old and new repo)

Public recipe functions in `recipe.py` (verified present, same names in
both `ai-edge-quantizer` and its successor `litert_quantizer`):

- `dynamic_wi8_afp32` — Int8, dynamic range (per-channel weights, FP32 activations). **The documented default** (`litert-torch export_hf`'s default `--quantization_recipe`).
- `dynamic_wi4_afp32` — Int4, dynamic range. Confirmed real (was previously only a lower-confidence web-search hit).
- `weight_only_wi8_afp32` — Int8, weight-only.
- `weight_only_wi4_afp32` — Int4, weight-only. Confirmed real, the other previously-unconfirmed candidate.
- `static_wi8_ai8`, `static_wi8_ai16` — static (calibration-requiring) Int8, for NPU deployment per the README's own guidance ("we recommend dynamic quantization for CPU/GPU deployment and static quantization for NPU deployment").
- `dynamic_legacy_wi8_afp32` — a legacy variant, not used unless there's a specific reason to.
- `gemma4_mixed48`, `gemma4_mixed48_hr`, `gemma4_mixed48_b32`, `gemma4_mixed48_b64` — Gemma-4-specific mixed recipes (lambdas, not plain functions), worth checking if targeting Gemma 4 specifically.

**Int4 caveat from the README, relevant to Tensor NPU targets**: dynamic
Int4 is "Not supported in all hardware (e.g., some GPU and NPU)." Static
Int8 is the README's own recommendation for NPU, not dynamic Int4/Int8 —
worth weighing against the owner's "Int8 main target, Int4 in scope"
decision when TASK-122 actually targets the Tensor NPU variant.

The CLI usage for both old and new tool is the same shape:
`litert-quantizer --model_file=... --recipe=dynamic_wi8_afp32 --output_dir=...`
(old: `aeq --recipe=dynamic_wi8_afp32 ...`).

## `litert-torch`'s real CLI: confirmed `export_hf` is the subcommand, not a separate binary

`https://github.com/google-ai-edge/litert-torch`'s `setup.py` registers
exactly one console script:
```
"console_scripts": [ "litert-torch = litert_torch.cli:main" ],
```
There is no `export_hf` or `export-hf` console script — `export_hf` is a
Python module path (`litert_torch.generative.export_hf...`), i.e. a
subcommand parsed by `litert_torch.cli:main`, not a separate installed
command. This strongly favours the underscore spelling,
`litert-torch export_hf ...`, matching the module's own name — the hyphen
seen in some of Google's own NPU doc examples (`litert-torch export-hf`) is
most likely a documentation inconsistency. `cli.py` itself was not read, so
this is not a direct confirmation of the subcommand's exact argparse name;
confirm with `litert-torch --help` when TASK-122 actually runs the tool.

`litert-torch`'s own `setup.py` dependencies include `litert-quantizer-nightly`
and `litert-lm-builder>=0.19.0` — the actual Tensor AOT compilation step
(`--aot_backend=GOOGLE --aot_soc_model=Tensor_G6`, from the official docs,
`research/sources/litert-torch-conversion-2026-11-06/`) likely routes through
`litert-lm-builder`, not something in `litert-torch`'s or the quantizer's own
open READMEs — neither mentions "Tensor" anywhere. This is consistent with
TASK-117's finding that the Tensor AOT path needs the gated Google Tensor SDK
Beta: the compiler step for that target is not fully in the open-source
repos read so far.

## Still open after this pass

- Whether Tensor G5 (as opposed to G6) is a reachable AOT target by this
  toolchain at all — neither `litert-torch` nor `litert_quantizer`'s own
  pages mention Tensor by name; this would need `litert-lm-builder`'s own
  documentation or a real conversion attempt to confirm.
- The exact `litert-torch` subcommand spelling (`export_hf` vs `export-hf`),
  high-confidence but not 100%-confirmed without reading `cli.py` or running
  `--help`.
