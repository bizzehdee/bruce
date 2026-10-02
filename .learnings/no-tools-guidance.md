# Do not tell a model to use tools it has not been given

Established: 2026-10-01, model-binning host screen (TASK-108), fixed in c3ba23b.

With every skill off, Bruce's system prompt still said "Use a tool for anything a tool can look
up… do not guess those". Gemma 4 E2B then answered "What time is it?" with made-up tool code
(`<tool_code> print(datetime.now()) </tool_code>`), which the user would see as the reply, and
LFM2.5-2.6B wrote its own tool-call markup with invented tool names. With no skill offered the
system prompt now says the model has no tools, cannot check the time, date or phone status, and
should say so rather than guess or write tool calls (`BruceRuntime.NO_SKILLS_GUIDANCE`).

Results in `research/experiments/model-bins-2026-09-30/` taken before c3ba23b used the old
skills-off prompt; the skills-off cases were run again on the host and the phones.

## A model trained to think cannot simply be told not to (2026-10-01)

LFM2.5 2.6B's template ends every generation prompt with `<think>` and has no switch, so it reasoned
in every reply (337 tokens before "Canberra"). Closing the block in the prompt (`<think></think>`)
stopped the reasoning but broke the model, measured on the Pixel 11 with all 41 cases: plain
answers with skills on fell from 0.90 to 0.07, it called a tool in 27 of 41 cases that needed
none, and with skills off it wrote made-up calls such as `[google(query=...)]`. Leave thinking on
for templates that always open it (owner, 2026-10-01); measure any such model with its reasoning.

## State today's date in the system prompt (2026-10-02)

Small models do not turn "today" or "on this day" into a date lookup: MiniCPM5 asked which day,
or searched "today in history". With "Today is Friday 2 October 2026." in the prompt, it searched
"October 2"; Qwen3 1.7B searched "History of October 2" instead of "History". A line telling the
model to fetch the date first changed nothing. Only the date is stated, so the prompt is the same
all day and prompt reuse holds. Data: `research/experiments/date-in-prompt-2026-10-02/`.

## Test prompt changes at the phone's temperature, not only greedy (2026-10-02)

A prompt that makes a model call a skill under greedy decoding can still miss at the phone's
temperature. MiniCPM5 2B searched every time greedy, but skipped the search in 15–20% of tries at
0.8 (Bruce has no top-k, top-p or min-p cutoff). A min-p cutoff did not help; 0.5 cut the misses to
about 5%, and became the default. Sample 20–30 seeds before trusting a prompt change. Data:
`research/experiments/date-in-prompt-2026-10-02/temperature-test.py`.

## Skill order in the prompt matters to small models (2026-10-02)

With fetch_page listed before search_wikipedia, MiniCPM5 2B searched for "what happened today in
history" 3 times in 30; with the search first, 27. Other skill sets moved the rate between 4 and 29
too, so test a prompt change against the phone's real prompt (`PromptDumpDeviceTest`), not a
hand-built one. Searches are now listed before fetch_page. Data:
`research/experiments/date-in-prompt-2026-10-02/order-test.py`.
