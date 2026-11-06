# LiteRT-LM Android integration, Tensor SDK, and NPU/TPU delegate

Retrieved 2026-11-06. TASK-117 research: Android integration path, the
Tensor G5/G6 delegate, AICore dependency, and F-Droid/licence fit for the
LiteRT pipeline opened in `plan.md` 2026-11-06.

Pages read:

- `https://developers.google.com/edge/litert-lm/android` (redirected from
  `https://ai.google.dev/edge/litert-lm/android`)
- `https://github.com/google-ai-edge/LiteRT-LM`
- `https://github.com/google-ai-edge/LiteRT-LM/blob/main/LICENSE`
- `https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/getting-started/build-and-run.md`
- `https://developers.google.com/edge/litert/next/tensor-sdk` (redirected from
  `https://ai.google.dev/edge/litert/next/tensor-sdk`)
- `https://developers.google.com/edge/litert/next/litert_lm_npu`
- `https://developers.google.com/edge/litert/next/npu`

## Android integration — confirmed, resolves one open question

A Maven artifact exists and is the documented path:

```
implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
```

(also `litertlm-jvm` for JVM targets). The getting-started page: "While
LiteRT-LM is developed with Bazel, we provide the Maven packages for Gradle or
Maven users." The Kotlin entry point is `Engine`, backend chosen with a typed
object, not a flag or enum:

```kotlin
backend = Backend.GPU()
// or
backend = Backend.NPU(nativeLibraryDir = "...")
```

For NPU: "To use the NPU backend, you might need to specify the directory
containing the NPU libraries"; "if the libraries are bundled with your app,
set it to `context.applicationInfo.nativeLibraryDir`." GPU needs
`<uses-native-library>` manifest entries for `libvndksupport.so` and
`libOpenCL.so`.

Pin an exact version rather than `latest.release` (unpinned upstream versions
are a reproducibility risk independent of this research).

## AICore — no dependency found in any page read

None of the seven pages read (Android getting-started, GitHub README, LICENSE,
build-and-run doc, Tensor SDK page, NPU LLM page, NPU overview page) mentions
AICore or Gemini Nano. This is an absence of evidence, not confirmed absence —
it means these specific documents don't describe an AICore dependency; it does
not rule out one existing deeper in the stack (e.g. inside the closed Tensor
runtime libraries described below, whose internals were not inspected).

## F-Droid / "build from source, no proprietary binary" — this is where it breaks

The GitHub repo is Apache-2.0 (confirmed: full "Apache License Version 2.0,
January 2004" text at the LICENSE link, no extra terms). But licence is not
the blocker; **how the library actually ships** is:

- The build doc states plainly: "You do **not** need to build this project
  from source to use it in your apps," and tells you to "run `git lfs pull` to
  fetch the latest prebuilt binaries."
- The GPU path uses checked-in prebuilt `.so` files under
  `prebuilt/android_arm64/*.so` — arm64 only, and prebuilt, not compiled by
  the consuming app's own build.
- The Tensor/NPU path requires building `@litert//litert/vendors/google_tensor/dispatch:dispatch_api_so`
  from an **external** `@litert` Bazel dependency, whose source and licence
  this research did not verify (out of scope for this pass; needs its own
  check if the Tensor path is pursued further).
- The Tensor **model** download itself needs "login and acknowledge the form
  to obtain permission to download the file," and the NPU build doc points to
  signing up for an "Early Access Program."
- The Tensor SDK's own page is headed "Sign up for access to Google Tensor SDK
  Beta" — a gated private beta, not a public, redistributable release. No
  statement anywhere found on third-party redistribution rights.
- The NPU overview page states: "Beta: Google Tensor SDK doesn't yet support
  on-device (JIT) compilation" — i.e. models must be precompiled for the
  target chip ahead of time (consistent with the publisher-side `_Google_Tensor_G5`
  / `_Google_Tensor_G6` file variants already seen in
  `research/sources/litert-lm-gemma-4-2026-11-06/`), not compiled by Bruce or
  the user at install/run time.
- From LiteRT 2.3.0 on, NPU runtime libraries are separately published as
  `com.google.ai.edge.litert:litert-npu-runtime-google-tensor:<VERSION>` on
  Maven, but Google's own documented delivery route is **Google Play Feature
  Delivery** ("Play for On-device AI"), not a plain bundled dependency. Nothing
  found states whether these libraries may be redistributed through F-Droid or
  a signed GitHub Releases APK outside Google Play.

**Net effect**: this is a real conflict with the existing plan constraint "F-Droid
requires the app to build from source with no proprietary dependencies," not
just with the LiteRT-specific open questions raised when the pipeline was
scoped in. The CPU/GPU LiteRT-LM path ships prebuilt binaries rather than
building from the app's own source (a likely problem for F-Droid regardless of
licence); the Tensor G5/G6 path adds a gated beta, an unverified external
Bazel dependency, and Play-specific distribution on top. Brought back to the
owner rather than built around, per TASK-117's own instructions.

## Supported chips — Tensor G6 is confirmed, not just G5

The Tensor SDK page lists both: "Google Tensor G6 (Tensor_G6)" and "Google
Tensor G5 (Tensor_G5)." The NPU LLM support table shows Gemma4-E2B on
`Tensor_G6` (4-bit per-channel, 8192 context, with a figure recorded as 2642 —
unit not confirmed by this pass, likely tokens/sec or similar; re-check before
quoting it) and Gemma3-1B on both `Tensor_G6` and `Tensor_G5`. Whether
Gemma4-E2B itself runs on Tensor_G5 was not shown in the retrieved table — only
G6 was listed for that model. Qualcomm, MediaTek and Intel chips are also in
the same NPU table, same gated/beta mechanism presumably applying to all of
them (not verified per-vendor).

## Open questions this resolves or sharpens

- Android integration path: **resolved** — Maven AAR + Kotlin `Engine`/`Backend` API.
- AICore dependency: **not found as a stated dependency**, but not provably
  absent either; the closed Tensor runtime libraries were not inspected.
- F-Droid buildability: **sharpened into a likely blocker** — prebuilt
  binaries (git-lfs, Maven) rather than from-source builds on the CPU/GPU
  path; a gated beta, an unverified external dependency and Play-specific
  delivery on the Tensor path.
- Licence: **Apache-2.0 confirmed** for the LiteRT-LM repo itself (GPL-3.0-or-later
  compatible); the Tensor runtime library's own licence was not checked.
