package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutMicrophonePermissionPolicyTest {
    @Test fun rationaleMeansTheUserCanBeShownAnExplanationAndAskedAgain() {
        assertEquals(
            WorkoutMicrophonePermissionPolicy.DenialAction.RETRY_PERMISSION,
            WorkoutMicrophonePermissionPolicy.denialAction(shouldShowRationale = true)
        )
    }

    @Test fun noRationaleAfterDenialRoutesTheUserToAppSettingsInsteadOfLoopingPermissionRequests() {
        assertEquals(
            WorkoutMicrophonePermissionPolicy.DenialAction.OPEN_APP_SETTINGS,
            WorkoutMicrophonePermissionPolicy.denialAction(shouldShowRationale = false)
        )
    }
}
