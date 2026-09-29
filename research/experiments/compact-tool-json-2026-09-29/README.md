# Tool JSON layout in Llama 3.2's chat template (2026-09-29)

Question: Llama 3.2's template prints each tool with `tojson(indent=4)`. Does printing it with less
whitespace cost tool-calling accuracy?

Setup: host `llama-server` (llama.cpp v0.5.0, `--jinja`), bartowski Llama-3.2-1B-Instruct Q4_K_M,
the template from that file with its two `tojson(indent=4)` calls rewritten per layout. System
prompt and the 15 tools of an empty Bruce chat on the Pixel 11 (Bruce personality, Documents and
Camera granted, settings skills on), from `PromptDumpDeviceTest`, with the owner's shorter
descriptions. 12 questions, 8 runs each, temperature 0.8 (Bruce's default). `run.py`,
`results.jsonl`. A call llama-server cannot parse (HTTP 500) is "unparsed".

| Layout | Template call | Prompt tokens | Correct | Unparsed |
|---|---|---|---|---|
| Indented, 4 spaces (as shipped) | `tojson(indent=4)` | 2,009 | 75/96 | 14 |
| Indented, 2 spaces | `tojson(indent=2)` | 2,009 | 76/96 | 8 |
| One line | `tojson` | 1,639 | 85/96 | 0 |
| Compact | `tojson(separators=[",",":"])` | 1,386 | 23/96 | 71 |

Indentation width does not change the count: the tokenizer takes a run of spaces as one token.
Compact JSON made the model copy a definition (`{"type":"function","name":...}`) instead of writing
a call. "Tell me a joke" never got a plain answer in any layout: Llama 3.2 1B calls some tool
whenever tools are offered.

Decision: Bruce prints template JSON on one line (`template_one_line.cpp`), not compact.

## Follow-up: `maxLength` in the tool schemas (same day)

On the Pixel 11, Llama 3.2 1B answered "James smells of pickles" with six tool calls; four were
refused only because the model copied `"maxLength"` from the schema into its arguments. Rerun with
the one-line layout and the Pixel's tools at the time (12 tools, `prompt-dump-4`; settings skills
other than `get_setting` were off, so the two settings-change questions had no matching tool), 15
questions, 8 runs each, a call counting only if its argument names are all in the schema
(`results-maxlength.jsonl`, `STRIP_MAXLENGTH=1` for the second run):

| Schema | Prompt tokens | Correct with valid arguments | Calls naming maxLength | Unparsed |
|---|---|---|---|---|
| With `maxLength` | 1,327 | 82/120 | 2 | 2 |
| Without | 1,273 | 83/120 | 0 | 4 |

Decision: the schema the model sees has no `maxLength`; Bruce's argument check still enforces
every limit. Small talk ("James smells of pickles", a joke) got a tool call in every run either way.
