package dev.vibeterm.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.vibeterm.data.Prefs
import dev.vibeterm.ssh.SessionManager
import dev.vibeterm.ssh.SshTerminalSession
import dev.vibeterm.ui.theme.TermGray
import dev.vibeterm.ui.theme.TermGreen
import dev.vibeterm.ui.theme.TermRed
import dev.vibeterm.ui.theme.TermYellow

@Composable
fun TerminalScreen(onShowHosts: () -> Unit) {
    val context = LocalContext.current
    val sessions = SessionManager.sessions
    val isWide = LocalConfiguration.current.screenWidthDp >= 840

    var split by remember { mutableStateOf(false) }
    var leftSession by remember { mutableStateOf(SessionManager.selected) }
    var rightSession by remember { mutableStateOf<SshTerminalSession?>(null) }
    var focusedPane by remember { mutableIntStateOf(0) }

    val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var fontSp by remember { mutableIntStateOf(prefs.getInt("fontSp", 14)) }
    LaunchedEffect(fontSp) { prefs.edit().putInt("fontSp", fontSp).apply() }

    val termTypeface = remember {
        Typeface.createFromAsset(context.assets, "fonts/JetBrainsMonoNL-Regular.ttf")
    }

    var showCommands by remember { mutableStateOf(false) }

    val leftClient = remember { VtViewClient() }
    val rightClient = remember { VtViewClient() }

    fun focusedClient() = if (split && focusedPane == 1) rightClient else leftClient
    fun focusedSession() = (if (split && focusedPane == 1) rightSession else leftSession) ?: leftSession

    // 会话增删时校正面板绑定
    LaunchedEffect(sessions.size) {
        if (sessions.isEmpty()) {
            onShowHosts()
            return@LaunchedEffect
        }
        if (leftSession == null || leftSession !in sessions) leftSession = sessions.firstOrNull()
        if (rightSession != null && rightSession !in sessions) {
            rightSession = sessions.firstOrNull { it != leftSession }
            if (rightSession == null) {
                split = false
                focusedPane = 0
            }
        }
        SessionManager.selected = focusedSession()
    }

    // 外部选中变化(通知点击/新开会话)进入焦点面板
    val externallySelected = SessionManager.selected
    LaunchedEffect(externallySelected) {
        if (externallySelected != null && externallySelected in sessions &&
            externallySelected != leftSession && externallySelected != rightSession
        ) {
            if (split && focusedPane == 1) rightSession = externallySelected else leftSession = externallySelected
        }
    }

    leftClient.onChangeFontSize = { increase -> fontSp = (fontSp + if (increase) 1 else -1).coerceIn(8, 32) }
    rightClient.onChangeFontSize = leftClient.onChangeFontSize
    leftClient.onFocusGained = {
        focusedPane = 0
        SessionManager.selected = leftSession
    }
    rightClient.onFocusGained = {
        focusedPane = 1
        SessionManager.selected = rightSession
    }
    val imeSwitch = {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }
    leftClient.onImeSwitchRequested = imeSwitch
    rightClient.onImeSwitchRequested = imeSwitch

    fun sendBarKey(key: BarKey) {
        val client = focusedClient()
        val session = focusedSession() ?: return
        when (key) {
            BarKey.ESC -> session.write("\u001b")
            BarKey.TAB -> session.write("\t")
            BarKey.SHIFT_TAB -> session.write("\u001b[Z")
            BarKey.CTRL -> client.ctrl.value = !client.ctrl.value
            BarKey.ALT -> client.alt.value = !client.alt.value
            BarKey.DASH -> session.write("-")
            BarKey.SLASH -> session.write("/")
            BarKey.PIPE -> session.write("|")
            BarKey.UP -> sendKeyCode(session, KeyEvent.KEYCODE_DPAD_UP)
            BarKey.DOWN -> sendKeyCode(session, KeyEvent.KEYCODE_DPAD_DOWN)
            BarKey.LEFT -> sendKeyCode(session, KeyEvent.KEYCODE_DPAD_LEFT)
            BarKey.RIGHT -> sendKeyCode(session, KeyEvent.KEYCODE_DPAD_RIGHT)
            BarKey.PGUP -> sendKeyCode(session, KeyEvent.KEYCODE_PAGE_UP)
            BarKey.PGDN -> sendKeyCode(session, KeyEvent.KEYCODE_PAGE_DOWN)
            BarKey.CMDS -> showCommands = true
            BarKey.PASTE -> pasteFromClipboard(context, session)
            BarKey.KEYBOARD -> client.view?.let { showSoftKeyboard(it) }
        }
    }

    val left = leftSession ?: return

    // edge-to-edge:系统栏(状态栏/导航栏)内边距 + 输入法内边距,避免顶栏/键条被系统栏遮挡
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        // 顶栏:主机列表入口 + 会话标签 + 分屏 + 新窗口
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        ) {
            // 顶栏所有控件禁止获得硬件键盘焦点:终端界面里实体键盘专属终端,
            // 否则 TerminalView 一旦失焦,回车会“点击”第一个可聚焦按钮(曾导致按回车退出终端)
            IconButton(
                onClick = onShowHosts,
                modifier = Modifier.focusProperties { canFocus = false },
            ) {
                Icon(Icons.Filled.Menu, contentDescription = "主机列表")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            ) {
                sessions.forEach { session ->
                    SessionChip(
                        session = session,
                        focused = session == focusedSession(),
                        onScreen = session == leftSession || (split && session == rightSession),
                        onClick = {
                            if (split && focusedPane == 1) rightSession = session else leftSession = session
                            SessionManager.selected = session
                        },
                        onClose = { SessionManager.close(session) },
                    )
                }
            }
            if (isWide) {
                TextButton(
                    modifier = Modifier.focusProperties { canFocus = false },
                    onClick = {
                    if (!split) {
                        val other = sessions.firstOrNull { it != left } ?: SessionManager.openSibling(left)
                        if (other == null) {
                            Toast.makeText(context, "没有可用的第二个会话(缺少该主机密码)", Toast.LENGTH_SHORT).show()
                        } else {
                            rightSession = other
                            split = true
                            focusedPane = 0
                            SessionManager.selected = left
                        }
                    } else {
                        rightSession = null
                        split = false
                        focusedPane = 0
                        SessionManager.selected = leftSession
                    }
                }) { Text(if (split) "并屏" else "分屏") }
            }
            IconButton(
                modifier = Modifier.focusProperties { canFocus = false },
                onClick = {
                    if (SessionManager.openSibling(focusedSession() ?: left) == null) {
                        Toast.makeText(context, "内存中没有该主机的密码,请从主机列表打开新窗口", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新窗口")
            }
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            TerminalPane(
                session = left,
                viewClient = leftClient,
                fontSp = fontSp,
                typeface = termTypeface,
                focused = !split || focusedPane == 0,
                showBorder = split,
                modifier = Modifier.weight(1f),
            )
            val right = rightSession
            if (split && right != null) {
                TerminalPane(
                    session = right,
                    viewClient = rightClient,
                    fontSp = fontSp,
                    typeface = termTypeface,
                    focused = focusedPane == 1,
                    showBorder = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ExtraKeysBar(
            ctrlActive = focusedClient().ctrl.value,
            altActive = focusedClient().alt.value,
            onKey = ::sendBarKey,
            onKeyLongPress = { key -> if (key == BarKey.KEYBOARD) imeSwitch() },
        )
    }

    if (showCommands) {
        QuickCommandsDialog(
            onDismiss = { showCommands = false },
            onSend = { command ->
                focusedSession()?.write(command + "\r")
                showCommands = false
            },
        )
    }
}

/** 快捷命令面板:一键发送常用命令(手机上敲 claude -c 这类最费劲),可增删自定义。 */
@Composable
private fun QuickCommandsDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    val context = LocalContext.current
    val commands = remember { mutableStateListOf<String>().apply { addAll(Prefs.quickCommands(context)) } }
    var newCommand by remember { mutableStateOf("") }

    fun persist() = Prefs.setQuickCommands(context, commands.toList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快捷命令") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                commands.forEach { command ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSend(command) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(
                            command,
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "删除",
                            modifier = Modifier.size(16.dp).clickable {
                                commands.remove(command)
                                persist()
                            },
                            tint = TermGray,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newCommand,
                        { newCommand = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("新命令") },
                        singleLine = true,
                    )
                    TextButton(
                        enabled = newCommand.isNotBlank(),
                        onClick = {
                            commands.add(newCommand.trim())
                            persist()
                            newCommand = ""
                        },
                    ) { Text("添加") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 单个终端面板:AndroidView(TerminalView) + 连接状态覆盖层。 */
@Composable
private fun TerminalPane(
    session: SshTerminalSession,
    viewClient: VtViewClient,
    fontSp: Int,
    typeface: Typeface,
    focused: Boolean,
    showBorder: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var appliedFontSp by remember { mutableIntStateOf(0) }
    val borderColor = when {
        !showBorder -> Color.Transparent
        focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(modifier.fillMaxHeight().border(1.dp, borderColor)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setTerminalViewClient(viewClient)
                    setTextSize(with(density) { fontSp.sp.toPx() }.toInt())
                    setTypeface(typeface)
                    viewClient.view = this
                    // 组合期 view 尚未挂进窗口,requestFocus 会静默失败;post 到 attach 之后执行。
                    // 曾导致:TerminalView 失焦 → 硬件回车被焦点系统交给顶栏按钮 → “按回车退出终端”
                    post { requestFocus() }
                }
            },
            update = { view ->
                if (appliedFontSp != fontSp) {
                    view.setTextSize(with(density) { fontSp.sp.toPx() }.toInt())
                    appliedFontSp = fontSp
                }
                if (view.currentSession !== session) {
                    (view.currentSession as? SshTerminalSession)?.let {
                        if (it.attachedView === view) it.attachedView = null
                    }
                    session.attachedView = view
                    view.attachSession(session)
                    view.onScreenUpdated()
                }
                // 焦点兜底:每次重组都确保焦点面板真正持有 Android 焦点
                if (focused && !view.hasFocus()) view.requestFocus()
            },
        )

        session.hostKeyPrompt?.let { prompt ->
            HostKeyDialog(prompt)
        }

        when (session.state) {
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
                    TextButton(
                        onClick = { session.reconnect() },
                        modifier = Modifier.focusProperties { canFocus = false },
                    ) { Text("立即重连") }
                }
            }
            SshTerminalSession.State.CLOSED -> {
                // 会话已结束:保留画面让用户看清最后输出,手动关才移除
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("会话已结束", color = TermGray, fontSize = 13.sp)
                    TextButton(
                        onClick = { SessionManager.close(session) },
                        modifier = Modifier.focusProperties { canFocus = false },
                    ) { Text("关闭窗口") }
                }
            }
            else -> {}
        }
    }

    DisposableEffect(session) {
        onDispose {
            if (session.attachedView === viewClient.view) session.attachedView = null
        }
    }
}

/** 有界读取剪贴板并粘贴到会话;非纯文本/超限时给出提示,不发送残缺内容。 */
private fun pasteFromClipboard(context: Context, session: SshTerminalSession) {
    when (val r = Clipboard.read(context)) {
        is Clipboard.Result.Text -> session.emulator?.paste(r.text)
        Clipboard.Result.Empty -> {}
        Clipboard.Result.Unsupported ->
            Toast.makeText(context, "剪贴板不是纯文本,已忽略", Toast.LENGTH_SHORT).show()
        Clipboard.Result.TooLarge ->
            Toast.makeText(context, "剪贴板内容过大(超过 100 万字符),已拒绝粘贴", Toast.LENGTH_SHORT).show()
    }
}

private fun tabTitle(session: SshTerminalSession): String {
    val base = session.profile.label.ifBlank { session.profile.host }
    return "$base·${session.windowIndex}"
}

/** 主机公钥确认:首连或指纹变更时展示 SHA256 指纹,由用户人工核对后决定是否信任。 */
@Composable
private fun HostKeyDialog(prompt: SshTerminalSession.HostKeyPrompt) {
    AlertDialog(
        onDismissRequest = { prompt.onDecision(false) },
        title = { Text(if (prompt.changed) "⚠️ 主机指纹已变更" else "确认主机指纹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prompt.changed) {
                    Text(
                        "${prompt.host}:${prompt.port} 的公钥与上次记录不一致。这可能是服务器重装,也可能是中间人攻击。请务必与服务器管理员核对后再信任。",
                        color = TermRed,
                        fontSize = 13.sp,
                    )
                    prompt.previousFingerprint?.let {
                        Text("旧:$it", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TermGray)
                    }
                    Text("新:${prompt.fingerprint}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                } else {
                    Text(
                        "首次连接 ${prompt.host}:${prompt.port}。请核对下面的指纹与服务器实际指纹一致后再信任(服务器上运行 ssh-keygen -lf /etc/ssh/ssh_host_*_key.pub 可查看)。",
                        fontSize = 13.sp,
                    )
                    Text("${prompt.algorithm}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TermGray)
                    Text(prompt.fingerprint, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { prompt.onDecision(true) }) { Text("信任并连接") } },
        dismissButton = { TextButton(onClick = { prompt.onDecision(false) }) { Text("取消") } },
    )
}

/** 胶囊式会话标签:焦点态高亮底色,在屏(分屏另一栏)次亮,其余置灰。 */
@Composable
private fun SessionChip(
    session: SshTerminalSession,
    focused: Boolean,
    onScreen: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val bg = if (focused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 3.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        StatusDot(session.state)
        Spacer(Modifier.width(6.dp))
        Text(
            tabTitle(session),
            maxLines = 1,
            fontSize = 13.sp,
            color = when {
                focused -> MaterialTheme.colorScheme.onSurface
                onScreen -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> TermGray
            },
        )
        if (focused) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭窗口",
                modifier = Modifier
                    .size(14.dp)
                    .focusProperties { canFocus = false }
                    .clickable(onClick = onClose),
                tint = TermGray,
            )
        }
    }
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

/** TerminalView 的客户端实现:闩锁 Ctrl/Alt、双指缩放调字号、单击聚焦+唤起输入法、Ctrl+空格切输入法。 */
class VtViewClient : TerminalViewClient {

    val ctrl = mutableStateOf(false)
    val alt = mutableStateOf(false)
    var view: TerminalView? = null
    var onChangeFontSize: ((increase: Boolean) -> Unit)? = null
    var onFocusGained: (() -> Unit)? = null
    var onImeSwitchRequested: (() -> Unit)? = null

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            onChangeFontSize?.invoke(scale > 1f)
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        onFocusGained?.invoke()
        view?.let { showSoftKeyboard(it) }
    }

    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        onFocusGained?.invoke()
        // Ctrl+空格 = 硬件键盘切输入法的系统惯例(TerminalView 在 onKeyPreIme 抢在输入法之前转发过来)。
        // 终端语义下它本是 NUL,极少用,需要时按 Ctrl+2。
        if (keyCode == KeyEvent.KEYCODE_SPACE && e.isCtrlPressed) {
            onImeSwitchRequested?.invoke()
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
