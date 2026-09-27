# llama.cpp allocates on every registered GPU unless given a device list

Established: 2026-09-27.

`llama_model_params.devices` is a NULL-terminated device list. If it is NULL,
llama.cpp uses every registered device, and it allocates buffers on a GPU even when
`n_gpu_layers = 0`. On the XZ Premium that reached a broken Vulkan driver and crashed
the app during a CPU-only load.

`loadModel` passes an explicit empty list (`{nullptr}`) so a CPU load never touches a
GPU. Backend selection (TASK-009) must pass exactly the devices it chose.

Evidence: crash backtrace from the XZ Premium before the change; the full device test
suite passed on it after the change.
