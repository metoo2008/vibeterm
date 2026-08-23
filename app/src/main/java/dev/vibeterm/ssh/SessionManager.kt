package dev.vibeterm.ssh

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dev.vibeterm.data.HostProfile
import dev.vibeterm.data.HostStore
import dev.vibeterm.data.SecureStore
import dev.vibeterm.data.writeTextAtomic
import dev.vibeterm.notify.Notifications
import dev.vibeterm.service.SshForegroundService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 进程级会话注册表:会话生命周期独立于 Activity。 */
@SuppressLint("StaticFieldLeak") // 只持有 applicationContext
object SessionManager {

    lateinit var appContext: Context
        private set

    val sessions = mutableStateListOf<SshTerminalSession>()

    var selected by mutableStateOf<SshTerminalSession?>(null)

    /** 内存中的密码缓存(仅用于“同主机再开窗口”),不落盘。 */
    private val passwordCache = HashMap<String, String>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastNetworkHandle: Long? = null

    var appVisible: Boolean = false
        set(value) {
            field = value
            if (value) sessions.forEach { it.reconnect() }
        }

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        registerNetworkCallback()
    }

    /** 换网(WiFi↔流量)时立即重建连接,不等 keepalive 超时——tmux -A 保证无缝回到原会话。 */
    private fun registerNetworkCallback() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val handle = network.networkHandle
                    mainHandler.post {
                        val previous = lastNetworkHandle
                        lastNetworkHandle = handle
                        when {
                            previous == null -> sessions.forEach { it.reconnect() }
                            previous != handle -> sessions.forEach { it.onNetworkChanged() }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.w("VibeTerm", "registerDefaultNetworkCallback failed", e)
        }
    }

    /** 打开某主机的新窗口。窗口序号绑定 tmux 会话名;不传则在该主机现有窗口基础上递增。 */
    fun open(profile: HostProfile, password: String, windowIndex: Int? = null): SshTerminalSession {
        passwordCache[profile.id] = password
        val index = windowIndex
            ?: ((sessions.filter { it.profile.id == profile.id }.maxOfOrNull { it.windowIndex } ?: 0) + 1)
        val session = SshTerminalSession(profile, index, password, GlobalSessionClient)
        sessions.add(session)
        selected = session
        SshForegroundService.update(appContext, sessions.size)
        persistWindows()
        return session
    }

    /** 在与 [sibling] 相同的主机上再开一个窗口(内存密码优先,加密存储兜底)。 */
    fun openSibling(sibling: SshTerminalSession): SshTerminalSession? {
        val password = passwordCache[sibling.profile.id]
            ?: SecureStore.getPassword(appContext, sibling.profile.id)
            ?: return null
        return open(sibling.profile, password)
    }

    /** 用户显式关闭窗口:杀传输并从列表移除。 */
    fun close(session: SshTerminalSession) {
        session.finishIfRunning()
        remove(session)
    }

    private fun remove(session: SshTerminalSession) {
        val index = sessions.indexOf(session)
        if (index < 0) return
        sessions.remove(session)
        if (selected == session) {
            selected = sessions.getOrNull((index - 1).coerceAtLeast(0))
        }
        // 该主机已无任何窗口时,清除内存中的明文密码,不让它驻留到进程结束
        if (sessions.none { it.profile.id == session.profile.id }) {
            passwordCache.remove(session.profile.id)
        }
        SshForegroundService.update(appContext, sessions.size)
        persistWindows()
    }

    /** 删除主机时清理其内存密码缓存(加密存储的清除由调用方负责)。 */
    fun forgetPassword(hostId: String) {
        passwordCache.remove(hostId)
    }

    /**
     * 会话自行结束(非用户显式关闭)时不移除:保留终端画面与「已结束」状态,
     * 让用户看清最后输出,点 ✕ 才移除。
     */
    fun onFinished(session: SshTerminalSession) {
        // 故意留空
    }

    fun findByHandle(handle: String): SshTerminalSession? =
        sessions.find { it.mHandle == handle }

    // ------------------------------------------------------------------
    // 上次窗口持久化与冷启动恢复
    // ------------------------------------------------------------------

    private fun windowsFile() = File(appContext.filesDir, "last_windows.json")

    private fun persistWindows() {
        try {
            val arr = JSONArray()
            sessions.forEach { s ->
                arr.put(JSONObject().put("hostId", s.profile.id).put("index", s.windowIndex))
            }
            windowsFile().writeTextAtomic(arr.toString())
        } catch (e: Exception) {
            Log.w("VibeTerm", "persistWindows failed", e)
        }
    }

    /** 冷启动恢复上次打开的窗口(只恢复存有密码的主机)。返回恢复数量。 */
    fun restoreWindows(): Int {
        if (sessions.isNotEmpty()) return 0
        val f = windowsFile()
        if (!f.exists()) return 0
        var restored = 0
        try {
            val hostsById = HostStore.load(appContext).associateBy { it.id }
            val arr = JSONArray(f.readText())
            val entries = (0 until arr.length()).map { arr.getJSONObject(it) }.sortedBy { it.optInt("index") }
            for (entry in entries) {
                val profile = hostsById[entry.optString("hostId")] ?: continue
                val password = SecureStore.getPassword(appContext, profile.id) ?: continue
                open(profile, password, entry.optInt("index"))
                restored++
            }
        } catch (_: Exception) {
        }
        if (restored > 0) selected = sessions.firstOrNull()
        return restored
    }
}

/**
 * 全局 TerminalSessionClient:回调里带会话参数,单例即可服务所有会话。
 */
object GlobalSessionClient : TerminalSessionClient {

    private const val TAG = "VibeTerm"

    override fun onTextChanged(changedSession: TerminalSession) {
        (changedSession as? SshTerminalSession)?.attachedView?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        (changedSession as? SshTerminalSession)?.let { it.terminalTitle = changedSession.title.orEmpty() }
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        (finishedSession as? SshTerminalSession)?.let { SessionManager.onFinished(it) }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        val cm = SessionManager.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("VibeTerm", text ?: ""))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val cm = SessionManager.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(SessionManager.appContext)?.toString()
        if (!text.isNullOrEmpty()) session?.emulator?.paste(text)
    }

    override fun onBell(session: TerminalSession) {
        (session as? SshTerminalSession)?.let { Notifications.onBell(it) }
    }

    override fun onColorsChanged(changedSession: TerminalSession) {
        (changedSession as? SshTerminalSession)?.attachedView?.invalidate()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message ?: "", e)
    }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "", e) }
}
