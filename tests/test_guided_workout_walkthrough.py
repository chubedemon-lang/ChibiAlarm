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

    def test_guided_session_preserves_full_supported_duration_range(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn(
            'WorkoutDurationChoices.guidedMinutes(intent.getIntExtra("minutes",15))',
            source,
        )
        self.assertNotIn('.coerceIn(5,45)', source)

    def test_navigation_state_is_bounded_at_both_ends(self):
        state = STATE.read_text(encoding="utf-8")
        self.assertIn("val canGoPrevious: Boolean get() = index > 0", state)
        self.assertIn("val canGoNext: Boolean get() = index < steps.lastIndex", state)
        self.assertIn("if (canGoPrevious) Walkthrough(steps, index - 1) else this", state)
        self.assertIn("if (canGoNext) Walkthrough(steps, index + 1) else this", state)

    def test_rotation_restores_self_reported_set_values_and_effort(self):
        source = SOURCE.read_text(encoding="utf-8")
        save_section = source.split("override fun onSaveInstanceState(outState:Bundle)", 1)[1].split(
            "private fun requestVoiceCommand()", 1
        )[0]
        self.assertIn('outState.putString("workout_set_values"', save_section)
        self.assertIn('outState.putString("workout_effort_log"', save_section)

        restore_start = source.index(
            'if(savedInstanceState!=null &&\\n            savedInstanceState.getString("workout_program")==program.id'
        )
        restore_end = source.index("} else if (savedInstanceState != null", restore_start)
        restore_section = source[restore_start:restore_end]
        self.assertIn("WorkoutSetRepetitionLog.decode(", restore_section)
        self.assertIn('savedInstanceState.getString("workout_set_values")', restore_section)
        self.assertIn("WorkoutEffortLog.decode(", restore_section)
        self.assertIn('savedInstanceState.getString("workout_effort_log")', restore_section)
        self.assertIn("completedSetsByMove", restore_section)


if __name__ == "__main__":
    unittest.main()
