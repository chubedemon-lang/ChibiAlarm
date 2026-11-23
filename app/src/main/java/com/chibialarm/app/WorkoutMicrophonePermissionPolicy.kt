package com.chibialarm.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Chooses a clear next step after Android denies the user's explicit microphone request. */
internal object WorkoutMicrophonePermissionPolicy {
    enum class DenialAction {
        RETRY_PERMISSION,
        OPEN_APP_SETTINGS
    }

    fun denialAction(shouldShowRationale: Boolean): DenialAction =
        if (shouldShowRationale) DenialAction.RETRY_PERMISSION
        else DenialAction.OPEN_APP_SETTINGS

    /** Build the exact app-details destination without relying on a resolvable Settings activity. */
    fun appSettingsIntent(packageName: String): Intent {
        require(packageName.isNotBlank())
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    }
}
