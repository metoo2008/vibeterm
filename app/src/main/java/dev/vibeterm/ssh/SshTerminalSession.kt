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
        val gen: Int,
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

    /** 单线程串行执行 resize;配合 [resizePending] 单槽合并,只保留最新尺寸。 */
    private val resizeExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "ssh-resize").apply { isDaemon = true } }
    private val resizePending = AtomicBoolean(false)

    /**
     * 本地状态提示专用线程:写入终端的输出队列在满时会阻塞等待主线程消费。若从主线程
     * (如网络切换回调、onTransportStart)直接写,就会「主线程等主线程」死锁。全部经此后台
     * 线程串行写入,保证既不阻塞调用方、又保持提示顺序。
     */
    private val statusExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "ssh-status").apply { isDaemon = true } }

    /** 保护 [generation] 与 [transport] 的原子读改写:切代与发布传输必须是一个不可分割的操作。 */
    private val lock = Any()

    @Volatile private var userClosed = false

    /** 重连代数:每次 connect() 递增,旧代的 IO 线程检测到不匹配即退出。仅在 [lock] 内修改。 */
    @Volatile private var generation = 0

    /** 当前世代的传输资源;仅在连接成功后赋值,断开/切代后置空。仅在 [lock] 内修改。 */
    @Volatile private var transport: Transport? = null

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
        // 单槽合并:已有一个待执行的 resize 时不再入队,任务运行时读最新的 cols/rows。
        // 这样旋转/分屏/软键盘动画期间即便 resizePTY 阻塞,任务也最多堆积一个。
        if (!resizePending.compareAndSet(false, true)) return
        try {
            resizeExecutor.execute {
                resizePending.set(false)
                val t = transport ?: return@execute
                if (t.gen != generation) return@execute
                try {
                    t.ch.resizePTY(this.cols, this.rows, 0, 0)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
            // executor 已关闭(会话结束):复位标记,忽略
            resizePending.set(false)
        }
    }

    override fun onTransportKill() {
        userClosed = true
        val gen: Int
        val dying: Transport?
        synchronized(lock) {
            gen = ++generation
            dying = transport
            transport = null
        }
        mainHandler.removeCallbacks(silenceChecker)
        resolveHostKeyPrompt(false)
        setState(gen, State.CLOSED, null)
        thread(name = "ssh-close", isDaemon = true) { if (dying != null) closeTransport(dying) }
        resizeExecutor.shutdownNow()
        statusExecutor.shutdown()
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
        // 切代与摘下旧 transport 必须原子:否则旧连接线程可能在新代 bump 之后才发布,覆盖新连接。
        val gen: Int
        val previous: Transport?
        synchronized(lock) {
            gen = ++generation
            previous = transport
            transport = null
        }
        // 唤醒可能仍在等待用户决定的旧世代指纹提示线程,避免线程堆积
        resolveHostKeyPrompt(false)
        if (previous != null) thread(name = "ssh-supersede", isDaemon = true) { closeTransport(previous) }
        setState(gen, State.CONNECTING, null)
        // 本次连接尝试独立的“指纹被拒”标记(不放会话全局,避免旧线程污染新连接的重连判定)
        val rejected = AtomicBoolean(false)
        thread(name = "ssh-connect-$gen") {
            var conn: Connection? = null
            var ch: SshChannel? = null
            try {
                conn = Connection(profile.host, profile.safePort)
                conn.connect({ hostname, port, algorithm, key ->
                    verifyHostKey(gen, rejected, hostname, port, algorithm, key)
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

                val t = Transport(gen, conn, ch, ch.stdin, ArrayBlockingQueue(WRITE_QUEUE_CAPACITY))
                t.connectedAt = SystemClock.elapsedRealtime()
                // 原子发布:校验仍是当前代 + 未关闭,同一临界区内赋值,杜绝被后续切代覆盖。
                val published = synchronized(lock) {
                    if (gen != generation || userClosed) false else { transport = t; true }
                }
                if (!published) { ch.close(); conn.close(); return@thread }
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
                onConnectFailed(gen, e.message ?: e.javaClass.simpleName, fatal = rejected.get())
            }
        }
    }

    /** 在连接线程上校验主机公钥;Unknown/Mismatch 时阻塞等待 UI 用户决定(带超时,超时按拒绝)。 */
    private fun verifyHostKey(gen: Int, rejected: AtomicBoolean, hostname: String, port: Int, algorithm: String, key: ByteArray): Boolean {
        val ctx = SessionManager.appContext
        return when (val r = KnownHosts.check(ctx, hostname, port, algorithm, key)) {
            KnownHosts.CheckResult.Trusted -> true
            KnownHosts.CheckResult.Unknown, is KnownHosts.CheckResult.Mismatch -> {
                if (gen != generation || userClosed) { rejected.set(true); return false }
                val changed = r is KnownHosts.CheckResult.Mismatch
                val previous = (r as? KnownHosts.CheckResult.Mismatch)?.storedFingerprint
                // 容量 1 的队列:即使决定先于 poll 到达也不会丢(SynchronousQueue 会丢)。
                val decision = ArrayBlockingQueue<Boolean>(1)
                val decided = AtomicBoolean(false)
                val holder = arrayOfNulls<HostKeyPrompt>(1)
                val prompt = HostKeyPrompt(
                    gen = gen, host = hostname, port = port, algorithm = algorithm,
                    fingerprint = KnownHosts.fingerprint(key),
                    changed = changed, previousFingerprint = previous,
                ) { accepted ->
                    // 幂等:按钮、超时唤醒、切代 resolveHostKeyPrompt 可能都调它,只认第一次。
                    if (!decided.compareAndSet(false, true)) return@HostKeyPrompt
                    // 信任必须以「成功落盘」为前提:保存失败则拒绝本次连接并提示,
                    // 否则会出现“看似已信任、实则未固定公钥”的静默不一致。
                    val effective = if (accepted) {
                        val saved = runCatching { KnownHosts.save(ctx, hostname, port, algorithm, key) }.isSuccess
                        if (!saved) postStatus("\r\n[VibeTerm] 主机指纹保存失败,连接已取消。")
                        saved
                    } else false
                    // 只清除“自己这一个”弹窗,避免误清新世代刚弹出的弹窗
                    mainHandler.post { if (hostKeyPrompt === holder[0]) hostKeyPrompt = null }
                    decision.offer(effective)
                }
                holder[0] = prompt
                // 只在仍是当前世代时展示;否则立即按拒绝处理,不干扰新世代
                mainHandler.post {
                    if (gen == generation && !userClosed) hostKeyPrompt = prompt else prompt.onDecision(false)
                }
                val accepted = decision.poll(HOST_KEY_PROMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    ?: run { prompt.onDecision(false); false } // 超时:唤醒并按拒绝
                if (!accepted) {
                    rejected.set(true)
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
        synchronized(lock) { if (transport === t) transport = null }
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

    /** 向本地终端(不经远端)追加提示文本。经后台线程写入,绝不阻塞调用方(见 statusExecutor 说明)。 */
    private fun postStatus(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        try {
            statusExecutor.execute { onTransportData(bytes, 0, bytes.size) }
        } catch (_: Exception) {
            // executor 已关闭(会话终结),忽略
        }
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
