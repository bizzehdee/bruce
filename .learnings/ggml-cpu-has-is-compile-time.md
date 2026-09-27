# ggml's `ggml_cpu_has_*` functions report build flags, not the running CPU

Established: 2026-09-27.

In llama.cpp v0.5.0, `ggml_cpu_has_dotprod()` and similar functions return
whether the library was compiled with the feature (`__ARM_FEATURE_DOTPROD`),
not whether the phone supports it (`ggml/src/ggml-cpu/ggml-cpu.c`, around
line 3819). Bruce builds for baseline ARMv8, so they would report "no" even
on the Xperia 1 II, which has DOTPROD.

Bruce detects features itself from `getauxval(AT_HWCAP/AT_HWCAP2)`
(`cpu_features.cpp`). A consequence for performance work: the current
`libbruce.so` does not use DOTPROD, FP16 or I8MM instructions on any phone.

Evidence: source above; `/proc/cpuinfo` on the Xperia 1 II lists
`asimdhp asimddp`, on the XZ Premium neither.
