# llama.cpp issues about Vulkan on Imagination PowerVR

Retrieved 2026-09-29 with `gh issue view <n> --repo ggml-org/llama.cpp --json ...` (title, state,
body, comments). All open at retrieval.

- `issue-28214.json`: https://github.com/ggml-org/llama.cpp/issues/28214 — Pixel 11 Pro, the same
  GPU as Bruce's Pixel 11 (CXTP-48-1536, driver 1.4.317). GPU firmware crashes
  (`GUILTY_OVERRUNING`, `FW_PAGEFAULT`) that wedge the compositor; FLASH_ATTN_EXT wrong by head size;
  dequant `mul_mat_vec` pipelines fail to compile (PR #28341); whole-model output wrong and different
  on every run even with flash attention off, which `test-backend-ops` misses because it checks one
  node at a time. Chrome's Dawn carries PowerVR workarounds (workgroup memory zero-init, crbug
  479242793). The reporter disabled the vendor in their app.
- `issue-28343.json`: https://github.com/ggml-org/llama.cpp/issues/28343 — IQ types give different
  results run to run on the same GPU.
- `issue-28502.json`: https://github.com/ggml-org/llama.cpp/issues/28502 — Pixel 10 Pro (PowerVR
  D-series): garbled output; the reporter's workaround (coopmat and integer dot product off) is for
  that GPU.
