package dev.vibeterm.ssh

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dev.vibeterm.data.HostProfile
import dev.vibeterm.notify.Notifications
import dev.vibeterm.service.SshForegroundService

/** 进程级会话注册表:会话生命周期独立于 Activity。 */
@SuppressLint("StaticFieldLeak") // 只持有 applicationContext
object SessionManager {

    lateinit var appContext: Context
        private set

    val sessions = mutableStateListOf<SshTerminalSession>()

    var selected by mutableStateOf<SshTerminalSession?>(null)

    /** 内存中的密码缓存(仅用于“同主机再开窗口”),不落盘。 */
    private val passwordCache = HashMap<String, String>()

    var appVisible: Boolean = false
        set(value) {
            field = value
            if (value) sessions.forEach { it.reconnect() }
        }

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
    }

    /** 打开某主机的新窗口。窗口序号在该主机现有窗口基础上递增,tmux 会话名与之绑定。 */
    fun open(profile: HostProfile, password: String): SshTerminalSession {
        passwordCache[profile.id] = password
        val index = (sessions.filter { it.profile.id == profile.id }
            .maxOfOrNull { it.windowIndex } ?: 0) + 1
        val session = SshTerminalSession(profile, index, password, GlobalSessionClient)
        sessions.add(session)
        selected = session
        SshForegroundService.update(appContext, sessions.size)
        return session
    }

    /** 在与 [sibling] 相同的主机上再开一个窗口(复用内存中的密码)。 */
    fun openSibling(sibling: SshTerminalSession): SshTerminalSession? {
        val password = passwordCache[sibling.profile.id] ?: return null
        return open(sibling.profile, password)
    }

    fun close(session: SshTerminalSession) {
        session.finishIfRunning()
    }

    fun onFinished(session: SshTerminalSession) {
        val index = sessions.indexOf(session)
        sessions.remove(session)
        if (selected == session) {
            selected = sessions.getOrNull((index - 1).coerceAtLeast(0))
        }
        SshForegroundService.update(appContext, sessions.size)
    }

    fun findByHandle(handle: String): SshTerminalSession? =
        sessions.find { it.mHandle == handle }
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
