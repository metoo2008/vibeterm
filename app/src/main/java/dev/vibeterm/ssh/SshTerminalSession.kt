package dev.vibeterm.ssh

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.trilead.ssh2.Connection
import com.trilead.ssh2.Session as SshChannel
import dev.vibeterm.data.HostProfile
import dev.vibeterm.data.KnownHosts
import dev.vibeterm.notify.Notifications
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 基于 SSH(sshlib/trilead)的终端会话。
 *
 * 生命周期:emulator 只创建一次(保留滚回历史);传输层可多次断开/重连(generation 递增使旧
 * IO 线程自然退出)。只有用户主动关闭才走 onTransportExited 终结整个会话。
 */
class SshTerminalSession(
    val profile: HostProfile,
    val windowIndex: Int,
    private val password: String,
    client: TerminalSessionClient,
) : TerminalSession(TRANSCRIPT_ROWS, client) {

    enum class State { CONNECTING, CONNECTED, DISCONNECTED, CLOSED }

    var state by mutableStateOf(State.CONNECTING)
        private set
    var stateMessage by mutableStateOf<String?>(null)
        private set
    var terminalTitle by mutableStateOf("")

    /** 当前正在显示本会话的 TerminalView(用于屏幕刷新回调),由 UI 层维护。 */
    var attachedView: TerminalView? = null

    /** tmux 会话名;null 表示本会话不走 tmux(普通 shell)。 */
    val tmuxName: String? = if (profile.useTmux) Tmux.sessionName(windowIndex) else null

    val displayName: String
        get() = "${profile.displayName} #$windowIndex"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var connection: Connection? = null
    @Volatile private var channel: SshChannel? = null
    @Volatile private var userClosed = false

    /** 重连代数:每次 connect() 递增,旧代的 IO 线程检测到不匹配即退出。 */
    @Volatile private var generation = 0

    @Volatile private var cols = 80
    @Volatile private var rows = 24
    @Volatile private var reconnectDelayMs = 1_000L

    private val writeQueue = LinkedBlockingQueue<ByteArray>()

    // ---- “忙碌后静默”完成通知的启发式状态 ----
    @Volatile private var lastOutputAt = 0L
    @Volatile private var busySince = 0L
    private val silenceChecker = Runnable { checkSilence() }

    // ------------------------------------------------------------------
    // TerminalSession 传输 SPI
    // ------------------------------------------------------------------

    override fun onTransportStart(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.cols = columns
        this.rows = rows
        connect()
    }

    override fun onTransportWrite(data: ByteArray, offset: Int, count: Int) {
        if (state == State.CONNECTED || state == State.CONNECTING) {
            writeQueue.offer(data.copyOfRange(offset, offset + count))
        }
    }

    override fun onTransportResize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.cols = columns
        this.rows = rows
        val ch = channel ?: return
        thread(name = "ssh-resize", isDaemon = true) {
            try {
                ch.resizePTY(columns, rows, 0, 0)
            } catch (_: Exception) {
            }
        }
    }

    override fun onTransportKill() {
        userClosed = true
        generation++
        mainHandler.removeCallbacks(silenceChecker)
        setState(State.CLOSED, null)
        thread(name = "ssh-close", isDaemon = true) { closeTransport() }
        onTransportExited(0, "VibeTerm session closed")
    }

    // ------------------------------------------------------------------
    // 连接与重连
    // ------------------------------------------------------------------

    fun reconnect() {
        if (!userClosed && state == State.DISCONNECTED) connect()
    }

    private fun connect() {
        if (userClosed) return
        val gen = ++generation
        writeQueue.clear()
        setState(State.CONNECTING, null)
        thread(name = "ssh-connect-$gen") {
            try {
                val conn = Connection(profile.host, profile.port)
                conn.connect({ hostname, port, algorithm, key ->
                    when (KnownHosts.verify(SessionManager.appContext, hostname, port, algorithm, key)) {
                        KnownHosts.Result.Trusted, KnownHosts.Result.FirstUse -> true
                        is KnownHosts.Result.Mismatch -> {
                            postStatus("\r\n[VibeTerm] 主机指纹与上次记录不一致,已拒绝连接(谨防中间人攻击)。")
                            false
                        }
                    }
                }, CONNECT_TIMEOUT_MS, KEX_TIMEOUT_MS)

                if (gen != generation || userClosed) {
                    conn.close(); return@thread
                }
                if (!conn.authenticateWithPassword(profile.username, password)) {
                    conn.close()
                    onConnectFailed(gen, "认证失败:用户名或密码错误", fatal = true)
                    return@thread
                }
                conn.setTCPNoDelay(true)

                val ch = conn.openSession()
                ch.requestPTY("xterm-256color", cols, rows, 0, 0, null)
                if (tmuxName != null) ch.execCommand(Tmux.attachCommand(tmuxName)) else ch.startShell()

                if (gen != generation || userClosed) {
                    ch.close(); conn.close(); return@thread
                }
                connection = conn
                channel = ch
                reconnectDelayMs = 1_000L
                setState(State.CONNECTED, null)

                startWriter(gen, ch.stdin)
                startKeepAlive(gen, conn)
                startSecondaryReader(gen, ch.stderr)
                readLoop(gen, ch.stdout) // 占用本线程直到断开
            } catch (e: Exception) {
                onConnectFailed(gen, e.message ?: e.javaClass.simpleName, fatal = false)
            }
        }
    }

    private fun readLoop(gen: Int, stream: InputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                if (gen != generation) return
                onOutputActivity()
                onTransportData(buffer, 0, read)
            }
        } catch (_: Exception) {
        }
        if (gen == generation) onDisconnected(gen, null)
    }

    private fun startSecondaryReader(gen: Int, stream: InputStream) {
        thread(name = "ssh-stderr-$gen", isDaemon = true) {
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1 || gen != generation) return@thread
                    onTransportData(buffer, 0, read)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startWriter(gen: Int, out: OutputStream) {
        thread(name = "ssh-writer-$gen", isDaemon = true) {
            try {
                while (gen == generation) {
                    val chunk = writeQueue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    out.write(chunk)
                    out.flush()
                }
            } catch (_: Exception) {
                // 断开由读线程统一处理
            }
        }
    }

    private fun startKeepAlive(gen: Int, conn: Connection) {
        thread(name = "ssh-keepalive-$gen", isDaemon = true) {
            try {
                while (gen == generation) {
                    Thread.sleep(KEEPALIVE_INTERVAL_MS)
                    if (gen != generation) return@thread
                    conn.sendIgnorePacket()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun onDisconnected(gen: Int, message: String?) {
        if (userClosed || gen != generation) return
        closeTransport()
        postStatus("\r\n[VibeTerm] 连接已断开${if (message != null) ":$message" else ""}")
        setState(State.DISCONNECTED, message)
        scheduleReconnect()
    }

    private fun onConnectFailed(gen: Int, message: String, fatal: Boolean) {
        if (userClosed || gen != generation) return
        closeTransport()
        postStatus("\r\n[VibeTerm] 连接失败:$message")
        setState(State.DISCONNECTED, message)
        if (!fatal) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (userClosed || !SessionManager.appVisible) return // 后台不重连(tmux 兜底),回前台统一触发
        val delay = reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(30_000L)
        postStatus("\r\n[VibeTerm] ${delay / 1000} 秒后自动重连…")
        mainHandler.postDelayed({
            if (state == State.DISCONNECTED && SessionManager.appVisible) connect()
        }, delay)
    }

    private fun closeTransport() {
        try { channel?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        channel = null
        connection = null
    }

    /** 向本地终端(不经远端)追加提示文本。 */
    private fun postStatus(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        onTransportData(bytes, 0, bytes.size)
    }

    private fun setState(s: State, message: String?) {
        mainHandler.post {
            state = s
            stateMessage = message
        }
    }

    // ------------------------------------------------------------------
    // “忙碌后静默”启发式:连续输出超过 BUSY_MIN_MS 的会话安静 SILENCE_MS 后视为任务完成
    // ------------------------------------------------------------------

    private fun onOutputActivity() {
        val now = SystemClock.elapsedRealtime()
        val prev = lastOutputAt
        lastOutputAt = now
        if (prev != 0L && now - prev < BUSY_GAP_MS) {
            if (busySince == 0L) busySince = prev
        } else {
            busySince = now
        }
        mainHandler.removeCallbacks(silenceChecker)
        mainHandler.postDelayed(silenceChecker, SILENCE_MS)
    }

    private fun checkSilence() {
        val now = SystemClock.elapsedRealtime()
        val busyFor = if (busySince == 0L) 0 else lastOutputAt - busySince
        if (busyFor >= BUSY_MIN_MS && now - lastOutputAt >= SILENCE_MS) {
            busySince = 0L
            Notifications.onPossiblyFinished(this)
        }
    }

    companion object {
        private const val TRANSCRIPT_ROWS = 4000
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val KEX_TIMEOUT_MS = 20_000
        private const val KEEPALIVE_INTERVAL_MS = 15_000L
        private const val BUSY_GAP_MS = 3_000L
        private const val BUSY_MIN_MS = 10_000L
        private const val SILENCE_MS = 8_000L
    }
}
