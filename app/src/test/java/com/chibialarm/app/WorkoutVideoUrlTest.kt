package com.chibialarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutVideoUrlTest {
    @Test fun acceptsCanonicalHttpsWatchLinks() {
        assertEquals(
            "abcdefghijk",
            WorkoutVideoUrl.extractVideoId("https://www.youtube.com/watch?v=abcdefghijk")
        )
        assertEquals(
            "abcdefghijk",
            WorkoutVideoUrl.extractVideoId("https://youtu.be/abcdefghijk?si=share-token")
        )
    }

    @Test fun acceptsShortEmbedAndLiveRoutesOnly() {
        assertEquals(
            "abcdefghijk",
            WorkoutVideoUrl.extractVideoId("https://m.youtube.com/shorts/abcdefghijk")
        )
        assertEquals(
            "abcdefghijk",
            WorkoutVideoUrl.extractVideoId("https://youtube.com/embed/abcdefghijk")
        )
        assertEquals(
            "abcdefghijk",
            WorkoutVideoUrl.extractVideoId("https://www.youtube.com/live/abcdefghijk")
        )
    }

    @Test fun rejectsNonHttpsAndUntrustedHosts() {
        assertNull(WorkoutVideoUrl.extractVideoId("http://youtu.be/abcdefghijk"))
        assertNull(WorkoutVideoUrl.extractVideoId("https://youtube.com.evil.example/watch?v=abcdefghijk"))
        assertNull(WorkoutVideoUrl.extractVideoId("https://notyoutube.com/watch?v=abcdefghijk"))
        assertNull(WorkoutVideoUrl.extractVideoId("https://evil.example/watch?v=abcdefghijk"))
    }

    @Test fun rejectsUserInfoAndUnexpectedPorts() {
        assertNull(WorkoutVideoUrl.extractVideoId("https://attacker@youtube.com/watch?v=abcdefghijk"))
        assertNull(WorkoutVideoUrl.extractVideoId("https://youtube.com:8443/watch?v=abcdefghijk"))
    }

    @Test fun rejectsAmbiguousOrUnexpectedRoutes() {
        assertNull(
            WorkoutVideoUrl.extractVideoId(
                "https://youtube.com/watch?v=abcdefghijk&v=lmnopqrstuv"
            )
        )
        assertNull(WorkoutVideoUrl.extractVideoId("https://youtube.com/embed/abcdefghijk/extra"))
        assertNull(WorkoutVideoUrl.extractVideoId("https://youtu.be/abcdefghijk/extra"))
        assertNull(WorkoutVideoUrl.extractVideoId("https://youtube.com/channel/abcdefghijk"))
    }

    @Test fun rejectsMalformedIdsAndOversizedInput() {
        assertNull(WorkoutVideoUrl.extractVideoId("https://youtube.com/watch?v=short"))
        assertNull(WorkoutVideoUrl.extractVideoId("https://youtube.com/watch?v=abcdefghij!"))
        assertNull(WorkoutVideoUrl.extractVideoId("https://youtube.com/watch?v=abcdefghijk%2F"))
        assertNull(WorkoutVideoUrl.extractVideoId("x".repeat(2049)))
        assertNull(WorkoutVideoUrl.extractVideoId(""))
    }
}
