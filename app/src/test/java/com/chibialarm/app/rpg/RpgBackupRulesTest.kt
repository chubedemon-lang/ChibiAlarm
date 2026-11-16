package com.chibialarm.app.rpg

import com.chibialarm.app.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** Guard against accidentally transferring Android Keystore-encrypted Gemini credentials. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RpgBackupRulesTest {
    private fun excludedPaths(resource: Int, group: String? = null): Set<String> {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(resource)
        val excluded = mutableSetOf<String>()
        var currentGroup: String? = null
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "cloud-backup" || parser.name == "device-transfer") {
                            currentGroup = parser.name
                        }
                        if (parser.name == "exclude" && (group == null || currentGroup == group)
                            && parser.getAttributeValue(null, "domain") == "sharedpref") {
                            parser.getAttributeValue(null, "path")?.let { excluded.add(it) }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == currentGroup) currentGroup = null
                    }
                }
            }
        } finally {
            parser.close()
        }
        return excluded
    }

    @Test fun encryptedGeminiKeysAndChatExcludedFromAllAndroidBackupPaths() {
        val required = setOf("gemini_secure.xml", "gemini_vault.xml",
            "coach_history.xml", "settings.xml")
        assertTrue(excludedPaths(R.xml.backup_rules).containsAll(required))
        assertTrue(excludedPaths(R.xml.data_extraction_rules, "cloud-backup")
            .containsAll(required))
        assertTrue(excludedPaths(R.xml.data_extraction_rules, "device-transfer")
            .containsAll(required))
    }
}
