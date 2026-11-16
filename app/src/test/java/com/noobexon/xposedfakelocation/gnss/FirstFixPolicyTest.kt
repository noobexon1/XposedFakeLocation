package com.noobexon.xposedfakelocation.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FirstFixPolicyTest {
    /** The TTFFs a policy seeded with 7 draws, in order. */
    private fun ttffDraws(count: Int): List<Int> = Random(7).let { random -> List(count) { random.nextInt(1000, 4001) } }

    @Test
    fun fixIsDueAfterTheTtffAndStays() {
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(1_000L)
        assertNull(policy.fixedTtff(1_999L))
        assertEquals(0, policy.timeToFirstFixMillis)
        val ttff = policy.fixedTtff(5_000L)!!
        assertTrue(ttff in 1000..4000)
        assertEquals(ttff, policy.timeToFirstFixMillis)
        assertEquals(ttff, policy.fixedTtff(6_000L))
        assertEquals(ttff, policy.timeToFirstFixMillis)
    }

    @Test
    fun ttffDelayIsRespected() {
        val ttff = ttffDraws(1).single()
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        assertNull(policy.fixedTtff(ttff - 1L))
        assertEquals(ttff, policy.fixedTtff(ttff.toLong()))
        assertEquals(ttff, policy.timeToFirstFixMillis)
    }

    @Test
    fun repeatedStartKeepsTheSession() {
        val ttff = ttffDraws(1).single()
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        policy.onStarted(500L)
        assertNull(policy.fixedTtff(ttff - 1L))
        assertEquals(ttff, policy.fixedTtff(ttff.toLong()))
        policy.onStarted(20_000L)
        assertEquals(ttff, policy.fixedTtff(20_001L))
        assertEquals(ttff, policy.timeToFirstFixMillis)
    }

    @Test
    fun realFirstFixFixesTheSessionWithItsTtff() {
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        policy.onRealFirstFix(2_345)
        assertEquals(2_345, policy.timeToFirstFixMillis)
        assertEquals(2_345, policy.fixedTtff(1L))
        assertEquals(2_345, policy.timeToFirstFixMillis)
    }

    @Test
    fun legacyRealFirstFixKeepsTheDrawnTtff() {
        val ttff = ttffDraws(1).single()
        val started = FirstFixPolicy(Random(7))
        started.onStarted(0L)
        started.onRealFirstFix(null)
        assertEquals(ttff, started.fixedTtff(1L))
        assertEquals(ttff, started.timeToFirstFixMillis)

        val unknown = FirstFixPolicy(Random(7))
        unknown.onRealFirstFix(null)
        assertEquals(ttff, unknown.timeToFirstFixMillis)
    }

    @Test
    fun registeredWhileRunningIsFixedImmediately() {
        val policy = FirstFixPolicy(Random(7))
        assertEquals(0, policy.timeToFirstFixMillis)
        val ttff = policy.fixedTtff(5_000L)!!
        assertTrue(ttff in 1000..4000)
        assertEquals(ttff, policy.timeToFirstFixMillis)
        assertEquals(ttff, policy.fixedTtff(5_001L))
    }

    @Test
    fun stopBeforeTheFixHasNoFix() {
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        policy.onStopped()
        assertEquals(0, policy.timeToFirstFixMillis)
    }

    @Test
    fun statusWhileStoppedRecoversTheMissedStart() {
        val (_, second, third) = ttffDraws(3)
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        policy.onStopped()
        val ttff = policy.fixedTtff(10_000L)!!
        assertTrue(ttff in 1000..4000)
        assertEquals(second, ttff)
        assertEquals(ttff, policy.timeToFirstFixMillis)
        policy.onStopped()
        policy.onStarted(30_000L)
        assertNull(policy.fixedTtff(30_999L))
        assertEquals(0, policy.timeToFirstFixMillis)
        assertEquals(third, policy.fixedTtff(34_000L))
    }

    @Test
    fun stopAfterTheFixClearsItUntilTheRestartFixes() {
        val (first, second) = ttffDraws(2)
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        assertEquals(first, policy.fixedTtff(10_000L))
        assertEquals(first, policy.timeToFirstFixMillis)
        policy.onStopped()
        assertEquals(0, policy.timeToFirstFixMillis)
        policy.onStarted(30_000L)
        assertNull(policy.fixedTtff(30_999L))
        assertEquals(0, policy.timeToFirstFixMillis)
        assertEquals(second, policy.fixedTtff(34_000L))
        assertEquals(second, policy.timeToFirstFixMillis)
    }

    @Test
    fun realFirstFixIsDroppedOnlyWhileSpoofing() {
        assertFalse(FirstFixPolicy.deliversRealFirstFix(spoofing = true))
        assertTrue(FirstFixPolicy.deliversRealFirstFix(spoofing = false))
    }
}
