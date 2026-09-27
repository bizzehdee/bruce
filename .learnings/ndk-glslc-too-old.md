# The NDK's glslc cannot build llama.cpp's Vulkan shaders

Established: 2026-09-27.

NDK 30.0.16248370 ships shaderc v2022.3, which lacks `GL_KHR_cooperative_matrix`. In
llama.cpp v0.5.0 the flash-attention decode shaders are always compiled with
cooperative matrices (`vulkan-shaders-gen.cpp`, lines 926–927), unlike the other
shaders, which are gated on the `GGML_VULKAN_COOPMAT_GLSLC_SUPPORT` feature test. So
the build fails with `'coopmat' : undeclared identifier`.

The build uses the host `glslc` from `PATH` instead (Fedora `glslc` 2026.1 works;
CI installs Ubuntu's `glslc`). See `docs/building.md`. Re-check when upgrading the NDK.
