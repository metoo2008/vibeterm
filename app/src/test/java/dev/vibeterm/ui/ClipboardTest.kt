package dev.vibeterm.ui

import com.termux.terminal.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardTest {

    private val max = TerminalEmulator.MAX_PASTE_CHARS

    /** 轻量假 CharSequence:只报告 length,不真的分配上百万字符。 */
    private fun seq(n: Int): CharSequence = object : CharSequence {
        override val length = n
        override fun get(index: Int) = 'x'
        override fun subSequence(startIndex: Int, endIndex: Int) = "x".repeat(endIndex - startIndex)
        override fun toString() = "x".repeat(length)
    }

    @Test
    fun nullIsUnsupported() {
        // URI/Intent 型剪贴板 item.text 为 null → 拒绝(不经 ContentProvider 读取)
        assertEquals(Clipboard.Result.Unsupported, Clipboard.classify(null))
    }

    @Test
    fun emptyIsEmpty() {
        assertEquals(Clipboard.Result.Empty, Clipboard.classify(""))
    }

    @Test
    fun normalTextPassesThrough() {
        val r = Clipboard.classify("git status")
        assertTrue(r is Clipboard.Result.Text)
        assertEquals("git status", (r as Clipboard.Result.Text).text)
    }

    @Test
    fun exactlyAtLimitAccepted() {
        val r = Clipboard.classify(seq(max))
        assertTrue(r is Clipboard.Result.Text)
    }

    @Test
    fun overLimitRejectedNotTruncated() {
        // 超限整次拒绝(TooLarge),绝不截断后发送半条命令
        assertEquals(Clipboard.Result.TooLarge, Clipboard.classify(seq(max + 1)))
    }

    @Test
    fun pasteWithinLimitBoundary() {
        assertTrue(TerminalEmulator.isPasteWithinLimit(0))
        assertTrue(TerminalEmulator.isPasteWithinLimit(max))
        assertTrue(!TerminalEmulator.isPasteWithinLimit(max + 1))
    }
}
