Extends `.learnings/vulkan-powervr-q8-still-broken.md`: tested five quantisations of
`gemma-4-E2B-it` (unsloth GGUFs: Q8_0, Q6_K, Q5_K_M, UD-Q4_K_XL, IQ4_NL) on llama.cpp's Vulkan
backend via Bruce's own `LlamaCppEngine`, with the PowerVR exclusion temporarily disabled
(user-approved, reverted immediately after). Unlike the smaller models tested before (Gemma 3
270M, Qwen3.5 0.8B — both gave fast garbage text), **every Gemma 4 E2B quant failed to even
reach generation**:

| Quant | File size | Result |
|---|---|---|
| Q8_0 | 4.7 GB | hung, RSS flat ~9 min, force-stopped |
| UD-Q4_K_XL | 3.2 GB | hung, RSS flat at ~2.5 GB, force-stopped after 60s |
| IQ4_NL | 3.0 GB | hung, RSS flat at ~200 MB (stalled very early, before full model load) |
| Q5_K_M | 3.4 GB | hung, RSS flat at ~60 MB (stalled even earlier) |
| Q6_K | 4.5 GB | crashed within ~11s, no tombstone/crash-buffer entry, no OOM log found — cause not confirmed, likely a silent OOM kill given it's the largest file |

CPU loaded and answered correctly for all five, confirming the files themselves are fine.

**The hang is a real device risk, not just wrong output.** The Q8_0 run's ~9-minute hang
preceded a full phone UI freeze (unresponsive touchscreen) that required `adb reboot` to clear —
adb itself stayed reachable throughout, so it was a display/compositor-level wedge, not a full
kernel hang, but the user could not interact with the phone at all until it rebooted. Subsequent
runs were caught early with a polling watchdog (kill after 60s of flat RSS) and did not freeze
the phone again, device stayed on `adb get-state` → `device` throughout.

Net conclusion: this is not a per-model or per-quant bug, nor specific to the smaller models
already tested. llama.cpp's Vulkan backend is unreliable on this PowerVR GPU across the full
range tried — fast garbage on small models, hangs (and OOM-like crashes) on a ~2.6B model
regardless of quantisation. Reinforces, rather than narrows, the TASK-065 exclusion.

**Practical rule for any future test on this GPU**: never run a Vulkan load unattended. Use a
polling watchdog that force-stops the app after a short fixed window of no RSS change, since a
hang here can silently escalate into a full device freeze if left running.

Read before: any further Vulkan/PowerVR investigation, or running an unattended GPU test on the
Pixel 11.
