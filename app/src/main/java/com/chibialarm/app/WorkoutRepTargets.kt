package com.chibialarm.app

/** Parse only index-scoped, unfinished per-set targets (reps or seconds) from an untrusted checkpoint. */
object WorkoutRepTargets {
    fun decode(raw:String?, stepCount:Int, currentIndex:Int):Map<Int,Int> {
        if (stepCount !in 1..100 || currentIndex !in 0 until stepCount) return emptyMap()
        return raw.orEmpty().split(',').take(100).mapNotNull { entry ->
            val parts=entry.split(':')
            if(parts.size!=2) return@mapNotNull null
            val index=parts[0].toIntOrNull() ?: return@mapNotNull null
            val target=parts[1].toIntOrNull() ?: return@mapNotNull null
            if(index !in currentIndex until stepCount || target !in 1..200) null else index to target
        }.toMap()
    }
}
