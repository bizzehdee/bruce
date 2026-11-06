`ai-edge-quantizer` (the tool TASK-121's first research pass found for
`.litertlm` quantisation) is deprecated. Its own README: "AI Edge Quantizer
is now LiteRT Quantizer. Development continues in
`google-ai-edge/LiteRT/litert_quantizer`. This repository is no longer
maintained." The CLI itself renamed too: `aeq` → `litert-quantizer`; package
`ai_edge_quantizer` → `litert_quantizer`.

Confirmed the recipe names did not change in the move — verified
`dynamic_wi8_afp32`, `dynamic_wi4_afp32`, `weight_only_wi8_afp32`,
`weight_only_wi4_afp32` all still exist as public functions in the
successor's own `recipe.py`, same names as the deprecated repo's. So TASK-121's
earlier quantisation-recipe findings stay correct; only the package/CLI name
needs updating to the current one before TASK-122 builds anything.

Separately confirmed: `litert-torch`'s only registered console script is
`litert-torch` itself (`setup.py`: `"litert-torch = litert_torch.cli:main"`);
`export_hf` is a Python module path, not a separate binary — strong evidence
the correct invocation is `litert-torch export_hf ...` (underscore), and the
hyphenated `export-hf` seen in some of Google's own doc examples is likely a
documentation typo. Not 100% confirmed without reading `cli.py` or running
`--help` directly.

Read before: TASK-122 (building the `.litertlm` conversion script) — target
`litert_quantizer`/`litert-quantizer`, not the deprecated names. Full detail:
`research/sources/litert-quantizer-2026-11-06/`.
