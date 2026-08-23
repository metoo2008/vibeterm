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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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

    /** 主机公钥待确认:首连(Unknown)或密钥变更(changed=true)。由 UI 展示指纹后回调决定。 */
    class HostKeyPrompt(
        val host: String,
        val port: Int,
        val algorithm: String,
        val fingerprint: String,
        val changed: Boolean,
        val previousFingerprint: String?,
        val onDecision: (accepted: Boolean) -> Unit,
    )

    var state by mutableStateOf(State.CONNECTING)
        private set
    var stateMessage by mutableStateOf<String?>(null)
        private set
    var terminalTitle by mutableStateOf("")
    var hostKeyPrompt by mutableStateOf<HostKeyPrompt?>(null)
        private set

    /** 当前正在显示本会话的 TerminalView(用于屏幕刷新回调),由 UI 层维护。 */
    var attachedView: TerminalView? = null

    /** tmux 会话名;null 表示本会话不走 tmux(普通 shell)。 */
    val tmuxName: String? = if (profile.useTmux) Tmux.sessionName(windowIndex) else null

    val displayName: String
        get() = "${profile.displayName} #$windowIndex"

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 单线程串行执行 resize,避免旋转/分屏时线程突增;任务执行时读最新 cols/rows,天然合并陈旧请求。 */
    private val resizeExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "ssh-resize").apply { isDaemon = true } }

    @Volatile private var connection: Connection? = null
    @Volatile private var channel: SshChannel? = null
    @Volatile private var userClosed = false

    /** 重连代数:每次 connect() 递增,旧代的 IO 线程检测到不匹配即退出。 */
    @Volatile private var generation = 0

    /** 每代一个;onDisconnected 只对本代生效一次(读/写/keepalive 任一线程先发现断开即触发)。 */
    @Volatile private var disconnected = AtomicBoolean(false)

    /** 每代一个独立的有界写队列;重连换新队列,旧写线程守着旧队列自然退出,不会窃取新按键。 */
    @Volatile private var writeQueue = ArrayBlockingQueue<ByteArray>(WRITE_QUEUE_CAPACITY)

    @Volatile private var cols = 80
    @Volatile private var rows = 24
    @Volatile private var reconnectDelayMs = BASE_RECONNECT_MS
    @Volatile private var connectedAt = 0L

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
        if (state == State.CONNECTED) {
            val chunk = data.copyOfRange(offset, offset + count)
            // 有界队列:满则丢弃最旧,保证不 OOM 且优先保留最新输入
            val q = writeQueue
            if (!q.offer(chunk)) {
                q.poll()
                q.offer(chunk)
            }
        }
    }

    override fun onTransportResize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.cols = columns
        this.rows = rows
        val gen = generation
        try {
            resizeExecutor.execute {
                if (gen != generation) return@execute
                try {
                    channel?.resizePTY(this.cols, this.rows, 0, 0)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
            // executor 已关闭(会话结束),忽略
        }
    }

    override fun onTransportKill() {
        userClosed = true
        generation++
        mainHandler.removeCallbacks(silenceChecker)
        setState(State.CLOSED, null)
        resolveHostKeyPrompt(false)
        thread(name = "ssh-close", isDaemon = true) { closeTransport() }
        resizeExecutor.shutdownNow()
        onTransportExited(0, "VibeTerm session closed")
    }

    // ------------------------------------------------------------------
    // 连接与重连
    // ------------------------------------------------------------------

    fun reconnect() {
        if (!userClosed && state == State.DISCONNECTED) connect()
    }

    /**
     * 系统默认网络发生了切换(WiFi↔流量):旧 TCP 几乎必死但检测可能要很久,
     * 直接丢弃旧传输立即重连,tmux -A 无缝回到原会话。
     */
    fun onNetworkChanged() {
        if (userClosed) return
        when (state) {
            State.CONNECTED, State.CONNECTING -> {
                val oldChannel = channel
                val oldConnection = connection
                channel = null
                connection = null
                thread(name = "ssh-netswitch-close", isDaemon = true) {
                    try { oldChannel?.close() } catch (_: Exception) {}
                    try { oldConnection?.close() } catch (_: Exception) {}
                }
                postStatus("\r\n[VibeTerm] 网络已切换,正在重连…")
                connect()
            }
            State.DISCONNECTED -> connect()
            State.CLOSED -> {}
        }
    }

    private fun connect() {
        if (userClosed) return
        val gen = ++generation
        val myDisconnected = AtomicBoolean(false)
        disconnected = myDisconnected
        val myQueue = ArrayBlockingQueue<ByteArray>(WRITE_QUEUE_CAPACITY)
        writeQueue = myQueue
        setState(State.CONNECTING, null)
        thread(name = "ssh-connect-$gen") {
            var conn: Connection? = null
            var ch: SshChannel? = null
            try {
                conn = Connection(profile.host, profile.port)
                conn.connect({ hostname, port, algorithm, key ->
                    verifyHostKey(gen, hostname, port, algorithm, key)
                }, CONNECT_TIMEOUT_MS, KEX_TIMEOUT_MS)

                if (gen != generation || userClosed) { conn.close(); return@thread }
                if (!conn.authenticateWithPassword(profile.username, password)) {
                    conn.close()
                    onConnectFailed(gen, "认证失败:用户名或密码错误", fatal = true)
                    return@thread
                }

                ch = conn.openSession()
                ch.requestPTY("xterm-256color", cols, rows, 0, 0, null)
                if (tmuxName != null) ch.execCommand(Tmux.attachCommand(tmuxName)) else ch.startShell()

                if (gen != generation || userClosed) { ch.close(); conn.close(); return@thread }
                connection = conn
                channel = ch
                connectedAt = SystemClock.elapsedRealtime()
                setState(State.CONNECTED, null)

                startWriter(gen, myDisconnected, myQueue, ch.stdin)
                startKeepAlive(gen, myDisconnected, conn)
                startSecondaryReader(gen, ch.stdout, ch.stderr)
                readLoop(gen, myDisconnected, ch.stdout) // 占用本线程直到断开
            } catch (e: Exception) {
                // 发布到成员变量之前出错,本地句柄需就地关闭防泄漏
                try { ch?.close() } catch (_: Exception) {}
                try { conn?.close() } catch (_: Exception) {}
                onConnectFailed(gen, e.message ?: e.javaClass.simpleName, fatal = false)
            }
        }
    }

    /** 在连接线程上校验主机公钥;Unknown/Mismatch 时阻塞等待 UI 用户决定(带超时,超时按拒绝)。 */
    private fun verifyHostKey(gen: Int, hostname: String, port: Int, algorithm: String, key: ByteArray): Boolean {
        val ctx = SessionManager.appContext
        return when (val r = KnownHosts.check(ctx, hostname, port, algorithm, key)) {
            KnownHosts.CheckResult.Trusted -> true
            KnownHosts.CheckResult.Unknown, is KnownHosts.CheckResult.Mismatch -> {
                if (gen != generation || userClosed) return false
                val changed = r is KnownHosts.CheckResult.Mismatch
                val previous = (r as? KnownHosts.CheckResult.Mismatch)?.storedFingerprint
                val decision = SynchronousQueue<Boolean>()
                mainHandler.post {
                    hostKeyPrompt = HostKeyPrompt(
                        host = hostname, port = port, algorithm = algorithm,
                        fingerprint = KnownHosts.fingerprint(key),
                        changed = changed, previousFingerprint = previous,
                    ) { accepted ->
                        if (accepted) KnownHosts.save(ctx, hostname, port, algorithm, key)
                        hostKeyPrompt = null
                        decision.offer(accepted)
                    }
                }
                val accepted = decision.poll(HOST_KEY_PROMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: false
                if (!accepted) {
                    mainHandler.post { if (hostKeyPrompt != null) hostKeyPrompt = null }
                    postStatus(
                        if (changed) "\r\n[VibeTerm] 主机指纹已变更,连接被拒绝(谨防中间人攻击)。"
                        else "\r\n[VibeTerm] 未确认主机指纹,连接已取消。"
                    )
                }
                accepted
            }
        }
    }

    private fun resolveHostKeyPrompt(accepted: Boolean) {
        mainHandler.post { hostKeyPrompt?.onDecision?.invoke(accepted) }
    }

    private fun readLoop(gen: Int, myDisconnected: AtomicBoolean, stream: InputStream) {
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
        onDisconnected(gen, myDisconnected, null)
    }

    private fun startSecondaryReader(gen: Int, stdout: InputStream, stream: InputStream) {
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

    private fun startWriter(gen: Int, myDisconnected: AtomicBoolean, queue: ArrayBlockingQueue<ByteArray>, out: OutputStream) {
        thread(name = "ssh-writer-$gen", isDaemon = true) {
            try {
                while (gen == generation) {
                    val chunk = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    // poll 可能睡过了重连边界,写入前再次校验,避免把新按键写向旧连接
                    if (gen != generation) break
                    out.write(chunk)
                    out.flush()
                }
            } catch (e: Exception) {
                // 写失败(如断管)也代表连接已死,主动触发重连,不再空等读线程
                onDisconnected(gen, myDisconnected, e.message)
            }
        }
    }

    private fun startKeepAlive(gen: Int, myDisconnected: AtomicBoolean, conn: Connection) {
        thread(name = "ssh-keepalive-$gen", isDaemon = true) {
            try {
                while (gen == generation) {
                    Thread.sleep(KEEPALIVE_INTERVAL_MS)
                    if (gen != generation) return@thread
                    conn.sendIgnorePacket()
                }
            } catch (e: Exception) {
                onDisconnected(gen, myDisconnected, e.message)
            }
        }
    }

    private fun onDisconnected(gen: Int, myDisconnected: AtomicBoolean, message: String?) {
        if (userClosed || gen != generation) return
        if (!myDisconnected.compareAndSet(false, true)) return // 本代只处理一次
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
        // 连接稳定存活过 STABLE_MS 才把退避重置为基准;否则(连上即退/秒断)持续指数退避,
        // 避免 tmux 缺失 + shell 立即退出造成每秒重连的死循环空耗电量。
        val stable = connectedAt != 0L && SystemClock.elapsedRealtime() - connectedAt >= STABLE_MS
        if (stable) reconnectDelayMs = BASE_RECONNECT_MS
        val delay = reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_MS)
        connectedAt = 0L
        postStatus("\r\n[VibeTerm] ${delay / 1000} 秒后自动重连…")
        mainHandler.postDelayed({
            if (state == State.DISCONNECTED && SessionManager.appVisible && !userClosed) connect()
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
        private const val WRITE_QUEUE_CAPACITY = 4096
        private const val HOST_KEY_PROMPT_TIMEOUT_MS = 120_000L
        private const val BASE_RECONNECT_MS = 1_000L
        private const val MAX_RECONNECT_MS = 30_000L
        private const val STABLE_MS = 20_000L
        private const val BUSY_GAP_MS = 3_000L
        private const val BUSY_MIN_MS = 10_000L
        private const val SILENCE_MS = 8_000L
    }
}
