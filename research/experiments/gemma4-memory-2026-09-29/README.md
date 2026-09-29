# Gemma 4 E4B memory: estimate against llama.cpp (2026-09-29)

Owner report: OBLITERATUS/gemma-4-E4B-it-OBLITERATED Q4_K_M (5,335,289,792 bytes), built to run on
phones, was judged too large for the Pixel 11.

Header (read from the file): 42 layers, 2 KV heads, key/value 512 (256 in sliding-window layers),
sliding window 512, `sliding_window_pattern` with full attention on layers 5, 11, 17, 23, 29, 35
and 41, `shared_kv_layers` 18. Tensors: `per_layer_token_embd` 2,205 MiB of 5,070 MiB, the
largest single tensor.

Host run, pinned llama.cpp, `llama-server -c N -t 8`, CPU:

| | 4K | 32K |
|---|---|---|
| llama.cpp KV (non-SWA: 4 layers, SWA: 20 layers x 1,024 cells) | 64 + 40 = 104 MiB | 512 + 40 = 552 MiB |
| Bruce's old KV estimate (every layer, whole context, 512-wide heads) | 704 MB | 5.5 GB |
| RssAnon after a 32-token reply | 2.1 GB | 2.6 GB |

`CPU_Mapped model buffer 5072.69 MiB`, `CPU_REPACK 1835.16 MiB`: the per-layer embeddings stay in the
mapped file and are read by row. Bruce's new estimate: weights 2,883 MiB (file less that table),
KV 104 / 552 MiB, matching llama.cpp.
