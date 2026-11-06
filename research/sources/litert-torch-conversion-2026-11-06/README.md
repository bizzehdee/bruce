# LiteRT Torch: converting a checkpoint to `.litertlm`

Retrieved 2026-11-06 from `https://developers.google.com/edge/litert/conversion/pytorch/genai`
("Convert PyTorch GenAI models for on-device inference"). TASK-121 research:
source format and toolchain for the planned `.litertlm` conversion script.
User (2026-11-06) stated no native GGUF-to-LiteRT-LM conversion exists and
that it must start from safetensors, and that Q8/Q6/Q4 quantisation is
recommended for phones; this source confirms the format claim and partially
confirms the quantisation claim (see below).

## Tool and command

Package: `litert-torch` (not `ai-edge-torch`, an older/different name).
"The Hugging Face Export extension is built directly into the `litert-torch`
package." No `pip install` line is shown on this page; it only links the
GitHub repo (`google-ai-edge/litert-torch`).

CLI:

```
litert-torch export_hf --model=google/gemma-3-270m-it --output_dir=/tmp/gemma3-270m-it-litertlm
```

Python API: `from litert_torch.generative.export_hf import export`, then
`export.export(model=..., output_dir=...)`.

The page is internally inconsistent: the main examples use `export_hf`
(underscore), the NPU AOT examples use `export-hf` (hyphen). Confirm the real
one against the package's own `--help` before scripting, not the page.

## Source format — confirms the user's claim

**No GGUF input is mentioned anywhere on the page.** Documented sources are
only a Hugging Face model ID, or a local safetensors directory: "For
exporting a local or custom model, you can also pass the path to the
safetensor checkpoint," with `--model=/path/to/safetensor/dir`. Supported
`model_type`s: Gemma 3, Gemma 3n, Gemma 4, Llama, Mistral, Qwen 2/2.5, Qwen 3,
SmolLM 3. Vision models (Gemma 3/4) need `--task=image_text_to_text
--export_vision_encoder`.

This matches the user's statement: the conversion script's realistic input is
a safetensors checkpoint, not a GGUF file. TASK-121's open question on source
format is resolved by this page; whether GGUF conversion is flatly impossible
or merely undocumented was not tested directly (no attempt was made to feed
GGUF to the tool), but no official path supports it.

## Quantisation — partially confirms the user's claim

The page does not list the full recipe set; it says only "You can provide the
name of a built-in recipe from AI Edge Quantizer ... or specify the path to a
custom JSON recipe," linking `github.com/google-ai-edge/ai-edge-quantizer`
(not fetched in this pass).

Recipes actually named on this page:

- `dynamic_wi8_afp32` — the default. "Quantizes weights to per-channel INT8
  while keeping activations in FP32."
- `weight_only_wi8_afp32` — used in the Google Tensor AOT example, not
  described.
- `''` (empty string) — used in the Qualcomm example, disabling quantisation:
  "Qualcomm NPU only support full float or SRQ models. Weight only Quantized
  model are not supported."

**Int8 is confirmed** (the default recipe, and the one used for the Tensor
AOT example). **Int4 is not named on this page** — an earlier, lower-confidence
web search surfaced a community (not official) recipe name
`dynamic_wi4_afp32`/`weight_only_wi4_afp32` for Gemma 4 E2B, reported to shrink
it from ~10 GB to ~2.58 GB; treat that as unconfirmed until seen in an
official source (the AI Edge Quantizer repo itself). **Int6 was not found in
any source read so far** — the user's "Q8, 6 or 4" is only a third confirmed
at int8; int4 is plausible but not yet from a primary source, and int6 has no
supporting source at all yet. Do not assert an int6 recipe exists in
`plan.md`/`TASK.md` until it is actually found; if the conversion script needs
it, check the AI Edge Quantizer repo directly rather than carrying the
assumption forward.

No phone-specific or per-target quantisation recommendation appears on this
page: it states targets (CPU, GPU, NPU) but gives no guidance on which
quantisation level suits which target or device class.

## NPU / Tensor AOT compilation — same tool, extra flags

Section "NPU AOT Compilation" uses the same `litert-torch export-hf` command
with `--aot_*` flags. Google Tensor example, **Tensor G6 only** (Tensor G5 is
not mentioned anywhere on this page, unlike the separate Tensor SDK page which
lists both):

```
--aot_backend=GOOGLE --aot_soc_model=Tensor_G6
--aot_compilation_config_dict='{"google_tensor_enable_large_model_support": True}'
--split_cache --externalize_embedder --prefill_lengths=128 --cache_length=1280
--quantization_recipe="weight_only_wi8_afp32"
```

(the page's code block has a trailing comma after `--prefill_lengths=128,` —
copy with care, it may be a page typo). Prerequisite: "Follow Google Tensor
SDK page for the development environment setup" (the same gated beta signup
found in TASK-117) and `pip install ai-edge-litert[npu-sdk]`. Other AOT
backends shown, same tool: Qualcomm (`--aot_backend=qualcomm
--aot_soc_model=SM8750 --quantization_recipe=''`), MediaTek
(`--aot_backend=mediatek --aot_soc_model=MT8189`), Intel OpenVINO
(`--aot_backend=intel_openvino --aot_soc_model=PTL`).

## Open items for TASK-121/122

- Confirm `export_hf` vs `export-hf` against the real CLI.
- Find the actual int4/int6 recipe names in `google-ai-edge/ai-edge-quantizer`
  rather than carrying forward an unconfirmed community name.
- Confirm whether Tensor G5 AOT compilation is possible with this tool (only
  G6 appears in this doc) or needs something else.
- The Tensor AOT path needs the same gated "Google Tensor SDK Beta" signup as
  TASK-117's NPU runtime finding — this is a second, independent place that
  gate shows up (model inference and model conversion), reinforcing that the
  Tensor path cannot be a plain from-source, ungated pipeline end to end.

## Note on this fetch

The page's fetched content had an unrelated instructions block appended after
the article text, apparently site/tooling boilerplate aimed at a different
Claude session telling it to create a "claude.ai doc." It was not part of the
article and was ignored; it is recorded here only so a future reader is not
confused if they see similar boilerplate refetching this URL.
