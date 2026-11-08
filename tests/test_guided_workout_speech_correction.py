"""Regression checks for explicit correction of guided-workout speech commands."""
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/chibialarm/app/GuidedWorkoutActivity.kt"


def test_recognized_non_safety_command_can_be_corrected_before_applying():
    source = SOURCE.read_text(encoding="utf-8")
    callback = source[source.index("override fun onActivityResult("):source.index("private fun openSavedVideo()")]
    assert '.setNeutralButton("Sửa câu nhận dạng")' in callback
    assert "setText(phrase.take(160))" in callback
    assert "handleCoachCommand(corrected.text.toString())" in callback
    assert '.setNegativeButton("Hủy",null)' in callback


def test_reported_pain_still_stops_immediately():
    source = SOURCE.read_text(encoding="utf-8")
    callback = source[source.index("override fun onActivityResult("):source.index("private fun openSavedVideo()")]
    assert "if (command==WorkoutVoiceCommands.Command.PAIN)" in callback
    assert "handleCoachCommand(phrase)" in callback
