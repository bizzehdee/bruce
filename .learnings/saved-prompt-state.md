# Saving the evaluated fixed prompt (TASK-109)

Established: 2026-10-01. Code: `inference/SavedPrompts.kt`, `evaluatePrompt` in `llama_jni.cpp`.
Device tests: `SavedPromptDeviceTest`, `SavedPromptTimingDeviceTest`.

- **It works for every architecture tested, including hybrid and sliding-window models.**
  `llama_state_seq_save_file` / `load_file` restore Qwen3, Qwen2.5, MiniCPM5, LFM2.5 (hybrid
  convolution), Qwen3.5 (gated delta net) and Gemma 4 (sliding window) with greedy output equal to a
  full evaluation. Prompt reuse within a chat cannot restore hybrid and sliding-window models
  without checkpoints; the saved file can, because it holds the whole sequence state.
- **Measured first reply after loading, Bruce personality prompt (170–203 tokens), 4 threads:**

  | Phone | Model | Full prompt | From saved state | State file |
  |---|---|---|---|---|
  | Xperia 1 II | Qwen3-1.7B Q5_K_M | 3.8 s | 1.2 s | 18.5 MB |
  | Xperia 1 II | LFM2.5-2.6B Q4_0 | 3.7 s | 1.3 s | 3.1 MB |
  | Xperia 1 II | Qwen3.5-2B Q6_K | 4.3 s | 1.5 s | 22.3 MB |
  | Xperia 1 II | Gemma 4 E2B Q3_K_M | 10.1 s | 2.2 s | 3.4 MB |
  | Xperia 1 II | MiniCPM5-2B Q4_K_M | 4.0 s | 1.2 s | 6.9 MB |
  | XZ Premium | Qwen3-1.7B Q4_K_M | 25.7 s | 6.3 s | 18.5 MB |
  | XZ Premium | Qwen2.5-1.5B Q4_K_M | 24.8 s | 1.6 s | 4.6 MB |
  | XZ Premium | Qwen3-0.6B Q8_0 | 8.6 s | 1.9 s | 18.5 MB |

  The remaining time is loading the file and decoding the user's message (9 tokens; 18 for
  Gemma 4). Saving adds almost nothing to the first evaluation. The skills prompt is about
  630 tokens, so the saving in real use is about 3.5 times larger.
- **State size follows the KV layout, not the file size.** Models with few KV heads or a
  recurrent state (LFM2.5, Gemma 4) save 3 MB; Qwen3 saves 18.5 MB for 170 tokens.
- **The saved state is personal data only if the prompt holds personal data,** so Bruce saves
  it only when the fixed prompt has no memories, no chat summaries and no grant names
  (`BackendChat.savable`). It lives in the cache directory, which Clear all data empties.
