package com.chibialarm.app

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class GuidedWorkoutMicrophonePermissionTest {
    @Test fun deniedMicrophonePermissionOffersAppSettingsAndKeepsTypedFallback() {
        val controller = Robolectric.buildActivity(GuidedWorkoutActivity::class.java).setup()
        val activity = controller.get()
        try {
            fun field(name: String) =
                GuidedWorkoutActivity::class.java.getDeclaredField(name).apply { isAccessible = true }

            val targetRequest = field("voiceRequest").getInt(activity)
            val permissionRequest = field("microphonePermissionRequest").getInt(activity)
            val pending = field("pendingRecognitionRequestCode")
            pending.set(activity, targetRequest)

            GuidedWorkoutActivity::class.java.getDeclaredMethod(
                "onRequestPermissionsResult",
                Int::class.javaPrimitiveType,
                Array<String>::class.java,
                IntArray::class.java
            ).apply { isAccessible = true }.invoke(
                activity,
                permissionRequest,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                intArrayOf(PackageManager.PERMISSION_DENIED)
            )

            assertNull("A denied permission must not leave a microphone session pending", pending.get(activity))
            val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
            assertEquals("Mở Cài đặt", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            assertEquals("Tiếp tục bằng chữ", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
            val message = dialog.findViewById<TextView>(android.R.id.message).text.toString()
            assertTrue(message.contains("không nghe nền"))
            assertTrue(message.contains("không lưu tệp ghi âm"))
            assertTrue(message.contains("bàn phím"))

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            val settingsIntent = shadowOf(activity).nextStartedActivity
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent.action)
            assertEquals(Uri.fromParts("package", activity.packageName, null), settingsIntent.data)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
