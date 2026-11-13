"""Regression guards for equipment-aware Fitness previews and guided sessions."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FITNESS = (ROOT / "app/src/main/java/com/chibialarm/app/FitnessActivity.kt").read_text(encoding="utf-8")
GUIDED = (ROOT / "app/src/main/java/com/chibialarm/app/GuidedWorkoutActivity.kt").read_text(encoding="utf-8")
AVAILABILITY = (ROOT / "app/src/main/java/com/chibialarm/app/TrainingEquipmentAvailability.kt").read_text(encoding="utf-8")


def test_equipment_change_refreshes_library_cards():
    assert "equipmentSpinner.onItemSelectedListener" in FITNESS
    assert "drawPrograms(it,categories[filter.selectedItemPosition])" in FITNESS


def test_recommendation_and_library_cards_filter_base_moves_before_preview():
    assert "WorkoutEquipmentFilter.apply(constrained.program, equipment)" in FITNESS
    assert "WorkoutEquipmentFilter.apply(" in FITNESS
    assert "equipped.notice" in FITNESS
    assert "isEnabled=equipped.canStart" in FITNESS


def test_guided_activity_revalidates_equipment_before_starting():
    assert "WorkoutEquipmentFilter.apply(constraintResult.program, equipmentSelection)" in GUIDED
    assert "if (!equipmentResult.canStart)" in GUIDED
    assert 'putExtra("extension_equipment", equipmentSelection)' in FITNESS


def test_missing_equipment_is_not_assumed_for_guest_profiles():
    assert "TrainingEquipmentAvailability.fromPreference(equipmentSelection)" in (
        ROOT / "app/src/main/java/com/chibialarm/app/WorkoutEquipmentFilter.kt"
    ).read_text(encoding="utf-8")
