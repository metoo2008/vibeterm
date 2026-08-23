package dev.vibeterm.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TmuxTest {

    @Test
    fun sessionName_isSafeAndStable() {
        assertEquals("vt1", Tmux.sessionName(1))
        assertEquals("vt42", Tmux.sessionName(42))
        // 会话名只含代码生成的字面量,无用户可控内容 → 无 shell 注入面
        assertTrue(Tmux.sessionName(7).all { it.isLetterOrDigit() })
    }

    @Test
    fun attachCommand_isIdempotentAndKicksGhosts() {
        val cmd = Tmux.attachCommand("vt1")
        // -A:存在则 attach 否则创建;-D:踢掉幽灵客户端防窗口尺寸被拖小
        assertTrue(cmd.contains("new-session -A -D -s vt1"))
        assertTrue(cmd.contains("-u")) // 强制 UTF-8
    }

    @Test
    fun attachCommand_probesCommonTmuxPathsForMacOsAndBrew() {
        val cmd = Tmux.attachCommand("vt1")
        assertTrue(cmd.contains("/opt/homebrew/bin/tmux"))    // macOS Apple 芯片
        assertTrue(cmd.contains("/usr/local/bin/tmux"))       // macOS Intel / BSD
        assertTrue(cmd.contains("falling back to a plain shell")) // 无 tmux 回退提示
    }

    @Test
    fun attachCommand_hasNoUnsubstitutedControlChars() {
        // 命令字符串不应混入字面控制字符(历史上多次踩坑)
        assertFalse(Tmux.attachCommand("vt1").any { it.code < 32 && it != '\n' && it != '\t' })
    }
}
