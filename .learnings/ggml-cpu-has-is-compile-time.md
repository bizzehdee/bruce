# ggml's `ggml_cpu_has_*` functions report build flags, not the running CPU

Established: 2026-09-27.

In llama.cpp v0.5.0, `ggml_cpu_has_dotprod()` and similar functions return
whether the library was compiled with the feature (`__ARM_FEATURE_DOTPROD`),
not whether the phone supports it (`ggml/src/ggml-cpu/ggml-cpu.c`, around
line 3819). Bruce builds for baseline ARMv8, so they would report "no" even
on the Xperia 1 II, which has DOTPROD.

Bruce detects features itself from `getauxval(AT_HWCAP/AT_HWCAP2)`
(`cpu_features.cpp`).

Since TASK-008, llama.cpp is built with `GGML_CPU_ALL_VARIANTS`, and ggml loads the
best CPU variant at runtime: `android_armv8.2_2` on the Xperia 1 II and
`android_armv8.0_1` on the XZ Premium. The loaded variant reports its features through
`ggml_backend_get_features`. ggml names FP16 vector arithmetic `FP16_VA` there.

Evidence: source above; `/proc/cpuinfo` on the Xperia 1 II lists
`asimdhp asimddp`, on the XZ Premium neither.
