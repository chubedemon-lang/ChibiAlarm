package com.chibialarm.app

import org.junit.Assert.*
import org.junit.Test

/** Parser protects boot restore and broadcast receivers from corrupt preferences. */
class AlarmTimeCodecTest {
    @Test fun supportsLegacyTwoFieldAndModernThreeFieldAlarms() {
        assertEquals(AlarmTimeCodec.Entry(6, 7, 127), AlarmTimeCodec.parse("6,7"))
        assertEquals(AlarmTimeCodec.Entry(23, 59, 65), AlarmTimeCodec.parse("23,59,65"))
        assertEquals(AlarmTimeCodec.Entry(0, 0, 0), AlarmTimeCodec.parse("0,0,0"))
    }

    @Test fun invalidRecordsFailClosedInsteadOfCrashingOnBootOrRing() {
        for (raw in listOf<String?>(null, "", "x,10,127", "8,y,127",
                "24,0,127", "-1,0,127", "10,60,127", "10,-1,127",
                "10,0,-1", "10,0,128", "10,0,wat", "10", "10,0,127,extra")) {
            assertNull("Unexpectedly accepted: $raw", AlarmTimeCodec.parse(raw))
        }
    }

    @Test fun acceptsWhitespaceWithoutChangingWeekdayBitMask() {
        assertEquals(AlarmTimeCodec.Entry(8, 5, 3), AlarmTimeCodec.parse(" 8 , 05 , 3 "))
    }
}
