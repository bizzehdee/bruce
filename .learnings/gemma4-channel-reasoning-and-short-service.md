# Gemma 4 marks reasoning with channel tags; the reply service ended a visible turn

Observed 2026-10-06 on the Pixel 11 (Gemma 4 E4B, TASK-048 reply service).

- Gemma 4 writes reasoning as `<|channel>thought\n…<channel|>`, not `<think>…</think>`. The chat
  stored it raw, so the reasoning showed as the answer. `ThinkingText` and `BruceRuntime.THINKING`
  now know both pairs. Add any new model's pair to `ThinkingText.MARKERS`; it also knows `[THINK]`, `<thinking>`, `<seed:think>` and `<|START_THINKING|>`. gpt-oss (`<|channel|>analysis<|message|>`) is not handled.
- A reply stopped mid-sentence exactly 3 minutes after the turn began. Cause: `ReplyService` is a
  short foreground service; Android calls `onTimeout` at about 3 minutes, and it stopped the turn
  even with Bruce on screen. TASK-115 moved the service to `specialUse`, which Android does not time out; the turn limit (Settings, 1 to 10 minutes) now ends a reply.
- Evidence: `adb logcat` line "Short FGS timed out: ServiceRecord{… ReplyService}" at the cut time.

Read when a reply ends early, or when a new reasoning model shows its thinking in the chat.
