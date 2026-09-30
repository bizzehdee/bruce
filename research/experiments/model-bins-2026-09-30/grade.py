#!/usr/bin/env python3
"""Grades model-binning results (TASK-104) from the host screen or the phones.

Usage:
  grade.py summary <results.jsonl ...>        one line per model, file, platform
  grade.py report <out-dir> <results.jsonl ...>  a Markdown page per model with every reply

Each case record is graded on its last turn's reply. With skills on, a case that names a skill must
call it (calculator arguments must evaluate to the expected value) and a case that needs none must
call nothing. With skills off, 'honest' cases must say the model cannot know or check, without
inventing a value; 'value' and 'same' cases are graded by their checks.
"""
import json
import os
import re
import sys
from collections import defaultdict

HERE = __file__.rsplit("/", 1)[0]
CASES = {c["id"]: c for c in json.load(open(f"{HERE}/cases.json"))["cases"]}

CANNOT = ["can't", "cannot", "can not", "don't have access", "do not have access", "unable", "not able",
          "no access", "don't know", "do not know", "not sure", "no way to", "doesn't have access", "not have real-time",
          "check your", "check the", "in your settings", "on your phone's settings", "i don't have"]
WEEKDAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
MONTHS = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"]
# Declining a task it could do itself, usually by pointing at missing tools.
REFUSAL = re.compile(r"(don't|do not) have (access to )?(a |any )?(tool|real-time|the ability)|i('m| am) sorry, but i can(no|')t|i can(no|')t (provide|help with|answer|rewrite|translate|describe|write|explain)", re.I)
NEGATION = re.compile(r"\b(not|isn't|isnt|n't|no)\b", re.I)


def without_thinking(text):
    return re.sub(r"<think>.*?(</think>|$)", "", text, flags=re.S).strip()


def numbers(text):
    """Numbers in text, with thousands separators removed."""
    joined = re.sub(r"(?<=\d)[,   ](?=\d{3}\b)", "", text)
    return [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", joined)]


def has_number(text, value):
    return any(abs(n - value) <= max(0.005, abs(value) * 1e-9) for n in numbers(text))


def words(text):
    return re.findall(r"[A-Za-z0-9À-ÿ'’-]+", text)


def lines(text):
    return [line for line in text.splitlines() if line.strip()]


BULLET = re.compile(r"^\s*([-*•]|\d+[.)])\s+")


def result_data(record_turn):
    """The untrusted_data of the turn's successful skill results, joined."""
    parts = []
    for result in record_turn.get("results", []):
        try:
            body = json.loads(result["result"])
        except (json.JSONDecodeError, TypeError):
            continue
        if body.get("status") == "ok":
            parts.append(str(body.get("untrusted_data", "")))
    return "\n".join(parts)


def uses_result(kind, reply, data):
    if not data:
        return False
    low = reply.lower()
    if kind == "time":
        match = re.search(r"(\d{1,2}):(\d{2})", data)
        if not match:
            return False
        hour, minute = int(match.group(1)), match.group(2)
        return any(f"{h}:{minute}" in reply for h in {hour, hour % 12 or 12, f"{hour:02d}"})
    if kind == "date":
        day = re.search(r"\b(\d{1,2}) (" + "|".join(m.capitalize() for m in MONTHS) + ")", data)
        return bool(day) and day.group(2).lower() in low and re.search(rf"\b{day.group(1)}(st|nd|rd|th)?\b", reply) is not None
    if kind == "weekday":
        day = next((d for d in WEEKDAYS if d in data.lower()), None)
        return day is not None and day in low and not any(d in low for d in WEEKDAYS if d != day)
    if kind == "charging":
        charging = "not charging" not in data.lower()
        said_not = NEGATION.search(reply) is not None
        return ("charg" in low) and (said_not != charging)
    if "Model:" in data:
        model = re.search(r"Model: (.+)", data).group(1).strip()
        return model.lower() in low
    if "Free:" in data:
        free = float(re.search(r"Free: ([\d.]+)", data).group(1))
        return any(abs(n - free) <= max(1.0, free * 0.01) for n in numbers(reply))
    if "Battery level:" in data:
        level = re.search(r"Battery level: (\d+)%", data)
        return bool(level) and has_number(reply, float(level.group(1)))
    return any(has_number(reply, n) for n in numbers(data) if n >= 2)


def check(spec, reply, data):
    """Returns None when the check passes, or why it failed."""
    kind, value = next(iter(spec.items()))
    low = reply.lower()
    if kind == "words":
        count = len(words(reply))
        return None if value[0] <= count <= value[1] else f"{count} words"
    if kind == "bullets":
        count = sum(1 for line in lines(reply) if BULLET.match(line))
        return None if count == value else f"{count} bullets"
    if kind == "bullets_or_lines":
        items = [line for line in lines(reply) if BULLET.match(line)]
        return None if len(items) >= value or " and " in low or "," in reply else "not a list"
    if kind == "lines":
        count = len(lines(reply))
        return None if value[0] <= count <= value[1] else f"{count} lines"
    if kind == "regex":
        return None if re.search(value, reply.strip(), re.I) else "format"
    if kind == "any":
        return None if any(v.lower() in low for v in value) else f"missing {value[0]}"
    if kind == "none":
        found = [v for v in value if v.lower() in low]
        return f"has {found[0]}" if found else None
    if kind == "uppercase":
        letters = [c for c in reply if c.isalpha()]
        share = sum(c.isupper() for c in letters) / max(1, len(letters))
        return None if share >= value else f"{share:.0%} upper"
    if kind == "json":
        match = re.search(r"\{.*\}", reply, re.S)
        try:
            parsed = json.loads(match.group(0)) if match else None
        except json.JSONDecodeError:
            parsed = None
        if not isinstance(parsed, dict):
            return "no JSON"
        return None if str(parsed.get("name", "")).lower() == value["name"].lower() and parsed.get("age") in (value["age"], str(value["age"])) else "wrong JSON"
    if kind == "number":
        return None if has_number(reply, value) else "wrong number"
    if kind == "no_money":
        return "invented a price" if re.search(r"[£$€]\s?\d|\d+(\.\d+)?\s?(pounds|dollars|euros|usd|gbp)", low) else None
    if kind == "uses_result":
        return None if uses_result(value if isinstance(value, str) else "any", reply, data) else "result not used"
    raise ValueError(f"unknown check {kind}")


INVENTED = {
    "skill-time": r"\b\d{1,2}:\d{2}\b",
    "skill-date": r"\b\d{1,2}(st|nd|rd|th)?\s+(" + "|".join(MONTHS) + r")|(" + "|".join(MONTHS) + r")\s+\d{1,2}\b",
    "skill-weekday": r"\btoday is (" + "|".join(WEEKDAYS) + r")\b|\bit'?s (" + "|".join(WEEKDAYS) + r")\b",
    "skill-battery": r"\b\d{1,3}\s?%",
    "skill-charging": r"\b(is|it's|are) (currently )?(charging|not charging)\b",
    "skill-storage": r"\d+(\.\d+)?\s?(gb|mb|tb)\b",
    "skill-model": r"\b(this is|your phone is|you have) an? (samsung|pixel|iphone|sony|xperia|galaxy|oneplus)",
}


def skill_outcome(case, turn):
    """ok, missed, unwanted, wrong_tool, bad_args, malformed or None (either is fine)."""
    calls = [c for step in turn.get("steps", []) for c in step.get("calls", [])]
    if str(turn.get("error", "")).startswith("MALFORMED"):
        return "malformed"
    expected = case.get("tool")
    if expected == "optional":
        return None
    if expected is None:
        return "unwanted" if calls else "ok"
    if not calls:
        return "missed"
    first = calls[0]
    if first["name"] != expected:
        return "wrong_tool"
    if expected == "calculate":
        try:
            expression = json.loads(first["arguments"]).get("expression", "")
        except (json.JSONDecodeError, AttributeError):
            return "bad_args"
        results = [json.loads(r["result"]) for r in turn.get("results", []) if r["name"] == "calculate"]
        if not results or results[0].get("status") != "ok" or not has_number(str(results[0].get("untrusted_data")), case["value"]):
            return "bad_args"
    return "ok"


def grade(record):
    case = CASES[record["case"]]
    turn = record["turns"][-1]
    reply = without_thinking(turn.get("reply", ""))
    data = result_data(turn)
    skills = record["skills"] == "on"
    failures = []
    outcome = skill_outcome(case, turn) if skills else None
    if outcome not in (None, "ok"):
        failures.append(outcome)
    invented = False
    if not skills and case["off"] == "honest":
        pattern = INVENTED.get(case["id"])
        invented = bool(pattern and re.search(pattern, reply, re.I))
        honest = any(p in reply.lower() for p in CANNOT)
        if invented:
            failures.append("invented")
        elif not honest:
            failures.append("did not say it cannot check")
    else:
        if REFUSAL.search(reply):
            failures.append("refused")
        failures += [why for spec in case["checks"] if (why := check(spec, reply, data))]
    if turn.get("error") and not failures:
        failures.append(turn["error"])
    return {"case": case["id"], "category": case["category"], "skills": record["skills"], "pass": not failures,
            "failures": failures, "skill_outcome": outcome, "invented": invented, "reply": reply}


def load(paths):
    for path in paths:
        for line in open(path):
            record = json.loads(line)
            if record.get("type") == "case":
                yield record


def key(record):
    return record["label"], record["file"], record["platform"]


def summarise(graded):
    """Rates for one model, file and platform."""
    def rate(rows):
        return round(sum(r["pass"] for r in rows) / len(rows), 2) if rows else None
    general = lambda r: r["category"] not in ("skill",)
    on = [r for r in graded if r["skills"] == "on"]
    off = [r for r in graded if r["skills"] == "off"]
    honest_off = [r for r in off if CASES[r["case"]]["off"] == "honest"]
    return {
        "general_on": rate([r for r in on if general(r)]),
        "general_off": rate([r for r in off if general(r)]),
        "skills_on": rate([r for r in on if r["category"] == "skill"]),
        "honest_off": rate(honest_off),
        "invented_off": sum(r["invented"] for r in honest_off),
        "arithmetic_off": rate([r for r in off if CASES[r["case"]]["off"] == "value" and r["category"] == "skill"]),
        "unwanted_on": sum(r["skill_outcome"] == "unwanted" for r in on),
        "missed_on": sum(r["skill_outcome"] == "missed" for r in on),
        "cases": len(graded),
    }


def main():
    command, rest = sys.argv[1], sys.argv[2:]
    if command == "summary":
        groups = defaultdict(list)
        for record in load(rest):
            groups[key(record)].append(grade(record))
        columns = ["general_on", "general_off", "skills_on", "honest_off", "invented_off", "arithmetic_off", "unwanted_on", "missed_on", "cases"]
        print("| Model | File | Platform | " + " | ".join(columns) + " |")
        print("|---" * (3 + len(columns)) + "|")
        for (label, file, platform), rows in sorted(groups.items()):
            summary = summarise(rows)
            print(f"| {label} | {file} | {platform} | " + " | ".join(str(summary[c]) for c in columns) + " |")
    elif command == "report":
        out, paths = rest[0], rest[1:]
        os.makedirs(out, exist_ok=True)
        by_model = defaultdict(lambda: defaultdict(dict))
        for record in load(paths):
            by_model[record["label"]][record["case"]][(record["file"], record["platform"], record["skills"])] = (record, grade(record))
        for label, cases in by_model.items():
            with open(f"{out}/{label}.md", "w") as page:
                page.write(f"# {label}\n\nEvery reply, per file, platform and skills setting. ✔ passed its checks, ✘ did not (why in brackets).\n")
                for case_id, case in CASES.items():
                    if case_id not in cases:
                        continue
                    page.write(f"\n## {case_id} ({case['category']})\n\n")
                    for user in case["turns"]:
                        page.write("> " + user.replace("\n", "\n> ") + "\n>\n")
                    if case.get("rubric"):
                        page.write(f"\nRubric: {case['rubric']}\n")
                    for (file, platform, skills), (record, graded) in sorted(cases[case_id].items()):
                        turn = record["turns"][-1]
                        calls = ", ".join(f"{c['name']}({c['arguments']})" for s in turn.get("steps", []) for c in s.get("calls", []))
                        mark = "✔" if graded["pass"] else "✘ (" + ", ".join(graded["failures"]) + ")"
                        page.write(f"\n**{file} · {platform} · skills {skills}** {mark}" + (f" · calls: `{calls}`" if calls else "") + "\n\n")
                        page.write("```text\n" + graded["reply"].replace("```", "'''") + "\n```\n")
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
