package com.chibialarm.app

/**
 * In-memory, offline multi-turn coaching state. A new conversation starts on Activity creation;
 * no health-related free text is persisted or sent to a server.
 */
object FitnessCoachConversation {
    fun merge(previous: FitnessNaturalRequest.Result?, next: FitnessNaturalRequest.Result): FitnessNaturalRequest.Result {
        if (previous == null) return next
        return FitnessNaturalRequest.Result(
            goal = next.goal ?: previous.goal,
            energy = next.energy ?: previous.energy,
            minutes = next.minutes ?: previous.minutes,
            equipment = next.equipment ?: previous.equipment,
            // Never silently downgrade an earlier injury warning in the same conversation.
            impact = if (previous.impact == 2 || next.impact == 2) 2 else next.impact ?: previous.impact,
            reply = next.reply
        )
    }
}
