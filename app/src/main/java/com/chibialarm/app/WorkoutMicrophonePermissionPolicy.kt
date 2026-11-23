package com.chibialarm.app

/** Chooses a clear next step after Android denies the user's explicit microphone request. */
internal object WorkoutMicrophonePermissionPolicy {
    enum class DenialAction {
        RETRY_PERMISSION,
        OPEN_APP_SETTINGS
    }

    fun denialAction(shouldShowRationale: Boolean): DenialAction =
        if (shouldShowRationale) DenialAction.RETRY_PERMISSION
        else DenialAction.OPEN_APP_SETTINGS
}
