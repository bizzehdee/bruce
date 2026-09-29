# Reusing the evaluated prompt (TASK-056)

Run 2026-09-29. `PromptReuseTimingDeviceTest` (manual): the same three-turn chat ("What time is
it?", "What is 17 times 23?", "Thanks! Tell me a one-line joke.") through BruceRuntime with the
app's skills and Bruce's personality, greedy, 4,096-token context, 4 CPU threads; once with the
previous prompt reused and once with every prompt decoded in full. Times are whole turns.

| Phone | Model | Turn | Without reuse | With reuse |
|---|---|---|---|---|
| Xperia 1 II | Qwen3.5-0.8B Q8_0 | 1 / 2 / 3 | 8.3 / 8.6 / 7.5 s | 8.4 / 2.4 / 1.4 s |
| Xperia 1 II | Llama-3.2-1B Q4_K_M | 1 / 2 / 3 | 25.4 / 28.4 / 14.7 s | 14.5 / 4.4 / 1.8 s |
| XZ Premium | Qwen3.5-0.8B Q8_0 | 1 / 2 / 3 | 67.3 / 74.8 / 73.3 s | 76.2 / 8.3 / 4.7 s |
| XZ Premium | Llama-3.2-1B Q4_K_M | 1 / 2 / 3 | 227.3 / 248.1 / 134.4 s | 120.3 / 26.7 / 11.1 s |

Totals: Xperia 1 II Qwen 24.4 → 12.2 s, Llama 68.5 → 20.7 s; XZ Premium Qwen 215.4 → 89.2 s,
Llama 609.8 → 158.2 s. Prompt tokens decoded over the three turns fell from about 3,800 to about
1,330–1,440.

- Qwen3.5 answered these without calling a skill at temperature 0 (one step per turn). Llama
  called skills; with reuse its second step in a turn decoded about 50–90 tokens instead of the
  whole prompt again.
- The first turn has nothing to reuse. Its time is the ~1,200-token fixed prompt (system prompt
  and skill definitions): about 8 s on the Xperia 1 II and 67–76 s on the XZ Premium, which
  TASK-059 and TASK-054 address.
- Qwen3.5 is a hybrid model: its memory cannot be cut back, so it is reused as an exact extension
  (the template reproduces the reply token for token) or through a checkpoint saved 8 tokens
  before each prompt's end. Llama 3.2 has plain attention memory and is cut back to the shared
  prefix.
- Output is unchanged: `PromptReuseDeviceTest` (stories260K) and `PromptReuseModelDeviceTest`
  (Qwen3.5) compare greedy output with and without reuse; both passed on the Xperia 1 II.
