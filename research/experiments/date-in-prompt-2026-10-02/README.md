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
