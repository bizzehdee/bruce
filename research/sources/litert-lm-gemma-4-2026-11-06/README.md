# LiteRT-LM model format, from the litert-community Gemma 4 E2B repository

Retrieved 2026-11-06 from
`https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm` (model card)
and `https://huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm/tree/main?recursive=true`
(file tree, for sizes). User supplied the URL as a candidate model source for
the LiteRT pipeline opened in `plan.md` 2026-11-06 (TASK-117 onward).

This is a preliminary finding, not the full TASK-117 research. It corrects two
assumptions made when the pipeline was first scoped into `plan.md`, which
assumed plain TFLite `.tflite` files and a "TensorFlow Lite Android runtime"
library. TASK-117 must confirm or supersede the rest of this, in particular
the Tensor G5/G6 acceleration mechanism and Android integration path, before
`LiteRtEngine` is built.

## Files in the repository

All large files are Git LFS. Sizes from the recursive tree API, not the model
card:

| File | Size | Notes |
|---|---|---|
| `gemma-4-E2B-it.litertlm` | ~2.59 GB | default/CPU |
| `gemma-4-E2B-it-gpu.litertlm` | ~2.01 GB | GPU-tuned |
| `gemma-4-E2B-it-web.litertlm` | ~2.01 GB | Web/WASM |
| `gemma-4-E2B-it-web.task` | ~2.00 GB | MediaPipe LLM Inference Engine format, "currently in maintenance mode" per the card |
| `gemma-4-E2B-it_Google_Tensor_G5.litertlm` | ~3.21 GB | named for the Tensor G5 |
| `gemma-4-E2B-it_Google_Tensor_G6.litertlm` | ~3.45 GB | named for the Tensor G6 |
| `gemma-4-E2B-it_NPU_Uncompiled.litertlm` | ~2.61 GB | generic NPU, uncompiled |
| `gemma-4-E2B-it_Qualcomm_QCS8275.litertlm` | ~2.72 GB | needs separately downloaded Qualcomm SDK libraries, "under their own terms and are not included in this model file upload" |
| `gemma-4-E2B-it_intel_LNL.litertlm` / `_intel_PTL.litertlm` | ~2.96 / ~2.95 GB | Intel Lunar Lake / Panther Lake |
| `chat_template.jinja`, `notebook.ipynb`, `README.md`, image, `.gitattributes` | small | support files |

**The model's own format is `.litertlm`, not `.tflite`.** This is already a
correction to `plan.md`'s "LiteRT (`.tflite`) model format" wording, which must
be fixed to `.litertlm` once TASK-117 confirms the format is stable across
publishers (not litert-community-specific).

## Runtime named by the card

> "These models are provided in the `.litertlm` format for use with the
> LiteRT-LM framework."

> "LiteRT provides the foundational hardware acceleration via XNNPack for CPU
> and ML Drift for GPU."

Usage shown is the `litert-lm` Python package and CLI
(`pip install -U litert-lm`; `litert-lm run --from-huggingface-repo=... `).
Platforms listed: "Android, iOS, Windows, Linux, macOS, IoT, Web/WASM" — Android
is named as a supported platform, but the card gives no Android library/AAR
details; that is a CLI/Python-first document, not an Android integration guide.
**TASK-117 must find the Android integration path for LiteRT-LM** (an AAR,
JNI/NDK library, or something else) before assuming it slots into
`InferenceEngine` the way `LlamaCppEngine` does.

The card separately flags MediaPipe's LLM Inference Engine (`.task` file) as
"currently in maintenance mode" — not a path to build on.

## Acceleration evidence

- GPU: benchmark tables cover GPU backends on Samsung S26 Ultra, iPhone 17 Pro,
  desktop/Jetson hardware. No Pixel GPU benchmark shown.
- NPU: only documented for "Qualcomm Dragonwing IQ8 (IQ-8275)" in an IoT table,
  and a hackathon note: "use the NPU variant with the matching Qualcomm
  dispatch/runtime libraries, not with the CPU backend. Compatibility with
  other Qualcomm chips or Android runtimes is not established by this
  example." That NPU path needs closed-source Qualcomm libraries downloaded
  separately — not relevant to Tensor G5/G6, but evidence that an NPU-named
  file does not necessarily mean a documented, open acceleration path.
- **Tensor G5/G6: the model card text never mentions them.** The only evidence
  they exist is the two file names. No NNAPI mention anywhere on the page.
  TASK-117 must find where the Tensor G5/G6 acceleration mechanism is actually
  documented (likely the LiteRT-LM or LiteRT GitHub repository, not this model
  card) before `plan.md`'s claim that a "Tensor G5/G6 TPU delegate" exists as a
  selectable, documented thing can be treated as confirmed.
- The card states Gemma 4 on "supported Android devices" is "available through
  Android AI Core as Gemini Nano, which is the recommended path for production
  applications" — i.e. Google's own documentation steers production Android
  use toward AICore, the exact dependency `plan.md` excludes. This sharpens
  (does not yet answer) the open question of whether the Tensor TPU path is
  reachable without AICore.

## Licence

Page header tag: `License: apache-2.0`. No separate licence section in the
card body. Apache-2.0 is GPL-3.0-or-later-compatible for inclusion, so this
does not block the model file itself; the LiteRT-LM runtime's own licence is
still to be checked (TASK-117).

## Model source implication

The user can also convert their own models into this format rather than only
pulling from Hugging Face; the Hub-based discovery decided earlier for the
LiteRT pipeline should also support local import of a self-converted
`.litertlm` file, matching how GGUF already supports both Hub download and
local import.
