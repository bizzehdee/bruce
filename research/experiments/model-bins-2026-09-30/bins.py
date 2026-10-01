#!/usr/bin/env python3
"""Bins the evaluated models per test phone (TASK-106).

Usage:
  bins.py <host-results.jsonl.gz> <phone-dir> [<phone-dir> ...]

Answer quality comes from the host screen (the phones matched it within a few points); speed, the
wait for the first token and memory come from each phone's own runs at context 4096. A phone
directory holds the raw `<file>.<mode>.jsonl` results of one phone (phones/<phone>/).

Recommended (TASK-106's proposed rule): at least 80% of the no-skill and the skill cases with skills
on, no invented answer with skills off, at least 8 tokens a second, and a follow-up turn's first
token within 10 seconds. Usable: at least 60% on both and 4 tokens a second. A model that meets a bar
only with skills off (its no-skill cases at the bar with skills off, nothing invented) is binned
"skills off". The "lenient" column applies the invented-answer condition only to the skills-off bins,
since a model used with skills on is never asked without them.
"""
import glob
import json
import os
import re
import statistics
import sys
from collections import defaultdict

from grade import grade, load, summarise

RECOMMENDED = {"quality": 0.8, "gen_tps": 8.0, "follow_up_s": 10.0, "no_invented": True}
USABLE = {"quality": 0.6, "gen_tps": 4.0, "follow_up_s": None, "no_invented": False}
DEFAULT_THREADS = 4


def host_quality(path):
    by_file = defaultdict(list)
    for record in load([path]):
        by_file[record["file"]].append(grade(record))
    return {file: summarise(rows) for file, rows in by_file.items()}


def records(path):
    with open(path) as lines:
        return [json.loads(line) for line in lines if line.startswith("{")]


def answer_wait(turn):
    """Milliseconds until the answer starts: the first token, or the whole turn when the reply opens with reasoning.
    An empty think block (thinking switched off in the template) is not reasoning."""
    reasoning = re.match(r"\s*<think>(.*?)(</think>|$)", turn.get("reply", ""), re.S)
    return turn["wall_ms"] if reasoning and reasoning.group(1).strip() else turn["ttft_ms"]


def phone_measures(directory):
    """Per model file: device, generation speed, first-token waits and memory estimate at 4096."""
    measures = defaultdict(dict)
    for path in glob.glob(f"{directory}/*.jsonl"):
        name = os.path.basename(path)
        # Superseded runs: before the skills-off prompt fix, off screen, a transient slow spell, or
        # LFM2.5 2.6B with its thinking forced off.
        if any(tag in name for tag in (".old-prompt.", ".offscreen.", ".transient.", ".forced-no-thinking.")):
            continue
        for record in records(path):
            m = measures[record["file"]]
            m["device"] = record["device"]
            if record["type"] == "speed" and record.get("onscreen") and record["threads"] == DEFAULT_THREADS:
                m.setdefault("gen", []).append(record["gen_tps"])
            elif record["type"] == "sweep" and record["context"] == 4096:
                m["loaded"] = record.get("loaded", False)
                m["estimate_gb"] = round(record["estimate_bytes"] / 1e9, 2)
                if record.get("loaded"):
                    m["cold_s"] = record["cold"]["ttft_ms"] / 1000
                    m["follow_up_s"] = answer_wait(record["warm"]) / 1000
            elif record["type"] == "case" and record["skills"] == "on":
                graded = grade(record)
                if graded["category"] == "skill":
                    m.setdefault("phone_skill", []).append(graded["pass"])
    for m in measures.values():
        if "gen" in m:
            m["gen_tps"] = round(statistics.median(m.pop("gen")), 1)
        if "phone_skill" in m:
            passes = m.pop("phone_skill")
            m["phone_skills"] = f"{sum(passes)}/{len(passes)}"
    return measures


def meets(bar, general, skills, invented, measure):
    if general is None or general < bar["quality"] or (invented and bar["no_invented"]):
        return False
    if skills is not None and skills < bar["quality"]:
        return False
    if measure.get("gen_tps", 0) < bar["gen_tps"]:
        return False
    return bar["follow_up_s"] is None or measure.get("follow_up_s", float("inf")) <= bar["follow_up_s"]


def bin_for(quality, measure, lenient=False):
    if not measure.get("loaded", False):
        return "Not recommended (did not load)" if "loaded" in measure else "Not measured"
    invented = quality["invented_off"] > 0
    on = (quality["general_on"], quality["skills_on"], invented and not lenient)
    off = (quality["general_off"], None, False)
    for name, bar in (("Recommended", RECOMMENDED), ("Usable", USABLE)):
        if meets(bar, *on, measure):
            return name
        # Skills off is only offered for a model that says it cannot check rather than inventing.
        if not invented and meets(bar, *off, measure):
            return f"{name}, skills off"
    return "Not recommended"


def main():
    quality = host_quality(sys.argv[1])
    print("| Phone | File | Bin | Bin (lenient) | general on/off | skills | invented off | phone skills | tok/s | follow-up s | cold s | est. GB at 4096 |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|")
    for directory in sys.argv[2:]:
        for file, measure in sorted(phone_measures(directory).items()):
            q = quality.get(file)
            if q is None:
                continue
            print(f"| {measure['device']} | {file} | {bin_for(q, measure)} | {bin_for(q, measure, lenient=True)} | {q['general_on']}/{q['general_off']} | {q['skills_on']} | "
                  f"{q['invented_off']} | {measure.get('phone_skills', '–')} | {measure.get('gen_tps', '–')} | "
                  f"{measure.get('follow_up_s', '–')} | {measure.get('cold_s', '–')} | {measure.get('estimate_gb', '–')} |")


if __name__ == "__main__":
    main()
