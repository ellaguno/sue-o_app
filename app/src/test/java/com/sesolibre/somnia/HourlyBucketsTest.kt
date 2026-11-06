package com.sesolibre.somnia

import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.stats.NightAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class HourlyBucketsTest {

    private val hour = 3_600_000L
    private val min = 60_000L

    private fun event(start: Long, duration: Long, category: String) = SoundEvent(
        id = start, sessionId = 1, startEpochMs = start, endEpochMs = start + duration,
        durationMs = duration, dbPeak = -40.0, dbAvg = -45.0, category = category,
    )

    @Test
    fun `reparte la noche en horas de reloj`() {
        // 23:30 → 02:10 (UTC) = horas 23, 0, 1, 2
        val start = 23 * hour + 30 * min
        val session = Session(id = 1, startEpochMs = start, endEpochMs = start + 2 * hour + 40 * min)
        val buckets = NightAnalyzer.hourlyBuckets(session, emptyList())
        assertEquals(4, buckets.size)
        assertEquals(23 * hour, buckets.first().hourStartEpochMs)
    }

    @Test
    fun `un ronquido que cruza la hora se reparte entre ambas`() {
        val start = 23 * hour + 30 * min
        val session = Session(id = 1, startEpochMs = start, endEpochMs = start + 2 * hour)
        val snore = event(start + 29 * min, 2 * min, "snoring") // 23:59 → 00:01
        val cough = event(start + 40 * min, 10_000, "cough")    // 00:10
        val other = event(start + 45 * min, 10_000, "other")
        val buckets = NightAnalyzer.hourlyBuckets(session, listOf(snore, cough, other))
        assertEquals(1.0, buckets[0].snoringMinutes, 1e-9)
        assertEquals(1.0, buckets[1].snoringMinutes, 1e-9)
        assertEquals(1, buckets[0].bodyEvents) // ronquido
        assertEquals(1, buckets[1].bodyEvents) // tos; "otro" no cuenta
    }

    @Test
    fun `respeta el desfase de la zona horaria`() {
        // 05:30 UTC con zona -6 h = 23:30 local: la primera hora empieza a las 05:00 UTC.
        val start = 5 * hour + 30 * min
        val session = Session(id = 1, startEpochMs = start, endEpochMs = start + hour)
        val buckets = NightAnalyzer.hourlyBuckets(session, emptyList(), zoneOffsetMs = -6 * hour)
        assertEquals(5 * hour, buckets.first().hourStartEpochMs)
    }
}
