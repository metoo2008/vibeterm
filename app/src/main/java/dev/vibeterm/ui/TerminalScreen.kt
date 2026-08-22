package dev.vibeterm.ui

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.vibeterm.ssh.SessionManager
import dev.vibeterm.ssh.SshTerminalSession
import dev.vibeterm.ui.theme.TermGray
import dev.vibeterm.ui.theme.TermGreen
import dev.vibeterm.ui.theme.TermRed
import dev.vibeterm.ui.theme.TermYellow

@Composable
fun TerminalScreen(onShowHosts: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sessions = SessionManager.sessions
    val selected = SessionManager.selected

    LaunchedEffect(sessions.size) {
        if (sessions.isEmpty()) {
            onShowHosts()
        } else if (SessionManager.selected == null || SessionManager.selected !in sessions) {
            SessionManager.selected = sessions.firstOrNull()
        }
    }
    if (selected == null || sessions.isEmpty()) return

    val viewClient = remember { VtViewClient() }
    var fontSp by remember { mutableIntStateOf(14) }
    var appliedFontSp by remember { mutableIntStateOf(14) }
    viewClient.onChangeFontSize = { increase ->
        fontSp = (fontSp + if (increase) 1 else -1).coerceIn(8, 32)
    }

    fun sendBarKey(key: BarKey) {
        val session = SessionManager.selected ?: return
        when (key) {
            BarKey.ESC -> session.write("\u001b")
            BarKey.TAB -> session.write("\t")
            BarKey.SHIFT_TAB -> session.write("\u001b[Z")
            BarKey.CTRL -> viewClient.ctrl.value = !viewClient.ctrl.value
            BarKey.ALT -> viewClient.alt.value = !viewClient.alt.value
            BarKey.DASH -> session.write("-")
            BarKey.SLASH -> session.write("/")
            BarKey.PIPE -> session.write("|")
            BarKey.UP -> sendKeyCode(session, KeyEvent.KEYCODE_DPAD_UP)
            BarKey.DOWN -> sendKeyCode(session, KeyEvent.KEYCODE_DPAD_DOWN)
            BarKey.LEFT -> sendKeyCode(session, KeyEvent.KEYCODE_DPAD_LEFT)
            BarKey.RIGHT -> sendKeyCode(session, KeyEvent.KEYCODE_DPAD_RIGHT)
            BarKey.PGUP -> sendKeyCode(session, KeyEvent.KEYCODE_PAGE_UP)
            BarKey.PGDN -> sendKeyCode(session, KeyEvent.KEYCODE_PAGE_DOWN)
            BarKey.PASTE -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!text.isNullOrEmpty()) session.emulator?.paste(text)
            }
            BarKey.KEYBOARD -> viewClient.view?.let { showSoftKeyboard(it) }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶栏:主机列表入口 + 会话标签 + 新窗口
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        ) {
            IconButton(onClick = onShowHosts) {
                Icon(Icons.Filled.Menu, contentDescription = "主机列表")
            }
            ScrollableTabRow(
                selectedTabIndex = sessions.indexOf(selected).coerceAtLeast(0),
                modifier = Modifier.weight(1f),
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {},
                indicator = {},
            ) {
                sessions.forEach { session ->
                    val isSelected = session == selected
                    Tab(selected = isSelected, onClick = { SessionManager.selected = session }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        ) {
                            StatusDot(session.state)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                tabTitle(session),
                                maxLines = 1,
                                fontSize = 13.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else TermGray,
                            )
                            if (isSelected) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "关闭窗口",
                                    modifier = Modifier.size(14.dp).clickable { SessionManager.close(session) },
                                    tint = TermGray,
                                )
                            }
                        }
                    }
                }
            }
            IconButton(onClick = {
                if (SessionManager.openSibling(selected) == null) {
                    Toast.makeText(context, "内存中没有该主机的密码,请从主机列表打开新窗口", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = "新窗口")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setTerminalViewClient(viewClient)
                        setTextSize(with(density) { fontSp.sp.toPx() }.toInt())
                        viewClient.view = this
                    }
                },
                update = { view ->
                    if (appliedFontSp != fontSp) {
                        view.setTextSize(with(density) { fontSp.sp.toPx() }.toInt())
                        appliedFontSp = fontSp
                    }
                    val current = SessionManager.selected ?: return@AndroidView
                    if (view.currentSession !== current) {
                        (view.currentSession as? SshTerminalSession)?.attachedView = null
                        current.attachedView = view
                        view.attachSession(current)
                        view.onScreenUpdated()
                        view.requestFocus()
                    }
                },
            )

            when (selected.state) {
                SshTerminalSession.State.CONNECTING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
                SshTerminalSession.State.DISCONNECTED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("连接已断开", color = TermRed, fontSize = 13.sp)
                        TextButton(onClick = { selected.reconnect() }) { Text("立即重连") }
                    }
                }
                else -> {}
            }
        }

        ExtraKeysBar(
            ctrlActive = viewClient.ctrl.value,
            altActive = viewClient.alt.value,
            onKey = ::sendBarKey,
            onKeyLongPress = { key ->
                if (key == BarKey.KEYBOARD) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                }
            },
        )
    }
}

private fun tabTitle(session: SshTerminalSession): String {
    val base = session.profile.label.ifBlank { session.profile.host }
    return "$base·${session.windowIndex}"
}

private fun sendKeyCode(session: SshTerminalSession, keyCode: Int) {
    val emulator = session.emulator ?: return
    KeyHandler.getCode(
        keyCode, 0,
        emulator.isCursorKeysApplicationMode,
        emulator.isKeypadApplicationMode,
    )?.let { session.write(it) }
}

private fun showSoftKeyboard(view: View) {
    view.requestFocus()
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(view, 0)
}

@Composable
private fun StatusDot(state: SshTerminalSession.State) {
    val color = when (state) {
        SshTerminalSession.State.CONNECTED -> TermGreen
        SshTerminalSession.State.CONNECTING -> TermYellow
        SshTerminalSession.State.DISCONNECTED -> TermRed
        SshTerminalSession.State.CLOSED -> TermGray
    }
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** TerminalView 的客户端实现:闩锁 Ctrl/Alt、双指缩放调字号、单击唤起输入法。 */
class VtViewClient : TerminalViewClient {

    val ctrl = mutableStateOf(false)
    val alt = mutableStateOf(false)
    var view: TerminalView? = null
    var onChangeFontSize: ((increase: Boolean) -> Unit)? = null

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            onChangeFontSize?.invoke(scale > 1f)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        view?.let { showSoftKeyboard(it) }
    }

    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        // Ctrl+空格 = 硬件键盘切输入法的系统惯例(TerminalView 在 onKeyPreIme 抢在输入法之前转发过来)。
        // 终端语义下它本是 NUL,极少用,需要时按 Ctrl+2。
        if (keyCode == KeyEvent.KEYCODE_SPACE && e.isCtrlPressed) {
            val v = view ?: return false
            val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
            return true
        }
        return false
    }
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = ctrl.value
    override fun readAltKey(): Boolean = alt.value
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // 闩锁键一次性生效:字符发出后自动松开
        if (ctrl.value) ctrl.value = false
        if (alt.value) alt.value = false
        return false
    }

    override fun onEmulatorSet() {}

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message ?: "", e)
    }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "", e) }

    companion object {
        private const val TAG = "VibeTerm"
    }
}
