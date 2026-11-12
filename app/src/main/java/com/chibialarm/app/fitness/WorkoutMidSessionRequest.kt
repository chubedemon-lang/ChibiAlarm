package com.chibialarm.app.fitness

import com.chibialarm.app.FitnessNaturalRequest
import com.chibialarm.app.WorkoutRestRequest
import java.text.Normalizer
import java.util.Locale

/**
 * Extracts several independent, explicitly spoken mid-session preferences.
 * This is deterministic offline parsing, not free-form medical advice.
 */
object WorkoutMidSessionRequest {
    data class Request(
        val remainingMinutes: Int? = null,
        val lowEnergy: Boolean = false,
        val avoidWristLoad: Boolean = false,
        val avoidFloorPositions: Boolean = false,
        val preferEasier: Boolean = false,
        val restSeconds: Int? = null,
        val avoidExternalLoad: Boolean = false,
        // Explicit permissions are distinct from an absent preference. They let a user
        // relax a session-only constraint without treating an unrelated utterance as consent.
        val allowWristLoad: Boolean = false,
        val allowFloorPositions: Boolean = false
    ) {
        init {
            require(remainingMinutes == null || remainingMinutes in 5..120)
            require(restSeconds == null || restSeconds in 15..120)
            require(!(avoidWristLoad && allowWristLoad))
            require(!(avoidFloorPositions && allowFloorPositions))
        }
    }

    sealed class Result {
        data class Proposed(val request: Request) : Result()
        object SafetyStop : Result()
        object Unsupported : Result()
    }

    private fun normalize(input: String): String {
        val protected = input.lowercase(Locale.ROOT)
            .replace(Regex("đầu\\s+gối"), "kneecap")
            .replace(Regex("bắt\\s+đầu"), "beginning")
            .replace('đ', 'd')
        return Normalizer.normalize(protected, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun parse(utterance: String): Result {
        val input = utterance.trim()
        if (input.isEmpty() || input.length > 1_000) return Result.Unsupported

        // Reuse the fitness parser's conservative symptom gate so "đau đầu gối"
        // stops the session while the body-part phrase "đầu gối" alone does not.
        val fitness = FitnessNaturalRequest.parse(input)
        if (fitness.impact == 2) return Result.SafetyStop

        val text = normalize(input)
        fun has(vararg phrases: String): Boolean = phrases.any(text::contains)

        val hasRemainingTime = Regex(
            """\bcon\s+(?:[0-9]{1,3}|[a-z]+(?:\s+[a-z]+){0,3})\s+(?:phut|minute|min)\b"""
        ).containsMatchIn(text)
        val remainingMinutes = if (hasRemainingTime) fitness.minutes else null

        val explicitlyNotTired = has("khong met", "khong hoi met", "khong thay met")
        val lowEnergy = !explicitlyNotTired && (
            fitness.energy == 0 || has("met roi", "hoi met", "met qua", "het suc", "it nang luong")
        )

        // "Đừng chống tay" is a common spoken form not covered by the profile form parser.
        val avoidWristLoad = fitness.avoidWristLoad == true || has(
            "dung chong tay", "dung ti co tay", "khong duoc chong tay",
            "dung chiu tai qua co tay"
        )
        val allowWristLoad = fitness.avoidWristLoad == false
        val avoidFloorPositions = fitness.avoidFloorPositions == true || has(
            "dung nam san", "dung quy san", "dung xuong san",
            "khong nam san", "khong quy san", "khong xuong san",
            "tranh nam san", "tranh quy san", "tranh xuong san",
            "dung nam", "dung quy", "khong nam", "khong quy", "tranh nam", "tranh quy"
        )
        val allowFloorPositions = fitness.avoidFloorPositions == false

        val preferEasier = lowEnergy || has(
            "nhe hon", "de hon", "giam do kho", "doi bai nhe",
            "doi sang bai nhe", "thay bai nhe"
        )
        // A user may lose access to equipment mid-session. Only explicit negative
        // phrases request bodyweight alternatives; positive equipment mentions do not.
        val avoidExternalLoad = has(
            "khong dung cu", "khong co dung cu", "khong can dung cu", "tay khong",
            "khong dung ta", "khong co ta", "bo ta", "khong mang ta", "de ta o nha",
            "khong co do tap", "khong co thiet bi", "khong mang dung cu",
            "khong dung day khang luc", "khong co day khang luc", "bo day khang luc"
        )

        val hasRestRequest = Regex("""\b(nghi|rest)\b""").containsMatchIn(text)
        val explicitRest = if (hasRestRequest) WorkoutRestRequest.seconds(input) else null
        val restSeconds = explicitRest ?: if (lowEnergy) 60 else null

        if (remainingMinutes == null && !lowEnergy && !avoidWristLoad && !avoidFloorPositions &&
            !allowWristLoad && !allowFloorPositions &&
            !preferEasier && restSeconds == null && !avoidExternalLoad
        ) return Result.Unsupported

        return Result.Proposed(
            Request(
                remainingMinutes = remainingMinutes,
                lowEnergy = lowEnergy,
                avoidWristLoad = avoidWristLoad,
                avoidFloorPositions = avoidFloorPositions,
                preferEasier = preferEasier,
                restSeconds = restSeconds,
                avoidExternalLoad = avoidExternalLoad,
                allowWristLoad = allowWristLoad,
                allowFloorPositions = allowFloorPositions
            )
        )
    }
}
