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
    assert "WorkoutEquipmentFilter.apply(" in FITNESS
    assert "constrained.program, equipment, additionalEquipment" in FITNESS
    assert "WorkoutEquipmentFilter.apply(" in FITNESS
    assert "equipped.notice" in FITNESS
    assert "isEnabled=equipped.canStart" in FITNESS


def test_guided_activity_revalidates_equipment_before_starting():
    assert "WorkoutEquipmentFilter.apply(" in GUIDED
    assert "constraintResult.program, equipmentSelection, additionalEquipment" in GUIDED
    assert "if (!equipmentResult.canStart)" in GUIDED
    assert 'putExtra("extension_equipment", equipmentSelection)' in FITNESS
    assert 'putStringArrayListExtra("extension_additional_equipment"' in FITNESS
    assert 'getStringArrayListExtra("extension_additional_equipment")' in GUIDED


def test_missing_equipment_is_not_assumed_for_guest_profiles():
    filter_source = (ROOT / "app/src/main/java/com/chibialarm/app/WorkoutEquipmentFilter.kt").read_text(encoding="utf-8")
    assert "TrainingEquipmentAvailability.fromPreference(equipmentSelection) + additionalEquipment" in filter_source



def test_stable_chair_choice_is_supported_by_parser_filter_and_guided_session():
    assert "Có ghế vững chắc (không bánh xe)" in FITNESS
    assert "3 -> setOf(TrainingEquipment.STABLE_CHAIR)" in AVAILABILITY
    assert 'coerceIn(0, 3)' in GUIDED

def test_multi_equipment_choices_reach_the_coach_and_guided_session():
    assert "additionalEquipmentCheckBoxes" in FITNESS
    assert "currentAdditionalEquipment()" in FITNESS
    assert "additionalEquipment = currentAdditionalEquipment()" in FITNESS
    assert "parsed.additionalEquipment?.let { setAdditionalEquipmentSelection(it) }" in FITNESS
    assert "additionalEquipment = additionalEquipment" in GUIDED
