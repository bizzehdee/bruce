#!/usr/bin/env python3
"""Converts a Hugging Face / safetensors checkpoint to a `.litertlm` file (TASK-122).

Wraps `litert-torch export_hf`. Dev tooling only — never shipped in the app, never run on
a phone. See README.md for setup, variant meanings and the gaps this script does not cover.
"""
import argparse
import subprocess
import sys

# Confirmed real names, not guessed (TASK-121, research/sources/litert-torch-conversion-2026-11-06/,
# research/sources/litert-quantizer-2026-11-06/): dynamic_wi8_afp32 is litert-torch's own documented
# default. int6 is deliberately absent (owner decision, 2026-11-06): no source ever confirmed an
# int6 recipe exists.
QUANTISATION_RECIPES = {
    "int8": "dynamic_wi8_afp32",
    "int4": "dynamic_wi4_afp32",
    "int4-weight-only": "weight_only_wi4_afp32",
}

TENSOR_SOC_MODELS = {
    "tensor-g5": "Tensor_G5",
    "tensor-g6": "Tensor_G6",
}


def build_command(args: argparse.Namespace) -> list[str]:
    command = [
        "litert-torch",
        "export_hf",
        args.model,
        args.output_dir,
        "--quantization_recipe",
        QUANTISATION_RECIPES[args.quantization],
        "--bundle_litert_lm=True",
        "--cache_length",
        str(args.cache_length),
        "--prefill_lengths",
        str(args.prefill_length),
    ]
    if args.variant == "gpu":
        # Confirmed via `litert-torch export_hf --help` (TASK-122): these are the flags that
        # distinguish a GPU-tuned export; Google's own "-gpu"-suffixed files were never reverse
        # engineered to confirm this is exactly what they used, so treat this as the documented,
        # not merely assumed, GPU path.
        command += ["--enable_gpu_dynamic_prefill=True", "--enable_gpu_dynamic_cache=True"]
    elif args.variant in TENSOR_SOC_MODELS:
        # The Tensor AOT path (TASK-117, TASK-119, TASK-121): needs the gated Google Tensor SDK
        # Beta environment (Bazel, specific NDK/SDK versions) that this script does not set up —
        # only the flags are wired through. Tensor G5 reachability is unconfirmed (TASK-121):
        # neither litert-torch's own docs nor --help output names which SoC models actually work.
        #
        # --split_cache, --externalize_embedder, the weight_only_wi8_afp32 recipe, and
        # google_tensor_enable_large_model_support are not optional tuning here: without them,
        # the gated compiler_worker binary segfaults (SIGSEGV, no diagnostic) on every model
        # tried, including gemma-3-270m-it under the 2GB large-model threshold. Confirmed fixed
        # by matching https://developers.google.com/edge/tensor-sdk/compilation-flags's own
        # worked example for this exact model (TASK-119, 2026-11-06); not isolated flag-by-flag,
        # so treat all four together as the minimum known-working set, not independently optional.
        command[command.index("--quantization_recipe") + 1] = "weight_only_wi8_afp32"
        command += [
            "--split_cache",
            "--externalize_embedder",
            "--aot_backend=GOOGLE",
            f"--aot_soc_model={TENSOR_SOC_MODELS[args.variant]}",
            '--aot_compilation_config_dict={"google_tensor_enable_large_model_support": True}',
        ]
    if args.extra_recipe:
        command[command.index("--quantization_recipe") + 1] += f",{args.extra_recipe}"
    return command


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", help="Hugging Face model ID, or a local safetensors directory.")
    parser.add_argument("output_dir", help="Directory to write the .litertlm file to.")
    parser.add_argument(
        "--variant",
        choices=["standard", "gpu", "tensor-g5", "tensor-g6"],
        default="standard",
        help="standard: CPU default. gpu: adds the GPU dynamic-shape flags. tensor-g5/tensor-g6: "
        "AOT-compiles for that Tensor chip (needs the Tensor SDK Beta environment; unverified for "
        "tensor-g5, TASK-121).",
    )
    parser.add_argument(
        "--quantization",
        choices=sorted(QUANTISATION_RECIPES),
        default="int8",
        help="int8 is the project's main target (owner decision, 2026-11-06); int4 stays in scope "
        "but its hardware support is narrower (litert_quantizer's own README: dynamic Int4 is "
        "'not supported in all hardware, e.g. some GPU and NPU').",
    )
    parser.add_argument("--extra-recipe", default=None, help="An extra quantisation recipe to append, comma-separated with the main one (e.g. a vision-encoder-specific recipe).")
    parser.add_argument("--cache-length", type=int, default=4096, help="KV cache length (default matches litert-community's own Tensor G6 Gemma 3 1B builds).")
    parser.add_argument("--prefill-length", type=int, default=128, help="Prefill input length.")
    parser.add_argument("--dry-run", action="store_true", help="Print the litert-torch command without running it.")
    args = parser.parse_args()

    command = build_command(args)
    print("Running:", " ".join(command), file=sys.stderr)
    if args.dry_run:
        return 0
    return subprocess.run(command, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
