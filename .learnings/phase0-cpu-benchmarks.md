# Phase 0 CPU benchmarks on the test phones

Established: 2026-09-27. llama.cpp v0.5.0, CPU backend (neither phone has a usable GPU;
see `gpu-backends-on-test-phones.md`). Harness:
`app/src/androidTest/java/com/bizzeh/bruce/benchmark/InferenceBenchmark.kt`, one run per
setting, context 2048, 49-token prompt, 64 generated tokens, greedy, after an 8-token warm-up.
Phones were on USB power at room temperature; no thermal control.

Models (not in the repository):

| Model | Source | Revision | SHA-256 | Licence |
|---|---|---|---|---|
| Qwen3-0.6B-Q4_0.gguf (429 MB) | huggingface.co/ggml-org/Qwen3-0.6B-GGUF | `b5f37287796e5be0ea3dab2e7430873fb3f73e49` | `da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4` | Apache-2.0 |
| Qwen3-1.7B-Q4_K_M.gguf (1.28 GB) | huggingface.co/ggml-org/Qwen3-1.7B-GGUF | `daeb8e2d528a760970442092f6bf1e55c3b659eb` | `d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5` | Apache-2.0 |

Tokens per second (prompt / generation) by thread count:

| Phone | Model | 1 | 2 | 4 | 8 |
|---|---|---|---|---|---|
| Xperia 1 II (SD865, `android_armv8.2_2`) | 0.6B Q4_0 | 97 / 35.0 | 168 / 49.5 | **322 / 59.1** | 249 / 37.4 |
| Xperia 1 II | 1.7B Q4_K_M | 23 / 9.8 | 41 / 15.3 | **80 / 20.2** | 55 / 13.8 |
| XZ Premium (SD835, `android_armv8.0_1`) | 0.6B Q4_0 | 9 / 6.1 | 13 / 11.2 | **25 / 14.0** | 24 / 12.9 |
| XZ Premium | 1.7B Q4_K_M | 3 / 2.3 | 4 / 3.3 | 8 / **5.1** | **10** / 3.5 |

Load time: 0.7–2.8 s on the Xperia 1 II, 0.8–2.9 s on the XZ Premium.

Resident memory added by load and generation (`VmRSS` delta) against the TASK-010 estimate:

| Phone | 0.6B Q4_0 | 1.7B Q4_K_M |
|---|---|---|
| Xperia 1 II | 879 MB vs 633 MB (+39%) | 2,256 MB vs 1,447 MB (+56%) |
| XZ Premium | 645 MB vs 633 MB (+2%) | 1,459 MB vs 1,447 MB (+1%) |

Conclusions:

1. **Threads.** 4 threads gives the best generation speed on both phones; all 8 cores is
   25–35% slower. Both SoCs have 4 performance cores. The engine's current default (all
   cores) is wrong for these phones.
2. **Memory estimate.** Accurate on both phones for memory that cannot be reclaimed; see
   the memory breakdown below (TASK-019, 2026-09-28).
3. **Budget phone.** On the 4 GB XZ Premium, a 0.6B Q4 model generates 14 tok/s; a 1.7B
   Q4_K_M model manages 5 tok/s, which is slow for chat.
4. **GPU order** (Vulkan before OpenCL) remains unmeasured.

## Memory breakdown (TASK-019, 2026-09-28)

`/proc/self/statm` resident memory split into anonymous and file-backed pages, after load and
generation, 4 threads, context 2048 (KV cache 224 MB for both models):

| Phone | Model (file size) | Anonymous | File-backed | Estimate |
|---|---|---|---|---|
| Xperia 1 II | 0.6B Q4_0 (409 MB) | 560 MB | 318 MB | 633 MB |
| Xperia 1 II | 1.7B Q4_K_M (1,223 MB) | 1,288 MB | 967 MB | 1,447 MB |
| XZ Premium | 0.6B Q4_0 (409 MB) | 244 MB | 403 MB | 633 MB |
| XZ Premium | 1.7B Q4_K_M (1,223 MB) | 243 MB | 1,216 MB | 1,447 MB |

Cause of the earlier gap, confirmed: on the DOTPROD phone llama.cpp repacks weights into an
anonymous buffer (anonymous minus KV cache ≈ the weights: 336 of 409 MB, 1,064 of 1,223 MB),
and most of the memory-mapped file stays resident too. On the baseline phone there is no
repacking: anonymous memory is the KV cache plus about 20 MB of compute buffers, and the file
is fully mapped. Compute buffers are therefore not the cause.

The resident file pages are clean page cache; Android can drop them under pressure because
the repacked copy is what inference uses. So the memory Bruce needs is weights + KV cache +
~20 MB, which the estimate covers on both phones. `ModelMemory` is unchanged apart from
documenting this.
