package com.chibialarm.app

/** Parse only index-scoped, unfinished rep targets from an untrusted local checkpoint. */
object WorkoutRepTargets {
    fun decode(raw:String?, stepCount:Int, currentIndex:Int):Map<Int,Int> {
        if (stepCount !in 1..100 || currentIndex !in 0 until stepCount) return emptyMap()
        return raw.orEmpty().split(',').take(100).mapNotNull { entry ->
            val parts=entry.split(':')
            if(parts.size!=2) return@mapNotNull null
            val index=parts[0].toIntOrNull() ?: return@mapNotNull null
            val reps=parts[1].toIntOrNull() ?: return@mapNotNull null
            if(index !in currentIndex until stepCount || reps !in 1..100) null else index to reps
        }.toMap()
    }
}
