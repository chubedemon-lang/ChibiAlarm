package com.chibialarm.app.fitness

import com.chibialarm.app.FitnessNaturalRequest
import com.chibialarm.app.WorkoutRestRequest
import com.chibialarm.app.TrainingEquipment
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
        val allowFloorPositions: Boolean = false,
        // Missing tools are tracked individually. A catalog move with alternative tools
        // remains valid while at least one of its declared options is available.
        val unavailableEquipment: Set<TrainingEquipment> = emptySet(),
        // Appended to preserve the positional constructor order used by older call sites.
        val avoidOverheadReach: Boolean = false,
        val allowOverheadReach: Boolean = false,
        val addOptionalExercise: Boolean = false,
        val availableEquipment: Set<TrainingEquipment> = emptySet()
    ) {
        init {
            require(remainingMinutes == null || remainingMinutes in 5..120)
            require(restSeconds == null || restSeconds in 15..120)
            require(!(avoidWristLoad && allowWristLoad))
            require(!(avoidFloorPositions && allowFloorPositions))
            require(!(avoidOverheadReach && allowOverheadReach))
        }

        /** True only when the request changes the rest countdown and nothing else. */
        val isRestOnly: Boolean
            get() = restSeconds != null &&
                remainingMinutes == null &&
                !lowEnergy &&
                !avoidWristLoad &&
                !avoidFloorPositions &&
                !preferEasier &&
                !avoidExternalLoad &&
                !allowWristLoad &&
                !allowFloorPositions &&
                !avoidOverheadReach &&
                !allowOverheadReach &&
                !addOptionalExercise &&
                availableEquipment.isEmpty() &&
                unavailableEquipment.isEmpty()
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
        val explicitlyNotDifficult = has("khong kho", "khong qua suc")
        // People often describe a set as "khó quá" or "không theo kịp" rather than
        // naming fatigue. Treat these as a request for a preview, never an automatic
        // plan mutation. The existing safety gate above still gives pain priority.
        val explicitDifficulty = has(
            "kho qua", "qua suc", "khong theo kip", "khong theo noi",
            "duoi roi", "duoi qua", "bai nay nang qua", "dong tac nay nang qua"
        )
        val lowEnergy = !(explicitlyNotTired || explicitlyNotDifficult) && (
            fitness.energy == 0 || explicitDifficulty ||
                has("met roi", "hoi met", "met qua", "het suc", "it nang luong")
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
        val avoidOverheadReach = fitness.avoidOverheadReach == true
        val allowOverheadReach = fitness.avoidOverheadReach == false

        val preferEasier = lowEnergy || has(
            "nhe hon", "de hon", "giam do kho", "doi bai nhe",
            "doi sang bai nhe", "thay bai nhe"
        )
        // Equipment is parsed per tool. A dumbbell-only absence must not discard a
        // catalog move that can also be performed with a resistance band.
        val noBothExternalLoads = has(
            "khong dung ta hoac day khang luc",
            "khong co ta hoac day khang luc",
            "khong co ca ta lan day khang luc",
            "khong dung ta va day khang luc",
            "khong co day khang luc hoac ta"
        )
        val noEquipment = has(
            "khong dung cu", "khong co dung cu", "khong can dung cu", "tay khong",
            "khong co do tap", "khong co thiet bi", "khong mang dung cu"
        )
        val noDumbbell = noBothExternalLoads || has(
            "khong dung ta", "khong co ta", "bo ta", "khong mang ta",
            "de ta o nha", "khong co ta don"
        )
        val noResistanceBand = noBothExternalLoads || has(
            "khong dung day khang luc", "khong co day khang luc",
            "bo day khang luc", "khong mang day khang luc", "khong co day tap"
        )
        val noStableChair = has("khong co ghe", "khong mang ghe", "de ghe o nha")
        val noMat = has("khong co tham tap", "khong co tham", "khong mang tham")
        val unavailableEquipment = if (noEquipment) {
            TrainingEquipment.values().toSet()
        } else {
            buildSet {
                if (noDumbbell) add(TrainingEquipment.DUMBBELL)
                if (noResistanceBand) add(TrainingEquipment.RESISTANCE_BAND)
                if (noStableChair) add(TrainingEquipment.STABLE_CHAIR)
                if (noMat) add(TrainingEquipment.MAT)
            }
        }
        val avoidExternalLoad = noEquipment || noBothExternalLoads ||
            (noDumbbell && noResistanceBand)
        // Only positively stated tools are added. A negated mention always wins.
        val availableEquipment = if (noEquipment) emptySet() else buildSet {
            if (!noStableChair && has(
                    "co ghe", "ghe vung chac", "ghe chac chan", "ghe khong banh xe",
                    "co ghe vung chac", "dung ghe vung"
                )
            ) add(TrainingEquipment.STABLE_CHAIR)
            if (!noMat && has("co tham tap", "co tham", "dung tham tap")) {
                add(TrainingEquipment.MAT)
            }
            if (!noDumbbell && has("co ta don", "co ta nhe", "co ta", "dung ta")) {
                add(TrainingEquipment.DUMBBELL)
            }
            if (!noResistanceBand && has(
                    "co day khang luc", "co day tap", "dung day khang luc"
                )
            ) add(TrainingEquipment.RESISTANCE_BAND)
        }
        val explicitlyNoAdd = has(
            "khong them bai", "khong them bai tap", "khong can them bai",
            "khong them dong tac", "khong can them dong tac",
            "dung them bai", "dung them dong tac"
        )
        val addOptionalExercise = !explicitlyNoAdd && has(
            "them mot bai", "them 1 bai", "them bai", "them bai tap",
            "them mot dong tac", "them 1 dong tac", "them dong tac"
        )

        val hasRestRequest = Regex("""\b(nghi|rest)\b""").containsMatchIn(text)
        val explicitRest = if (hasRestRequest) WorkoutRestRequest.seconds(input) else null
        val restSeconds = explicitRest ?: if (lowEnergy) 60 else null

        if (remainingMinutes == null && !lowEnergy && !avoidWristLoad && !avoidFloorPositions &&
            !allowWristLoad && !allowFloorPositions &&
            !avoidOverheadReach && !allowOverheadReach &&
            !preferEasier && restSeconds == null && !avoidExternalLoad &&
            !addOptionalExercise && availableEquipment.isEmpty() &&
            unavailableEquipment.isEmpty()
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
                allowFloorPositions = allowFloorPositions,
                avoidOverheadReach = avoidOverheadReach,
                allowOverheadReach = allowOverheadReach,
                unavailableEquipment = unavailableEquipment,
                addOptionalExercise = addOptionalExercise,
                availableEquipment = availableEquipment
            )
        )
    }
}
