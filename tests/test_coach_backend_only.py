"""Regression guard: the coach UI must never call Gemini with device-held keys."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
COACH = ROOT / "app/src/main/java/com/chibialarm/app/CoachActivity.kt"


class CoachBackendOnlyTest(unittest.TestCase):
    def test_coach_does_not_access_device_gemini_credentials(self):
        source = COACH.read_text(encoding="utf-8")
        self.assertNotIn("GeminiSecrets", source)
        self.assertNotIn("GeminiService", source)
        self.assertNotIn("generatePrompt(", source)

    def test_offline_fitness_conversation_and_plan_remain_available(self):
        source = COACH.read_text(encoding="utf-8")
        self.assertIn("FitnessCoachConversation.parseReply", source)
        self.assertIn("FitnessCoachFollowUp.nextQuestion", source)
        self.assertIn("FreeCoach.plan(detail,days)", source)
        self.assertIn("showFreePlan(title,draft,days)", source)
        self.assertIn("saveApprovedPlan(", source)


if __name__ == "__main__":
    unittest.main()
