"""Tests for grade.py: python3 -m unittest test_grade (from this folder)."""
import json
import unittest

import grade


def ok(tool, data):
    return {"name": tool, "result": json.dumps({"status": "ok", "tool": tool, "untrusted_data": data}), "ran": True}


def record(case, reply, skills="on", calls=(), results=(), error=None):
    turn = {"reply": reply, "steps": [{"calls": [{"name": n, "arguments": a} for n, a in calls]}], "results": list(results)}
    if error:
        turn["error"] = error
    return {"case": case, "skills": skills, "turns": [turn], "label": "m", "file": "f", "platform": "host", "type": "case"}


class Checks(unittest.TestCase):
    def test_numbers_ignore_thousands_separators(self):
        self.assertTrue(grade.has_number("It is 7,006,652.", 7006652))
        self.assertTrue(grade.has_number("It is 7 006 652.", 7006652))
        self.assertTrue(grade.has_number("Each pays 46.90", 46.9))
        self.assertFalse(grade.has_number("It is 5,707,664.", 7006652))

    def test_word_bullet_and_format_checks(self):
        self.assertIsNone(grade.check({"words": [3, 3]}, "Vast, deep, blue.", ""))
        self.assertEqual("1 words", grade.check({"words": [3, 3]}, "Sea", ""))
        self.assertIsNone(grade.check({"bullets": 3}, "- Apples\n- Bananas\n* Pears", ""))
        self.assertIsNone(grade.check({"bullets": 3}, "1. Apples\n2. Bananas\n3. Pears", ""))
        self.assertIsNone(grade.check({"regex": r"^\W*yes\W*$"}, "Yes.", ""))
        self.assertEqual("format", grade.check({"regex": r"^\W*yes\W*$"}, "Yes, it is.", ""))
        self.assertIsNone(grade.check({"uppercase": 0.9}, "CATS ARE GREAT.", ""))

    def test_json_check_finds_the_object_in_a_code_block(self):
        spec = {"json": {"name": "Sam", "age": 30}}
        self.assertIsNone(grade.check(spec, '```json\n{"name": "Sam", "age": 30}\n```', ""))
        self.assertEqual("wrong JSON", grade.check(spec, '{"name": "Sam", "age": 31}', ""))
        self.assertEqual("no JSON", grade.check(spec, "Sam is 30.", ""))

    def test_price_check_catches_invented_money(self):
        self.assertEqual("invented a price", grade.check({"no_money": True}, "They cost $40.", ""))
        self.assertIsNone(grade.check({"no_money": True}, "The note does not say.", ""))

    def test_results_must_be_used(self):
        date = "Wednesday 30 September 2026, 14:37:12, time zone Europe/London (UTC+01:00)"
        self.assertTrue(grade.uses_result("time", "It's 2:37 pm.", date))
        self.assertTrue(grade.uses_result("time", "It is 14:37.", date))
        self.assertFalse(grade.uses_result("time", "It is 15:37.", date))
        self.assertTrue(grade.uses_result("date", "Today is 30 September 2026.", date))
        self.assertTrue(grade.uses_result("date", "It's September 30th.", date))
        self.assertTrue(grade.uses_result("weekday", "It's Wednesday.", date))
        self.assertFalse(grade.uses_result("weekday", "Wednesday or Thursday.", date))
        self.assertTrue(grade.uses_result("charging", "No, it's not charging.", "Battery level: 76%\nNot charging"))
        self.assertFalse(grade.uses_result("charging", "Yes, it is charging.", "Battery level: 76%\nNot charging"))
        self.assertTrue(grade.uses_result("any", "You have 76% left.", "Battery level: 76%\nNot charging"))
        self.assertTrue(grade.uses_result("any", "About 177 GB free.", "Total: 238.4 GB\nUsed: 61.7 GB\nFree: 176.7 GB"))
        self.assertTrue(grade.uses_result("any", "It's a Sony XQ-AT51.", "Manufacturer: Sony\nModel: XQ-AT51"))
        self.assertFalse(grade.uses_result("any", "You have 76% left.", ""))


class Grading(unittest.TestCase):
    def test_skill_cases_need_the_right_call_and_its_result(self):
        result = ok("calculate", "7006652")
        good = grade.grade(record("skill-multiply", "It's 7,006,652.", calls=[("calculate", '{"expression":"1234*5678"}')], results=[result]))
        self.assertTrue(good["pass"])
        self.assertEqual(["missed", "wrong number"], grade.grade(record("skill-multiply", "It's 5,707,664."))["failures"])
        wrong = grade.grade(record("skill-multiply", "It's 7006652.", calls=[("get_datetime", "{}")]))
        self.assertEqual("wrong_tool", wrong["skill_outcome"])

    def test_calculator_arguments_must_give_the_expected_value(self):
        result = ok("calculate", "7006651")
        graded = grade.grade(record("skill-multiply", "7006652", calls=[("calculate", '{"expression":"1234*5678-1"}')], results=[result]))
        self.assertEqual("bad_args", graded["skill_outcome"])

    def test_a_call_where_none_is_needed_is_unwanted(self):
        graded = grade.grade(record("fact-canberra", "Canberra.", calls=[("get_datetime", "{}")]))
        self.assertEqual(["unwanted"], graded["failures"])

    def test_skills_off_must_say_it_cannot_check_and_not_invent(self):
        self.assertTrue(grade.grade(record("skill-time", "I can't check the time on your phone.", skills="off"))["pass"])
        invented = grade.grade(record("skill-time", "It's 10:15 am.", skills="off"))
        self.assertTrue(invented["invented"])
        self.assertEqual(["did not say it cannot check"], grade.grade(record("skill-battery", "Plenty!", skills="off"))["failures"])

    def test_invented_phone_facts_are_caught(self):
        for case, reply in [("skill-date", "Today is October 26th, 2023."), ("skill-charging", "Yep, it’s fully charged!"),
                            ("skill-model", "It’s a Samsung Galaxy S23!"), ("skill-weekday", "It’s Tuesday, folks!")]:
            self.assertTrue(grade.grade(record(case, reply, skills="off"))["invented"], reply)
        self.assertTrue(grade.grade(record("skill-charging", "I can’t tell whether it is charging.", skills="off"))["pass"])

    def test_skills_off_arithmetic_is_graded_on_the_value(self):
        self.assertTrue(grade.grade(record("skill-percent", "17.5% of 240 is 42.", skills="off"))["pass"])
        self.assertFalse(grade.grade(record("skill-percent", "It is 40.", skills="off"))["pass"])

    def test_declining_a_task_it_can_do_fails(self):
        graded = grade.grade(record("none-joke", "I'm sorry, but I can't provide a joke. I don't have access to a joke tool."))
        self.assertIn("refused", graded["failures"])
        self.assertTrue(grade.grade(record("ground-price", "The note does not say what they cost."))["pass"])

    def test_malformed_calls_and_errors_fail(self):
        graded = grade.grade(record("skill-time", "", error="MALFORMED: bad json"))
        self.assertEqual("malformed", graded["skill_outcome"])
        self.assertFalse(grade.grade(record("fact-canberra", "Canberra", error="TIMED_OUT"))["pass"])

    def test_thinking_is_ignored(self):
        self.assertTrue(grade.grade(record("fact-jupiter", "<think>Saturn? No.</think>Jupiter.", skills="off"))["pass"])

    def test_summary_rates(self):
        rows = [grade.grade(record("fact-jupiter", "Jupiter")), grade.grade(record("fact-canberra", "Sydney")),
                grade.grade(record("skill-time", "It's 9:00.", skills="off"))]
        summary = grade.summarise(rows)
        self.assertEqual(0.5, summary["general_on"])
        self.assertEqual(1, summary["invented_off"])
        self.assertEqual(0.0, summary["honest_off"])


if __name__ == "__main__":
    unittest.main()
