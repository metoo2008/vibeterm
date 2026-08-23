package dev.vibeterm.ssh

/** 服务端 tmux 接管命令构造。 */
object Tmux {

    /** tmux 会话名只用安全字符(vt + 序号),避免引号/注入问题。 */
    fun sessionName(index: Int) = "vt$index"

    /**
     * -u 强制 UTF-8;new-session -A:存在则 attach,否则创建 —— 幂等,断线重连回到原会话;
     * -D:attach 时踢掉其他客户端 —— 换网重连后服务端可能残留幽灵客户端,不踢会把窗口尺寸拖小。
     *
     * SSH exec 环境的 PATH 很瘦(不含 Homebrew 等),`command -v` 找不到时按常见安装路径显式探测:
     * macOS Apple 芯片 /opt/homebrew/bin、macOS Intel 及 BSD /usr/local/bin、Linuxbrew、标准 /usr/bin。
     * 都没有才回落普通 login shell,并提示会话不具备断线保活能力。
     */
    fun attachCommand(session: String): String =
        "VT_TMUX=\$(command -v tmux 2>/dev/null); " +
            "if [ -z \"\$VT_TMUX\" ]; then " +
            "for p in /opt/homebrew/bin/tmux /usr/local/bin/tmux /home/linuxbrew/.linuxbrew/bin/tmux /usr/bin/tmux; do " +
            "if [ -x \"\$p\" ]; then VT_TMUX=\"\$p\"; break; fi; done; fi; " +
            "if [ -n \"\$VT_TMUX\" ]; then " +
            "exec \"\$VT_TMUX\" -u new-session -A -D -s $session; " +
            "else " +
            "echo '[VibeTerm] tmux not found on server; falling back to a plain shell.'; " +
            "echo '[VibeTerm] Long-running programs will NOT survive disconnects. Install tmux to fix.'; " +
            "exec \$SHELL -l; " +
            "fi"
}
