package com.chibialarm.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseVideoRelevanceTest {
    @Test fun bodyweightSquatTitleMatchesCuratedSquatAlias() {
        assertTrue(
            ExerciseVideoRelevance.matches(
                "Squat không tạ",
                "Bodyweight Squat Tutorial for Beginners"
            )
        )
    }

    @Test fun wallPushUpDoesNotAcceptFloorPushUpAsTheSameExercise() {
        assertFalse(
            ExerciseVideoRelevance.matches(
                "Chống đẩy tường",
                "Floor Push-Up Tutorial"
            )
        )
        assertTrue(
            ExerciseVideoRelevance.matches(
                "Chống đẩy tường",
                "Wall Push-Up for Beginners"
            )
        )
    }

    @Test fun gluteBridgeTitleMatchesVietnameseExerciseName() {
        assertTrue(
            ExerciseVideoRelevance.matches(
                "Nâng hông trên sàn",
                "Glute Bridge Exercise - Beginner Tutorial"
            )
        )
    }

    @Test fun accentInsensitiveFallbackSupportsUnmappedCatalogNames() {
        assertTrue(
            ExerciseVideoRelevance.matches(
                "Giãn cơ vai",
                "Gian co vai tai nha"
            )
        )
    }

    @Test fun genericFitnessTitlesDoNotPassTheRelevanceGate() {
        assertFalse(
            ExerciseVideoRelevance.matches(
                "Chống đẩy tường",
                "Best Workout for Beginners at Home"
            )
        )
        assertFalse(
            ExerciseVideoRelevance.matches(
                "Bài tập tại nhà",
                "Best Workout for Beginners"
            )
        )
    }

    @Test fun blankMoveOrTitleNeverMatches() {
        assertFalse(ExerciseVideoRelevance.matches("", "Bodyweight Squat"))
        assertFalse(ExerciseVideoRelevance.matches("Squat không tạ", " "))
    }
}
