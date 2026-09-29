# Vulkan on the Pixel 11's PowerVR GPU (TASK-050)

Run 2026-09-29. llama.cpp v0.5.0 (ggml 7fe450e19), the revision Bruce pins. Shaders built with the
host `glslc` (shaderc v2026.1) that Bruce's build uses, Vulkan and SPIR-V headers from
`app/src/main/cpp/external`.

## Step 1: a GPU known to work (AMD RX 6750 XT, RADV, Vulkan 1.4, no matrix cores)

- `test-backend-ops -b Vulkan0` (every operation): 0 failures
  (`host-rx6750xt-test-backend-ops.log.gz`).
- `llama-simple -m stories260K.gguf -n 24 "Once upon a time"`: CPU and `-ngl 99` both give
  ", there was a little girl named Lily. She loved to play outside in the p", the reference.

So the pinned llama.cpp Vulkan code and shaders built with this `glslc` compute correctly on a
working driver. RADV has no cooperative-matrix support, so those shader variants were not exercised.

## Step 2: the Pixel 11 (PowerVR C-Series CXTP-48-1536 MC1, Android 17, subgroup size 128)

Built for arm64 with Bruce's backend settings (`GGML_BACKEND_DL`, every CPU variant, OpenMP),
run from `/data/local/tmp` with `adb shell`, without Bruce.

Greedy stories260K, "Once upon a time", 24 tokens:

| Setting | Output |
|---|---|
| `-ngl 0` | reference |
| `-ngl 99` (flash attention auto = on) | `happ happenily a a..... a a a` — Bruce's wrong output exactly |
| `-ngl 99 -fa off` | different garbage on every run |
| `-fa off` + `GGML_VK_SERIALIZE_SUBMISSIONS=1` | reference, 3/3 runs |
| `-fa off` + `GGML_VK_DISABLE_ASYNC=1` | reference, 3/3 runs |
| `-fa off` + any one of DISABLE_COOPMAT, DISABLE_FUSION, DISABLE_GRAPH_OPTIMIZE, DISABLE_MULTI_ADD, MAX_NODES_PER_SUBMIT=1 | wrong, 0/3 |
| `-fa on` + every switch above | wrong, 0/3 |

Greedy Qwen3.5-0.8B Q8_0, "Write a short paragraph about why the sky is blue.", 64 tokens:
CPU answers directly ("The sky appears blue because of … Rayleigh scattering"). GPU with `-fa off`
gives garbage (`.liciis. 1000000…`), also with `GGML_VK_DISABLE_ASYNC=1`. With
`GGML_VK_SERIALIZE_SUBMISSIONS=1` it is coherent but not the CPU's text (it starts a "Thinking
Process" list), so it cannot be shown to be right. "The capital of France is" (16 tokens) matched the
CPU with either switch.

`test-backend-ops -b Vulkan0`, one operation at a time (each op checked alone, not the whole graph):

| Op | Result |
|---|---|
| FLASH_ATTN_EXT | 727 pass, 2,288 fail (`pixel-tbo-FLASH_ATTN_EXT.log.gz`) |
| MUL_MAT, `type_a=f32` | 3 fail (batched `m=64,n=77,k=77,bs=[12,1]`); 0 with `GGML_VK_DISABLE_COOPMAT=1` |
| GET_ROWS | 45 fail, all IQ types (`pixel-tbo-GET_ROWS.log.gz`) |
| RMS_NORM, MUL, ADD, ROPE, SOFT_MAX, CPY, SET_ROWS, SCALE, GLU, UNARY, CONT | all pass |

Standalone CPU speed from `adb shell` was 1–2 tokens/s at 2–8 threads, far below Bruce's own CPU
path, so no speed comparison is drawn from these runs.

The same failures are reported upstream for this GPU (see
`research/sources/llama-cpp-powervr-issues-2026-09-29`).
