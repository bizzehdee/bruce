# Why no test phone uses a GPU backend

Established: 2026-09-27, llama.cpp v0.5.0.

**Vulkan.** ggml requires a Vulkan 1.2 instance (`ggml-vulkan.cpp`, line 4978) but
never checks the device's own API version.
- Xperia 1 II (Android 12, Adreno 650): the Vulkan loader reports no instance version
  above 1.1 (`cmd gpu vkjson` has no `apiVersion`), so ggml registers no Vulkan device.
- XZ Premium (LineageOS 20 / Android 13, Adreno 540): the loader is 1.3, so ggml
  registers the GPU, but the device is Vulkan 1.1. Allocating a model buffer on it
  crashed the app with a null dereference in the driver's `CreateBuffer`.
Bruce therefore marks a Vulkan device usable only if the device reports 1.2 or later
(`vulkan_devices.cpp`, `LlamaCppEngine.getCapabilities`).

**OpenCL.** `libggml-opencl.so` needs `clCreateBufferWithProperties`, an OpenCL 3.0
entry point. Both phones' vendor drivers are older, so `dlopen` fails with
`cannot locate symbol "clCreateBufferWithProperties"`. llama.cpp's OpenCL backend
targets Adreno 7xx and later.

Evidence: logcat from both phones, 2026-09-27, captured with a temporary `dlopen` +
`dlerror` probe, and the crash backtrace
(`libvulkan.so CreateBuffer` ← `libggml-vulkan.so` ← `llama_model_base::load_tensors`).

**Pixel 11 (2026-09-28, Android 17, Tensor G6, PowerVR C-Series CXTP-48-1536).** Vulkan 1.4,
so it passes the 1.2 rule and Auto used to choose it. Bruce's Vulkan build loads and runs there
but gives wrong output: greedy stories260K gave `" happ\uFFFDenily a a..... a a a a"` instead of
llama.cpp's CPU reference `", there was a little girl named Lily…"`, and a device test run
under Auto froze the phone until a forced reboot (`sys.boot.reason` `reboot,longkey`). The
same app on the CPU backend (i8mm variant), 1 and 4 threads, matches the reference.
ggml's OpenCL backend drops the device as unsupported.

**Cause (established 2026-09-29, TASK-050): the PowerVR driver, not Bruce.** Eliminated in turn:
- *Our shader build and llama.cpp's Vulkan code in general*: the pinned llama.cpp built with the
  same host `glslc` passes every `test-backend-ops` case on an AMD RX 6750 XT (RADV) and gives the
  stories260K reference there.
- *Bruce's integration*: standalone llama.cpp built with Bruce's backend settings, run on the Pixel
  without Bruce, gives Bruce's wrong output exactly.
On the Pixel, FLASH_ATTN_EXT fails 2,288 of 3,015 op tests, a batched F32 MUL_MAT fails with
cooperative matrices, IQ GET_ROWS fail, and whole-graph output changes from run to run although
most single ops pass. Flash attention off plus `GGML_VK_SERIALIZE_SUBMISSIONS=1` fixed stories260K
and a short Qwen3.5 answer, but a 64-token Qwen3.5 answer still differed from the CPU's, so no
known setting makes this GPU trustworthy. The phone freeze matches upstream's firmware crashes on
the same GPU (llama.cpp #28214, `GUILTY_OVERRUNING` / `FW_PAGEFAULT`). Evidence:
`research/experiments/vulkan-powervr-2026-09-29`, `research/sources/llama-cpp-powervr-issues-2026-09-29`.

Since TASK-065 (2026-09-29) Bruce treats any Imagination Technologies GPU (vendor ID `0x1010`) as unusable for Vulkan, so the Pixel 11 is no longer offered it.

So Auto always uses the CPU; Vulkan and OpenCL run only when the user picks them, marked
experimental. A "usable" device (API version check) is not evidence that it computes
correctly. Before trusting any GPU, run the manual-only `GpuReferenceOutputDeviceTest`.
Evidence: `GpuReferenceOutputDeviceTest` and `ReferenceOutputDeviceTest` on the Pixel 11.
