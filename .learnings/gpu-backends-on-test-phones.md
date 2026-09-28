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
so it passes the 1.2 rule and Auto used to choose it. ggml's Vulkan backend loads and runs but
computes wrongly: greedy stories260K gave `" happ\uFFFDenily a a..... a a a a"` instead of
llama.cpp's CPU reference `", there was a little girl named Lily…"`, and a device test run
under Auto froze the phone until a forced reboot (`sys.boot.reason` `reboot,longkey`). The
same app on the CPU backend (i8mm variant), 1 and 4 threads, matches the reference.
ggml's OpenCL backend drops the device as unsupported.

So Auto always uses the CPU; Vulkan and OpenCL run only when the user picks them, marked
experimental. A "usable" device (API version check) is not evidence that it computes
correctly. Before trusting any GPU, run the manual-only `GpuReferenceOutputDeviceTest`.
Evidence: `GpuReferenceOutputDeviceTest` and `ReferenceOutputDeviceTest` on the Pixel 11.
