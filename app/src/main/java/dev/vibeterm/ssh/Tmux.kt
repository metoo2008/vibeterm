package dev.vibeterm.ssh

/** 服务端 tmux 接管命令构造。 */
object Tmux {

    /** tmux 会话名只用安全字符(vt + 序号),避免引号/注入问题。 */
    fun sessionName(index: Int) = "vt$index"

    /**
     * -u 强制 UTF-8;new-session -A:存在则 attach,否则创建 —— 幂等,断线重连回到原会话。
     * 服务器没装 tmux 时回落普通 login shell,并提示会话不具备断线保活能力。
     */
    fun attachCommand(session: String): String =
        "if command -v tmux >/dev/null 2>&1; then " +
            "exec tmux -u new-session -A -s $session; " +
            "else " +
            "echo '[VibeTerm] tmux not found on server; falling back to a plain shell.'; " +
            "echo '[VibeTerm] Long-running programs will NOT survive disconnects. Install tmux to fix.'; " +
            "exec \$SHELL -l; " +
            "fi"
}
