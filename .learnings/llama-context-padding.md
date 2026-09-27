# llama.cpp rounds the context length up to a multiple of 256

Established: 2026-09-27.

`llama_init_from_model` pads `n_ctx` with `GGML_PAD(n_ctx, 256)`
(`src/llama-context.cpp`, line 289 at v0.5.0). A requested context of 128
tokens becomes 256. Code that needs the real context size must read
`llama_n_ctx(ctx)`, not the value it asked for.

Evidence: on both test phones, a generation with `contextLength = 128`
stopped with `CONTEXT_FULL` after prompt + generated tokens reached 256.
