# Why neither test phone can use a GPU backend

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
