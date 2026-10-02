# Today's date in the system prompt (2026-10-02)

Question: a user asked MiniCPM5 2B on the Pixel 11 "what happened today in history"; it used no
skill and said it had no "today". Does stating the date in the system prompt help, and does a line
telling the model to fetch the date first?

Method: `today-test.py` against `llama-server` on the development machine (build/scratch/bins/serve.sh,
context 4096, greedy, thinking off), Bruce's exported skills-on prompt (`../model-bins-2026-09-30/bruce-prompt.json`)
plus `search_wikipedia` and `fetch_page`, with and without `web_search`. One reply per prompt.

Results (first step's tool calls):

| Model, skills | Prompt | Current prompt | "Get the date first" line | Date stated |
|---|---|---|---|---|
| MiniCPM5 2B, phone's | what happened today in history | search "today in history" | get_datetime | search "October 2" |
| MiniCPM5 2B, phone's | what happened on this day in history | none: asks which day | none: asks which day | search "October 2" |
| MiniCPM5 2B, with web search | what happened on this day in history | none: asks which day | none: asks which day | web search "October 2 history" |
| Qwen3 1.7B, phone's | what happened on this day in history | search "History" | – | search "History of October 2" |
| Qwen3 1.7B, phone's | what day of the week is it tomorrow | get_datetime | – | answers "Saturday" from the stated date |

Both models still call get_datetime for "how many days until christmas". Decision: the system prompt
states the date (not the time, so the prompt stays reusable all day); the "get the date first" line
was not kept.

## Sampling temperature (2026-10-02, later)

On the Pixel, with the date stated, MiniCPM5 2B still answered "what happened today in history"
without searching. The phone samples at temperature 0.8 with no top-k, top-p or min-p cutoff; the
test above was greedy. `temperature-test.py`, same prompt with the date after the guidance, the
phone's skills, MiniCPM5 2B Q4_K_M, seeds 0 to N-1:

| Sampling | Tries | "today in history": search / none | "on this day": search / none |
|---|---|---|---|
| greedy | 1 | search | search |
| 0.8 | 20 | 16 / 4 | 17 / 3 |
| 0.8, min-p 0.05 | 30 | 22 / 6 (2 get_datetime) | 23 / 7 |
| 0.8, min-p 0.1 | 30 | 21 / 8 (1 get_datetime) | 26 / 4 |
| 0.5 | 30 | 27 / 1 (2 get_datetime) | 28 / 2 |

A min-p cutoff did not help; a lower temperature did. Decision: the default temperature is 0.5
(was 0.8). The model bins were measured greedy, so 0.5 is closer to the measured behaviour.

## Skill order (2026-10-02, later)

At 0.5 the Pixel still answered without searching. `PromptDumpDeviceTest` gave the phone's own
prompt (`phone-prompt.txt`, with the question added); the computer test's prompt is `host-prompt.txt`.
They differ in the pun line, the phone-status skills switched on, and the order: on the phone
fetch_page came before search_wikipedia. `order-test.py` sends the raw prompt to llama-server's
`/completion` at 0.5, 30 seeds. (In raw output `<function` is a special token and is not shown;
a call starts with ` name="`.)

| Phone-status skills | Order | "today in history": search / date / none | "on this day": search / none |
|---|---|---|---|
| none | read first | 4 / 1 / 25 | 30 / 0 |
| none | search first | 25 / 0 / 5 | 30 / 0 |
| network (the phone's) | read first | 3 / 6 / 21 | 29 / 1 |
| network (the phone's) | search first | 27 / 1 / 2 | 30 / 0 |
| battery, device, storage | read first | 16 / 0 / 14 | 25 / 5 |
| battery, device, storage | search first | 21 / 0 / 9 | 25 / 5 |
| all four | read first | 28 / 0 / 2 | 29 / 1 |
| all four | search first | 29 / 0 / 1 | 28 / 2 |

The pun line made no difference (5 vs 3 searches of 30). Searches first was never clearly worse
and lifted the phone's case from 3 to 27. Decision: Bruce lists the search skills before fetch_page.
