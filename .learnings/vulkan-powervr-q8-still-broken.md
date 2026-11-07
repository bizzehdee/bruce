Tested (2026-11-07) whether Q8_0 quantisation avoids the PowerVR/Vulkan wrong-output bug in
`.learnings/gpu-backends-on-test-phones.md` — it does not. Ran `Qwen3.5-0.8B-Q8_0.gguf`
(`unsloth/Qwen3.5-0.8B-GGUF`) through Bruce's own `LlamaCppEngine` (not a standalone binary),
temporarily disabling the PowerVR Vulkan exclusion in `LlamaCppEngine.vulkanUsable` (user-approved,
reverted immediately after this one test — never shipped weakened) so `BackendPreference.VULKAN`
would actually attempt the device instead of returning `BACKEND_UNAVAILABLE`.

Same prompt, both backends, greedy (temperature 0), via `VulkanQ8ReferenceOutputDeviceTest.kt`:

- CPU: `<think>\n\n</think>\n\nParis is the capital of France.` — correct.
- VULKAN: `!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!` — degenerate repeated-token garbage.

This matches the original research's (`research/experiments/vulkan-powervr-2026-09-29/`) own
Q8_0 finding under default settings, and rules out "it's an int4-specific bug" as a hypothesis:
Q8_0 fails just as hard as the lower-bit quantisations already tested. The underlying op-level
failures that research found (`FLASH_ATTN_EXT` 2,288/3,015 fail, batched `MUL_MAT`, `GET_ROWS` on
IQ types) are the more likely cause, and those are architectural/driver bugs independent of
quantisation level, not something a different quant level routes around.

Confirms the TASK-065 PowerVR Vulkan exclusion should stay as-is: no quantisation level tested so
far (4-bit, 8-bit) gives trustworthy Vulkan output on this GPU.

Read before: re-investigating whether some quantisation level "fixes" Vulkan on this device, or
relaxing the PowerVR exclusion for any reason.
