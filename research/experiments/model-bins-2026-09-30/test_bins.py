"""Tests for bins.py: python3 -m unittest test_bins (from this folder)."""
import unittest

from bins import answer_wait, bin_for


def quality(general_on=0.9, general_off=0.9, skills_on=0.9, invented_off=0):
    return {"general_on": general_on, "general_off": general_off, "skills_on": skills_on, "invented_off": invented_off}


FAST = {"loaded": True, "gen_tps": 12.0, "follow_up_s": 1.0}


class Bins(unittest.TestCase):
    def test_good_and_fast_is_recommended(self):
        self.assertEqual("Recommended", bin_for(quality(), FAST))

    def test_slow_generation_or_follow_up_drops_to_usable(self):
        self.assertEqual("Usable", bin_for(quality(), {**FAST, "gen_tps": 6.0}))
        self.assertEqual("Usable", bin_for(quality(), {**FAST, "follow_up_s": 12.0}))

    def test_an_invented_answer_blocks_recommended_unless_lenient(self):
        self.assertEqual("Usable", bin_for(quality(invented_off=1), FAST))
        self.assertEqual("Recommended", bin_for(quality(invented_off=1), FAST, lenient=True))

    def test_weak_skills_with_good_plain_answers_is_skills_off(self):
        self.assertEqual("Recommended, skills off", bin_for(quality(general_on=0.6, skills_on=0.5), FAST))
        self.assertEqual("Usable", bin_for(quality(general_on=0.6, skills_on=0.6, invented_off=2), FAST))

    def test_a_model_that_invents_is_never_binned_skills_off(self):
        self.assertEqual("Not recommended", bin_for(quality(general_on=0.1, skills_on=0.0, invented_off=1), FAST))

    def test_a_reply_that_opens_with_reasoning_waits_for_the_whole_turn(self):
        self.assertEqual(900, answer_wait({"reply": "Paris.", "ttft_ms": 900, "wall_ms": 2000}))
        self.assertEqual(24000, answer_wait({"reply": " <think>hmm</think>Paris.", "ttft_ms": 900, "wall_ms": 24000}))
        self.assertEqual(900, answer_wait({"reply": "<think>\n\n</think>\n\nParis.", "ttft_ms": 900, "wall_ms": 2000}))

    def test_poor_or_unloaded_is_not_recommended(self):
        self.assertEqual("Not recommended", bin_for(quality(general_on=0.5, general_off=0.5), FAST))
        self.assertEqual("Not recommended", bin_for(quality(), {**FAST, "gen_tps": 3.0}))
        self.assertEqual("Not recommended (did not load)", bin_for(quality(), {"loaded": False}))
        self.assertEqual("Not measured", bin_for(quality(), {}))


if __name__ == "__main__":
    unittest.main()
