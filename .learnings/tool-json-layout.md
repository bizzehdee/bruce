# Tool JSON on one line, never compact

Established 2026-09-29 (research/experiments/compact-tool-json-2026-09-29).

Llama 3.2's chat template prints tool definitions with `tojson(indent=4)`, which made up much of
Bruce's empty-chat prompt. Printed on one line (plain `tojson`, which keeps ", " and ": "), the
prompt fell from 2,009 to 1,639 tokens and Llama 3.2 1B called tools correctly more often (85/96
against 75/96). Fully compact JSON (no spaces) saved 250 more tokens but broke tool calling (23/96):
the model copied the definitions instead of calling. Indentation width is free: a run of spaces is
one token.

Bruce rewrites only `tojson(indent=N)` in a model's own or fetched template (`oneLineToolJson` in
`template_one_line.cpp`, applied in `chatTemplatesInit`). A model with a separate `tool_use`
template is left alone, since an override would drop it.

String `maxLength` is not shown to the model either: Llama 3.2 1B copied it into calls as an
argument, which Bruce then refused. Limits live only in `InputSchema.check`.

Read before changing how templates or tool definitions are printed.
