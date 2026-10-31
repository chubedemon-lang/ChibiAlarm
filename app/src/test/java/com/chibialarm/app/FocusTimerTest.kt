package com.chibialarm.app

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Focus completion must survive process restarts without double-paying sessions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FocusTimerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("focus", Context.MODE_PRIVATE)

    @Before fun reset() {
        FocusTimer.stop(context)
        prefs.edit().clear().commit()
    }

    @After fun cleanup() {
        FocusTimer.stop(context)
        prefs.edit().clear().commit()
    }

    private fun expiredSession() {
        assertTrue(prefs.edit().putLong("endTime", System.currentTimeMillis() - 1_000L).commit())
    }

    @Test fun repeatedReceiverAndRestoreCanOnlyCompleteOnce() {
        expiredSession()
        assertTrue(FocusTimer.complete(context))
        assertFalse(FocusTimer.complete(context))
        FocusTimer.restore(context)
        assertEquals(1, FocusTimer.sessions(context))
        assertEquals(1, prefs.getInt("sessions:" + LifeData.today(), 0))
        assertEquals(0L, FocusTimer.endTime(context))
    }

    @Test fun incompleteSessionDoesNotPayReward() {
        assertTrue(prefs.edit().putLong("endTime", System.currentTimeMillis() + 60_000L).commit())
        assertFalse(FocusTimer.complete(context))
        assertEquals(0, FocusTimer.sessions(context))
    }

    @Test fun completionCountersNeverWrapNegative() {
        expiredSession()
        val dayKey = "sessions:" + LifeData.today()
        prefs.edit().putInt("sessions", Int.MAX_VALUE)
            .putInt(dayKey, Int.MAX_VALUE).commit()
        assertTrue(FocusTimer.complete(context))
        assertEquals(Int.MAX_VALUE, FocusTimer.sessions(context))
        assertEquals(Int.MAX_VALUE, prefs.getInt(dayKey, 0))
    }

    @Test fun concurrentCompletionOnlyPaysOnce() {
        expiredSession()
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val successes = AtomicInteger()
        try {
            repeat(threads) {
                pool.execute {
                    try {
                        ready.countDown()
                        if (start.await(5, TimeUnit.SECONDS) && FocusTimer.complete(context)) {
                            successes.incrementAndGet()
                        }
                    } finally {
                        done.countDown()
                    }
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals(1, successes.get())
            assertEquals(1, FocusTimer.sessions(context))
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }
}
