# ggml hides backend load failures in release builds

Established: 2026-09-27.

`ggml_backend_load_all_from_path` loads with `silent = true` whenever `NDEBUG` is
defined (`ggml-backend-reg.cpp`, line 579). Bruce always builds native code optimised,
so a backend that fails to `dlopen`, or whose `ggml_backend_init` returns null, simply
does not appear. ggml-vulkan also writes some errors to `std::cerr`, which Android
discards.

To diagnose a missing backend, call `dlopen` on the library path yourself and log
`dlerror()`, and raise the ggml log callback to INFO. That is how the findings in
`gpu-backends-on-test-phones.md` were obtained.
