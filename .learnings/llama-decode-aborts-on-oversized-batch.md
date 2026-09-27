# `llama_decode` aborts the process if a batch exceeds `n_batch`

Established: 2026-09-27.

Passing `llama_decode` more tokens than the context's `n_batch` does not
return an error. It calls `ggml_abort`, which raises SIGABRT and kills the
app. Prompts are therefore decoded in chunks of `llama_n_batch(ctx)`
(`llama_jni.cpp`, `evaluatePrompt`).

Evidence: logcat on the Xperia 1 II showed `Fatal signal 6 (SIGABRT)` with
`ggml_abort` called from `llama_context::decode`, for a ~150-token prompt
with `n_batch = 128`. Regression tests:
`GenerationDeviceTest.promptLongerThanRequestedContextButWithinPaddedContextIsEvaluated`
and `promptLongerThanBatchIsEvaluatedInChunks`.
