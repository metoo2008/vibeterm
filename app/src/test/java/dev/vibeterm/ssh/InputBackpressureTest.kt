package dev.vibeterm.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputBackpressureTest {

    private val cap = InputBackpressure.MAX_PENDING_INPUT_BYTES

    @Test
    fun allowsWhenUnderLimit() {
        assertTrue(InputBackpressure.canEnqueue(0, 1))
        assertTrue(InputBackpressure.canEnqueue(0, 1024))
        assertTrue(InputBackpressure.canEnqueue(cap - 10, 10))
    }

    @Test
    fun allowsExactlyAtLimit() {
        // 恰好填满到上限应允许(4 MiB 在 Int 范围内)
        assertTrue(InputBackpressure.canEnqueue(0, cap.toInt()))
        assertTrue(InputBackpressure.canEnqueue(cap - 100, 100))
    }

    @Test
    fun rejectsWhenExceedingLimit() {
        assertFalse(InputBackpressure.canEnqueue(cap, 1))          // 已满再来一个
        assertFalse(InputBackpressure.canEnqueue(cap - 10, 11))    // 差一点点超
        assertFalse(InputBackpressure.canEnqueue(0, Int.MAX_VALUE)) // 单次超大粘贴
    }

    @Test
    fun rejectsNonPositiveCount() {
        assertFalse(InputBackpressure.canEnqueue(0, 0))
        assertFalse(InputBackpressure.canEnqueue(0, -5))
    }

    @Test
    fun dequeueAccountingRoundTrips() {
        // 模拟入队累加、出队扣减,余量应始终允许再次入队
        var pending = 0L
        val chunk = 1000
        repeat(100) { if (InputBackpressure.canEnqueue(pending, chunk)) pending += chunk }
        assertTrue(pending <= cap)
        repeat(100) { pending -= chunk } // 出队扣减
        assertTrue(pending == 0L)
        assertTrue(InputBackpressure.canEnqueue(pending, chunk))
    }
}
