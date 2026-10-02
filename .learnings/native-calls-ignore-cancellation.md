# Coroutine cancellation does not reach a blocking native call (2026-10-02)

`withTimeout` and cancelling a flow only take effect at suspension points. llama_decode on a long
prompt is one JNI call that can run for minutes on a phone CPU (8,500 tokens: 500 s on a
Snapdragon 865), so the runtime's time limit and the chat's Stop did nothing until it returned.
Bruce now sets llama.cpp's abort callback per context (`llama_set_abort_callback`), and
LlamaCppEngine starts a watcher on Dispatchers.Default that sets the flag when the generation's
coroutine is cancelled; the decode returns within a graph node and the call reports STOPPED.

Any other long native call (embedding a big file, loading a model) has the same limitation.
How it was found: `research/experiments/pixel-chat-replay-2026-10-02/`.
