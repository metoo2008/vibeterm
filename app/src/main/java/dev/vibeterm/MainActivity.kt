package dev.vibeterm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.vibeterm.notify.Notifications
import dev.vibeterm.ssh.SessionManager
import dev.vibeterm.ui.HostListScreen
import dev.vibeterm.ui.TerminalScreen
import dev.vibeterm.ui.theme.VibeTermTheme
import dev.vibeterm.ui.theme.applyTerminalPalette

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionManager.init(this)
        Notifications.ensureChannels(this)
        applyTerminalPalette()
        // OSC 52(远端写剪贴板)按用户设置启用,默认关
        com.termux.terminal.TerminalEmulator.allowOsc52Clipboard = dev.vibeterm.data.Prefs.osc52Clipboard(this)
        SessionManager.restoreWindows() // 冷启动自动恢复上次打开的窗口
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        handleIntent(intent)
        setContent {
            VibeTermTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val handle = intent?.getStringExtra(Notifications.EXTRA_SESSION_HANDLE) ?: return
        SessionManager.findByHandle(handle)?.let { SessionManager.selected = it }
    }

    override fun onStart() {
        super.onStart()
        SessionManager.appVisible = true
    }

    override fun onStop() {
        SessionManager.appVisible = false
        super.onStop()
    }
}

@Composable
fun AppRoot() {
    var showHosts by remember { mutableStateOf(SessionManager.sessions.isEmpty()) }
    val selected = SessionManager.selected

    // 通知点击或新开会话时自动切到终端
    LaunchedEffect(selected) {
        if (selected != null) showHosts = false
    }

    if (showHosts || SessionManager.sessions.isEmpty()) {
        HostListScreen(onOpenSession = { showHosts = false })
    } else {
        BackHandler { showHosts = true }
        TerminalScreen(onShowHosts = { showHosts = true })
    }
}
