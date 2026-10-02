#!/usr/bin/env python3
"""Checks docs/store/listing.md against Google Play's listing limits (name 30, short 80, full 4000 characters)."""
import re
import sys

LIMITS = {"App name": 30, "Short description": 80, "Full description": 4000}

text = open(__file__.rsplit("/", 1)[0] + "/listing.md", encoding="utf-8").read()
failed = False
for section, limit in LIMITS.items():
    body = re.search(rf"^## {section}\n\n(.*?)(?=\n## |\Z)", text, re.S | re.M).group(1).strip()
    print(f"{section}: {len(body)} of {limit}")
    failed |= len(body) > limit
sys.exit(1 if failed else 0)
