#!/usr/bin/env python3
"""Summarises run.py results: accuracy and cost per model and variant.

Usage: analyse.py <results.jsonl> [...]
"""
import collections
import json
import sys

OUTCOMES = ["ok", "missed", "unwanted", "wrong_tool", "bad_args", "malformed"]


def main():
    groups = collections.defaultdict(list)
    for path in sys.argv[1:]:
        for line in open(path):
            row = json.loads(line)
            groups[(row["model"], row["variant"])].append(row)

    print("| Model | Variant | Correct | " + " | ".join(OUTCOMES[1:]) + " | Prompt tokens | Prompt ms | Generated tokens | Wall s |")
    print("|---|---|---|" + "---|" * (len(OUTCOMES) - 1) + "---|---|---|---|")
    for (model, variant), rows in sorted(groups.items()):
        counts = collections.Counter(r["outcome"] for r in rows)
        n = len(rows)

        def mean(field):
            values = [r[field] for r in rows if r.get(field) is not None]
            return sum(values) / len(values) if values else float("nan")

        print(
            f"| {model} | {variant} | {counts['ok']}/{n} | "
            + " | ".join(str(counts[o]) for o in OUTCOMES[1:])
            + f" | {mean('prompt_n'):.0f} | {mean('prompt_ms'):.0f} | {mean('predicted_n'):.0f} | {mean('wall_s'):.1f} |"
        )


if __name__ == "__main__":
    main()
