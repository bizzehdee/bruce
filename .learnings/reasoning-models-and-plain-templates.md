# Ask reasoning models for side tasks with thinking off

Observed 2026-09-28 on the Pixel 11 with Qwen3.5-0.8B (TASK-046).

The auto-summary prompt was first built with `formatChat` (the plain chat template). Qwen3.5's
template then leaves thinking on: the model spent 1,467 characters reasoning inside `<think>` and
wrote no summary, so nothing replaced the old messages (log line "summary had only reasoning").
Built with `formatToolChat(..., enableThinking = false)` and parsed with `parseReply`, the same
prompt gave a 520-character summary on the first try.

Use the tool-chat path with thinking off for any extra model pass (summaries, memory extraction),
and take `ParsedReply.content`, not the raw text.

Also: `uiautomator dump` leaves out long text blocks in Bruce's chat. Read the saved chat from the
app's database (`run-as com.bizzeh.bruce cat databases/conversations.db`, with `-wal` and `-shm`)
rather than concluding from a dump that a reply or note is missing.

Read when adding a model pass that is not a chat reply, or when checking chat contents on a phone.
