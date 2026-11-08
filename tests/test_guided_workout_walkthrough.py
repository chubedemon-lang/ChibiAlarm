"""Regression checks for the offline guided exercise walkthrough."""
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/chibialarm/app/GuidedWorkoutActivity.kt"


class GuidedWorkoutWalkthroughTest(unittest.TestCase):
    def test_walkthrough_is_paused_and_has_progress(self):
        source = SOURCE.read_text(encoding="utf-8")
        section = source.split("private fun showStepByStepGuide()", 1)[1].split("private fun displayStep()", 1)[0]
        self.assertIn("pause()", section)
        self.assertIn("spoken?.stop()", section)
        self.assertIn("max=steps.size", section)
        self.assertIn("stepProgress.progress=current+1", section)
        self.assertIn("label.contentDescription", section)

    def test_last_step_does_not_wrap_to_first(self):
        source = SOURCE.read_text(encoding="utf-8")
        section = source.split("private fun showStepByStepGuide()", 1)[1].split("private fun displayStep()", 1)[0]
        self.assertIn("if(current<steps.lastIndex)", section)
        self.assertIn("isEnabled=current<steps.lastIndex", section)
        self.assertNotIn("(current+1)%steps.size", section)


if __name__ == "__main__":
    unittest.main()
