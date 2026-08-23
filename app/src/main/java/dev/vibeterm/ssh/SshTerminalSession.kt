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
 * 生命周期:emulator 只创建一次(保留滚回历史);传输层可多次断开/重连。每次重连是一个独立的
 * [Transport] 世代,generation 递增。所有针对连接/通道的关闭与状态更新都只作用于「自己那一代」的
 * Transport 对象,并在主线程再次校验 generation —— 杜绝旧世代线程误关新连接、误写旧状态的竞态。
 * 只有用户主动关闭才走 onTransportExited 终结整个会话。
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

    /** 一个连接世代的全部可变资源;关闭时只碰本对象,不触碰其他世代。 */
    private class Transport(
        val gen: Int,
        val conn: Connection,
        val ch: SshChannel,
        val stdin: OutputStream,
        val queue: ArrayBlockingQueue<ByteArray>,
    ) {
        val disconnected = AtomicBoolean(false)
        @Volatile var connectedAt = 0L
    }

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

    @Volatile private var userClosed = false

    /** 重连代数:每次 connect() 递增,旧代的 IO 线程检测到不匹配即退出。 */
    @Volatile private var generation = 0

    /** 当前世代的传输资源;仅在连接成功后赋值,断开/切代后置空。 */
    @Volatile private var transport: Transport? = null

    /** 本次连接尝试是否因用户拒绝主机指纹而失败 —— 若是则禁止自动重连。 */
    @Volatile private var hostKeyRejected = false

    @Volatile private var cols = 80
    @Volatile private var rows = 24
    @Volatile private var reconnectDelayMs = BASE_RECONNECT_MS

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
        if (state != State.CONNECTED) return
        val t = transport ?: return
        val chunk = data.copyOfRange(offset, offset + count)
        // 有界队列。满则拒绝「新」输入(绝不动已入队字节,避免把已排队命令改坏),并提示。
        // 队列满通常意味着远端已不再读取(连接卡死),读线程会随后触发重连。
        if (!t.queue.offer(chunk)) {
            postStatus("\r\n[VibeTerm] 输入缓冲已满,连接可能已卡住,输入被丢弃。")
        }
    }

    override fun onTransportResize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.cols = columns
        this.rows = rows
        val t = transport ?: return
        try {
            resizeExecutor.execute {
                if (t.gen != generation) return@execute
                try {
                    t.ch.resizePTY(this.cols, this.rows, 0, 0)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
            // executor 已关闭(会话结束),忽略
        }
    }

    override fun onTransportKill() {
        userClosed = true
        val gen = ++generation
        val dying = transport
        transport = null
        mainHandler.removeCallbacks(silenceChecker)
        resolveHostKeyPrompt(false)
        setState(gen, State.CLOSED, null)
        thread(name = "ssh-close", isDaemon = true) { if (dying != null) closeTransport(dying) }
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
                postStatus("\r\n[VibeTerm] 网络已切换,正在重连…")
                connect() // connect() 会切代并关闭旧 transport
            }
            State.DISCONNECTED -> connect()
            State.CLOSED -> {}
        }
    }

    private fun connect() {
        if (userClosed) return
        val gen = ++generation
        hostKeyRejected = false
        // 唤醒可能仍在等待用户决定的旧世代指纹提示线程,避免线程堆积
        resolveHostKeyPrompt(false)
        // 立即接管旧世代:置空全局引用并异步关闭,旧线程 gen 检查失败后自然退出
        val previous = transport
        transport = null
        if (previous != null) thread(name = "ssh-supersede", isDaemon = true) { closeTransport(previous) }
        setState(gen, State.CONNECTING, null)
        thread(name = "ssh-connect-$gen") {
            var conn: Connection? = null
            var ch: SshChannel? = null
            try {
                conn = Connection(profile.host, profile.safePort)
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
                val t = Transport(gen, conn, ch, ch.stdin, ArrayBlockingQueue(WRITE_QUEUE_CAPACITY))
                t.connectedAt = SystemClock.elapsedRealtime()
                transport = t
                setState(gen, State.CONNECTED, null)

                startWriter(t)
                startKeepAlive(t)
                startSecondaryReader(t, ch.stderr)
                readLoop(t, ch.stdout) // 占用本线程直到断开
            } catch (e: Exception) {
                // 发布到 transport 之前出错,本地句柄需就地关闭防泄漏
                try { ch?.close() } catch (_: Exception) {}
                try { conn?.close() } catch (_: Exception) {}
                // 用户拒绝指纹(或指纹异常)属致命,禁止自动重连;仅网络类失败才退避重试
                onConnectFailed(gen, e.message ?: e.javaClass.simpleName, fatal = hostKeyRejected)
            }
        }
    }

    /** 在连接线程上校验主机公钥;Unknown/Mismatch 时阻塞等待 UI 用户决定(带超时,超时按拒绝)。 */
    private fun verifyHostKey(gen: Int, hostname: String, port: Int, algorithm: String, key: ByteArray): Boolean {
        val ctx = SessionManager.appContext
        return when (val r = KnownHosts.check(ctx, hostname, port, algorithm, key)) {
            KnownHosts.CheckResult.Trusted -> true
            KnownHosts.CheckResult.Unknown, is KnownHosts.CheckResult.Mismatch -> {
                if (gen != generation || userClosed) { hostKeyRejected = true; return false }
                val changed = r is KnownHosts.CheckResult.Mismatch
                val previous = (r as? KnownHosts.CheckResult.Mismatch)?.storedFingerprint
                val decision = SynchronousQueue<Boolean>()
                mainHandler.post {
                    hostKeyPrompt = HostKeyPrompt(
                        host = hostname, port = port, algorithm = algorithm,
                        fingerprint = KnownHosts.fingerprint(key),
                        changed = changed, previousFingerprint = previous,
                    ) { accepted ->
                        // save 失败也要放行决定,避免连接线程一直卡在 poll
                        if (accepted) runCatching { KnownHosts.save(ctx, hostname, port, algorithm, key) }
                        hostKeyPrompt = null
                        decision.offer(accepted)
                    }
                }
                val accepted = decision.poll(HOST_KEY_PROMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: false
                if (!accepted) {
                    hostKeyRejected = true
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

    private fun readLoop(t: Transport, stream: InputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                if (t.gen != generation) return
                onOutputActivity()
                onTransportData(buffer, 0, read)
            }
        } catch (_: Exception) {
        }
        onDisconnected(t, null)
    }

    private fun startSecondaryReader(t: Transport, stream: InputStream) {
        thread(name = "ssh-stderr-${t.gen}", isDaemon = true) {
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1 || t.gen != generation) return@thread
                    onTransportData(buffer, 0, read)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startWriter(t: Transport) {
        thread(name = "ssh-writer-${t.gen}", isDaemon = true) {
            try {
                while (t.gen == generation) {
                    val chunk = t.queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    // poll 可能睡过了重连边界,写入前再次校验,避免把新按键写向旧连接
                    if (t.gen != generation) break
                    t.stdin.write(chunk)
                    t.stdin.flush()
                }
            } catch (e: Exception) {
                // 写失败(如断管)也代表连接已死,主动触发重连,不再空等读线程
                onDisconnected(t, e.message)
            }
        }
    }

    private fun startKeepAlive(t: Transport) {
        thread(name = "ssh-keepalive-${t.gen}", isDaemon = true) {
            try {
                while (t.gen == generation) {
                    Thread.sleep(KEEPALIVE_INTERVAL_MS)
                    if (t.gen != generation) return@thread
                    t.conn.sendIgnorePacket()
                }
            } catch (e: Exception) {
                onDisconnected(t, e.message)
            }
        }
    }

    private fun onDisconnected(t: Transport, message: String?) {
        if (userClosed || t.gen != generation) return
        if (!t.disconnected.compareAndSet(false, true)) return // 本代只处理一次
        closeTransport(t)
        if (transport === t) transport = null
        postStatus("\r\n[VibeTerm] 连接已断开${if (message != null) ":$message" else ""}")
        setState(t.gen, State.DISCONNECTED, message)
        scheduleReconnect(t)
    }

    private fun onConnectFailed(gen: Int, message: String, fatal: Boolean) {
        if (userClosed || gen != generation) return
        postStatus("\r\n[VibeTerm] 连接失败:$message")
        setState(gen, State.DISCONNECTED, message)
        if (!fatal) scheduleReconnect(null)
    }

    private fun scheduleReconnect(t: Transport?) {
        if (userClosed || !SessionManager.appVisible) return // 后台不重连(tmux 兜底),回前台统一触发
        // 连接稳定存活过 STABLE_MS 才把退避重置为基准;否则(连上即退/秒断)持续指数退避,
        // 避免 tmux 缺失 + shell 立即退出造成每秒重连的死循环空耗电量。
        val connectedAt = t?.connectedAt ?: 0L
        val stable = connectedAt != 0L && SystemClock.elapsedRealtime() - connectedAt >= STABLE_MS
        if (stable) reconnectDelayMs = BASE_RECONNECT_MS
        val delay = reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_MS)
        postStatus("\r\n[VibeTerm] ${delay / 1000} 秒后自动重连…")
        mainHandler.postDelayed({
            if (state == State.DISCONNECTED && SessionManager.appVisible && !userClosed) connect()
        }, delay)
    }

    private fun closeTransport(t: Transport) {
        try { t.ch.close() } catch (_: Exception) {}
        try { t.conn.close() } catch (_: Exception) {}
    }

    /** 向本地终端(不经远端)追加提示文本。 */
    private fun postStatus(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        onTransportData(bytes, 0, bytes.size)
    }

    /** 只有仍是当前世代(或会话终结)时才应用状态,防止旧世代覆盖新世代状态。 */
    private fun setState(gen: Int, s: State, message: String?) {
        mainHandler.post {
            if (gen != generation && s != State.CLOSED) return@post
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
        private const val HOST_KEY_PROMPT_TIMEOUT_MS = 90_000L
        // KEX 超时必须大于指纹确认超时:主机公钥校验发生在密钥交换期间,若用户在指纹弹窗上
        // 的思考时间超过 KEX 超时,连接会在用户点「信任」之前就被 sshlib 判死。留 30s 加密余量。
        private const val KEX_TIMEOUT_MS = (HOST_KEY_PROMPT_TIMEOUT_MS + 30_000L).toInt()
        private const val KEEPALIVE_INTERVAL_MS = 15_000L
        private const val WRITE_QUEUE_CAPACITY = 4096
        private const val BASE_RECONNECT_MS = 1_000L
        private const val MAX_RECONNECT_MS = 30_000L
        private const val STABLE_MS = 20_000L
        private const val BUSY_GAP_MS = 3_000L
        private const val BUSY_MIN_MS = 10_000L
        private const val SILENCE_MS = 8_000L
    }
}
