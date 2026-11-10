"""Regression checks for the offline guided exercise walkthrough."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/chibialarm/app/GuidedWorkoutActivity.kt"
STATE = ROOT / "app/src/main/java/com/chibialarm/app/WorkoutStepGuide.kt"


class GuidedWorkoutWalkthroughTest(unittest.TestCase):
    def test_walkthrough_is_paused_and_has_progress(self):
        source = SOURCE.read_text(encoding="utf-8")
        section = source.split("private fun showStepByStepGuide()", 1)[1].split("private fun renderDose()", 1)[0]
        self.assertIn("pause()", section)
        self.assertIn("spoken?.stop()", section)
        self.assertIn("WorkoutStepGuide.Walkthrough.start(move.steps)", section)
        self.assertIn("max = walkthrough.steps.size", section)
        self.assertIn("stepProgress.progress = walkthrough.position", section)
        self.assertIn("label.contentDescription", section)
        self.assertIn("previousButton", section)

    def test_previous_and_next_navigation_are_wired(self):
        source = SOURCE.read_text(encoding="utf-8")
        section = source.split("private fun showStepByStepGuide()", 1)[1].split("private fun renderDose()", 1)[0]
        self.assertIn("walkthrough = walkthrough.previous()", section)
        self.assertIn("walkthrough = walkthrough.next()", section)
        self.assertIn("previousButton.isEnabled = walkthrough.canGoPrevious", section)
        self.assertIn("isEnabled = walkthrough.canGoNext", section)
        self.assertNotIn("(current+1)%steps.size", section)

    def test_navigation_state_is_bounded_at_both_ends(self):
        state = STATE.read_text(encoding="utf-8")
        self.assertIn("val canGoPrevious: Boolean get() = index > 0", state)
        self.assertIn("val canGoNext: Boolean get() = index < steps.lastIndex", state)
        self.assertIn("if (canGoPrevious) Walkthrough(steps, index - 1) else this", state)
        self.assertIn("if (canGoNext) Walkthrough(steps, index + 1) else this", state)


if __name__ == "__main__":
    unittest.main()
