"""Regression guard for the CoachActivity speech-to-text consent boundary."""
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/chibialarm/app/CoachActivity.kt"


def test_microphone_requires_explicit_disclosure():
    source = SOURCE.read_text(encoding="utf-8")
    assert 'setOnClickListener { explainMicrophoneThenListen() }' in source
    assert '.setPositiveButton("Tiếp tục dùng mic") { _, _ -> listen() }' in source
    assert '.setNegativeButton("Để tôi gõ", null)' in source
    assert "có thể dùng Internet" in source


def test_recognized_speech_is_reviewed_not_auto_submitted():
    source = SOURCE.read_text(encoding="utf-8")
    start = source.index("override fun onActivityResult(")
    end = source.index("override fun onDestroy()", start)
    callback = source[start:end]
    assert "prompt.setText(recognized)" in callback
    assert "prompt.setSelection(prompt.text.length)" in callback
    assert "chat()" not in callback
