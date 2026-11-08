package com.chibialarm.app

/** Only exercises with a meaningful two-phase offline drawing are animated. */
object WorkoutPoseMotion {
    fun hasMotion(pose: WorkoutPoseGuide.Pose): Boolean = when (pose) {
        WorkoutPoseGuide.Pose.SQUAT,
        WorkoutPoseGuide.Pose.PUSH_UP,
        WorkoutPoseGuide.Pose.LUNGE -> true
        WorkoutPoseGuide.Pose.PLANK,
        WorkoutPoseGuide.Pose.REST,
        WorkoutPoseGuide.Pose.STANDING -> false
    }
}
