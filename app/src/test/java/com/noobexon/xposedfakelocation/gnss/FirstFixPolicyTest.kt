package com.noobexon.xposedfakelocation.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FirstFixPolicyTest {
    @Test
    fun firstFixIsDueOnceAfterTheTtff() {
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(1_000L)
        assertNull(policy.firstFixDue(1_999L))
        assertEquals(0, policy.timeToFirstFixMillis)
        val ttff = policy.firstFixDue(5_000L)!!
        assertTrue(ttff in 1000..4000)
        assertEquals(ttff, policy.timeToFirstFixMillis)
        assertNull(policy.firstFixDue(6_000L))
    }

    @Test
    fun ttffDelayIsRespected() {
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        val ttff = policy.firstFixDue(10_000L)!!
        val again = FirstFixPolicy(Random(7))
        again.onStarted(0L)
        assertNull(again.firstFixDue(ttff - 1L))
        assertEquals(ttff, again.firstFixDue(ttff.toLong()))
    }

    @Test
    fun registeredWhileRunningGetsTheFirstFixImmediately() {
        val policy = FirstFixPolicy(Random(7))
        val ttff = policy.firstFixDue(5_000L)!!
        assertTrue(ttff in 1000..4000)
        assertNull(policy.firstFixDue(5_001L))
    }

    @Test
    fun stoppedEngineHasNoFirstFixUntilRestarted() {
        val policy = FirstFixPolicy(Random(7))
        policy.onStarted(0L)
        policy.firstFixDue(10_000L)
        policy.onStopped()
        assertNull(policy.firstFixDue(20_000L))
        assertEquals(0, policy.timeToFirstFixMillis)
        policy.onStarted(30_000L)
        assertNull(policy.firstFixDue(30_500L))
        assertTrue(policy.firstFixDue(40_000L) != null)
    }

    @Test
    fun realFirstFixIsDroppedOnlyWhileSpoofing() {
        assertFalse(FirstFixPolicy.deliversRealFirstFix(spoofing = true))
        assertTrue(FirstFixPolicy.deliversRealFirstFix(spoofing = false))
    }
}
