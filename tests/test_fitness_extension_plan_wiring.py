"""Regression checks for goal-aware duration extensions across preview and guided workout."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FITNESS = (ROOT / "app/src/main/java/com/chibialarm/app/FitnessActivity.kt").read_text(encoding="utf-8")
GUIDED = (ROOT / "app/src/main/java/com/chibialarm/app/GuidedWorkoutActivity.kt").read_text(encoding="utf-8")


def test_fitness_preview_uses_current_goal_equipment_energy_and_constraints():
    assert "currentExtensionPreferences(constraintProfile)" in FITNESS
    assert "recommendationGoalSpinner?.selectedItemPosition" in FITNESS
    assert "recommendationEnergySpinner?.selectedItemPosition" in FITNESS
    assert "recommendationEquipmentSpinner?.selectedItemPosition" in FITNESS
    assert "planned.extensionMoves" in FITNESS


def test_guided_workout_rebuilds_the_same_extension_preferences():
    for key in ("extension_goal", "extension_energy", "extension_equipment", "extension_impact"):
        assert f'"{key}"' in FITNESS
        assert f'"{key}"' in GUIDED
    assert "WorkoutDurationPlanner.build(program,level,minutes,extensionPreferences)" in GUIDED
    assert "constraints = workoutConstraints" in GUIDED
