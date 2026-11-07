A self-compiled Tensor G6 AOT `.litertlm` (TASK-119/TASK-121/TASK-122's `tools/litertlm-convert`,
`gemma-3-270m-it`, built 2026-11-06) was pushed to the owner's Pixel 11 and run through
`LiteRtNpuDeviceTest`. Two results, both via logcat (`am instrument` alone only shows the final
assertion, not the native cause):

**NPU attempt: identical failure to Google's own official file.** `Failed to allocate tensors`,
same as `.learnings/litert-lm-npu-no-cpu-fallback.md`'s two independently-compiled models. This
rules out "our own build is broken" as the cause of that failure — a third, independently-built
model hits the exact same wall, reinforcing that it's a device/runtime-level (firmware or
dispatch-library ABI) problem, not something fixable by changing how the `.litertlm` is built.

**CPU fallback: a different, more specific failure than Google's files.** Not the expected
`Main backend constraint mismatch` or `Signature not found: decode` from the learning above.
Instead: `FAILED_PRECONDITION: RET_CHECK failure
(third_party/odml/litert_lm/runtime/executor/litert_compiled_model_executor_utils.cc:423)
!model_signatures.input_positions.empty() Input positions not found.` Suspected cause, not yet
confirmed: `--split_cache` (added to `convert.py`'s Tensor branch 2026-11-06 to fix a
`compiler_worker` segfault — see `tools/litertlm-convert/convert.py`'s comment on that branch)
restructures the exported model to take explicit position inputs, a shape the installed
`litertlm-android:0.19.1` runtime's CPU executor may not read the same way AOT-only execution
does. Not isolated: unknown whether `--split_cache` alone causes this, or the combination with
`--externalize_embedder`/the AOT packaging path.

Net effect: a locally-built Tensor-AOT `.litertlm` is no more runnable on this Pixel 11 than
Google's own — same NPU wall, and a CPU fallback that was never going to work anyway (AOT files
have no CPU path by design, per the learning above). Building our own file does not route around
the device-level NPU block.

Read before: any further attempt to get a self-compiled Tensor AOT model running on-device, or
investigating why `--split_cache` models behave differently from non-split ones under
`litertlm-android`'s executor.
