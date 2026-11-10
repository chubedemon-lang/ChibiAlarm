"""Regression guard: a reported injury must block workout creation and launch."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/chibialarm/app/FitnessActivity.kt"


def test_professional_review_gate_precedes_catalog_lookup():
    source = SOURCE.read_text(encoding="utf-8")
    start = source.index("val recommendation=FitnessPlanAdvisor.recommend(")
    gate = source.index("if (recommendation.requiresProfessionalReview)", start)
    catalog = source.index("val p=TrainingCatalog.byId(recommendation.programId)", start)
    gate_block = source[gate:catalog]
    assert gate < catalog
    assert "return@setOnClickListener" in gate_block
    assert 'setPositiveButton("Đã hiểu", null)' in gate_block
    assert "startProgram(" not in gate_block


def test_injury_path_has_no_override_confirmation_to_start():
    source = SOURCE.read_text(encoding="utf-8")
    start = source.index("if (recommendation.requiresProfessionalReview)")
    catalog = source.index("val p=TrainingCatalog.byId(recommendation.programId)", start)
    gate_block = source[start:catalog]
    assert "Tạm dừng kế hoạch tập" in gate_block
    assert "không khởi chạy bài tập" in gate_block
