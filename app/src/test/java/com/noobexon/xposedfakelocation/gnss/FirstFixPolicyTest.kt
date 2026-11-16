package com.noobexon.xposedfakelocation.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertFalse(policy.isFixed(1_999L))
        assertEquals(0, policy.timeToFirstFixMillis)
        assertTrue(policy.isFixed(5_000L))
        val ttff = policy.timeToFirstFixMillis
        assertTrue(ttff in 1000..4000)
        assertTrue(policy.isFixed(6_000L))
        assertEquals(ttff, policy.timeToFirstFixMillis)
    }

    @Test
    fun ttffDelayIsRespected() {
        val ttff = ttffDraws(1).single()
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        assertFalse(policy.isFixed(ttff - 1L))
        assertTrue(policy.isFixed(ttff.toLong()))
        assertEquals(ttff, policy.timeToFirstFixMillis)
    }

    @Test
    fun repeatedStartKeepsTheSession() {
        val ttff = ttffDraws(1).single()
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        policy.onStarted(500L)
        assertFalse(policy.isFixed(ttff - 1L))
        assertTrue(policy.isFixed(ttff.toLong()))
        policy.onStarted(20_000L)
        assertTrue(policy.isFixed(20_001L))
        assertEquals(ttff, policy.timeToFirstFixMillis)
    }

    @Test
    fun realFirstFixFixesTheSessionWithItsTtff() {
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        policy.onRealFirstFix(2_345)
        assertEquals(2_345, policy.timeToFirstFixMillis)
        assertTrue(policy.isFixed(1L))
        assertEquals(2_345, policy.timeToFirstFixMillis)
    }

    @Test
    fun legacyRealFirstFixKeepsTheDrawnTtff() {
        val ttff = ttffDraws(1).single()
        val started = FirstFixPolicy(Random(7))
        started.onStarted(0L)
        started.onRealFirstFix(null)
        assertTrue(started.isFixed(1L))
        assertEquals(ttff, started.timeToFirstFixMillis)

        val unknown = FirstFixPolicy(Random(7))
        unknown.onRealFirstFix(null)
        assertEquals(ttff, unknown.timeToFirstFixMillis)
    }

    @Test
    fun registeredWhileRunningIsFixedImmediately() {
        val policy = FirstFixPolicy(Random(7))
        assertEquals(0, policy.timeToFirstFixMillis)
        assertTrue(policy.isFixed(5_000L))
        assertTrue(policy.timeToFirstFixMillis in 1000..4000)
        assertTrue(policy.isFixed(5_001L))
    }

    @Test
    fun stopBeforeTheFixHasNoFix() {
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        policy.onStopped()
        assertFalse(policy.isFixed(10_000L))
        assertEquals(0, policy.timeToFirstFixMillis)
    }

    @Test
    fun stopAfterTheFixClearsItUntilTheRestartFixes() {
        val (first, second) = ttffDraws(2)
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        assertTrue(policy.isFixed(10_000L))
        assertEquals(first, policy.timeToFirstFixMillis)
        policy.onStopped()
        assertEquals(0, policy.timeToFirstFixMillis)
        assertFalse(policy.isFixed(20_000L))
        policy.onStarted(30_000L)
        assertFalse(policy.isFixed(30_999L))
        assertEquals(0, policy.timeToFirstFixMillis)
        assertTrue(policy.isFixed(34_000L))
        assertEquals(second, policy.timeToFirstFixMillis)
    }

    @Test
    fun realFirstFixIsDroppedOnlyWhileSpoofing() {
        assertFalse(FirstFixPolicy.deliversRealFirstFix(spoofing = true))
        assertTrue(FirstFixPolicy.deliversRealFirstFix(spoofing = false))
    }
}
