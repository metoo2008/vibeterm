package dev.vibeterm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import dev.vibeterm.R
import dev.vibeterm.data.HostProfile
import dev.vibeterm.data.HostStore
import dev.vibeterm.data.LocaleManager
import dev.vibeterm.data.Prefs
import com.termux.terminal.TerminalEmulator
import dev.vibeterm.data.SecureStore
import dev.vibeterm.ssh.SessionManager
import dev.vibeterm.ui.theme.TermGray
import dev.vibeterm.ui.theme.TermGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(onOpenSession: () -> Unit) {
    val context = LocalContext.current
    val hosts = remember { mutableStateListOf<HostProfile>().apply { addAll(HostStore.load(context)) } }
    var editing by remember { mutableStateOf<HostProfile?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var passwordPrompt by remember { mutableStateOf<HostProfile?>(null) }

    fun persist() = HostStore.save(context, hosts)

    fun connectTo(host: HostProfile) {
        val password = SecureStore.getPassword(context, host.id)
        if (password == null) {
            passwordPrompt = host
        } else {
            SessionManager.open(host, password)
            onOpenSession()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "❯ VibeTerm",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TermGreen,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (SessionManager.sessions.isNotEmpty()) {
                        TextButton(onClick = onOpenSession) {
                            Text(stringResource(R.string.sessions_count, SessionManager.sessions.size))
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings), tint = TermGray)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = TermGreen,
                contentColor = MaterialTheme.colorScheme.background,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_host))
            }
        },
    ) { padding ->
        if (hosts.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "❯_",
                    fontSize = 44.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TermGreen,
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.hosts_empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.hosts_empty_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(hosts, key = { it.id }) { host ->
                    HostCard(
                        host = host,
                        onConnect = { connectTo(host) },
                        onEdit = { editing = host },
                        onDelete = {
                            hosts.remove(host)
                            SecureStore.removePassword(context, host.id)
                            SessionManager.forgetPassword(host.id)
                            persist()
                        },
                    )
                }
            }
        }
    }

    if (showAdd || editing != null) {
        HostEditDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { profile, password ->
                val old = hosts.firstOrNull { it.id == profile.id }
                // 安全:改了地址/端口/用户名意味着可能是另一台机器,旧密码不能沿用,
                // 强制清除已存密码(含内存缓存),要求重新输入,避免把旧凭据发给新服务器
                val identityChanged = old != null &&
                    (old.host != profile.host || old.port != profile.port || old.username != profile.username)
                val index = hosts.indexOfFirst { it.id == profile.id }
                if (index >= 0) hosts[index] = profile else hosts.add(profile)
                when {
                    password.isNotEmpty() -> SecureStore.putPassword(context, profile.id, password)
                    identityChanged -> {
                        SecureStore.removePassword(context, profile.id)
                        SessionManager.forgetPassword(profile.id)
                    }
                }
                persist()
                showAdd = false
                editing = null
            },
        )
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }

    passwordPrompt?.let { host ->
        PasswordDialog(
            host = host,
            onDismiss = { passwordPrompt = null },
            onConnect = { password, save ->
                if (save) SecureStore.putPassword(context, host.id, password)
                passwordPrompt = null
                SessionManager.open(host, password)
                onOpenSession()
            },
        )
    }
}

@Composable
private fun HostCard(
    host: HostProfile,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onConnect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "❯",
                color = TermGreen,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(host.displayName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "${host.username}@${host.host}:${host.port}" +
                    if (host.useTmux) " ·tmux" else "",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit), tint = TermGray, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete), tint = TermGray, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bell by remember { mutableStateOf(Prefs.notifyBell(context)) }
    var silence by remember { mutableStateOf(Prefs.notifySilence(context)) }
    var noAuth by remember { mutableStateOf(Prefs.lockscreenApproveWithoutAuth(context)) }
    var osc52 by remember { mutableStateOf(Prefs.osc52Clipboard(context)) }
    var showLang by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingSwitch(
                    title = stringResource(R.string.set_bell_title),
                    description = stringResource(R.string.set_bell_desc),
                    checked = bell,
                ) { bell = it; Prefs.setNotifyBell(context, it) }
                SettingSwitch(
                    title = stringResource(R.string.set_silence_title),
                    description = stringResource(R.string.set_silence_desc),
                    checked = silence,
                ) { silence = it; Prefs.setNotifySilence(context, it) }
                SettingSwitch(
                    title = stringResource(R.string.set_noauth_title),
                    description = stringResource(R.string.set_noauth_desc),
                    checked = noAuth,
                ) { noAuth = it; Prefs.setLockscreenApproveWithoutAuth(context, it) }
                SettingSwitch(
                    title = stringResource(R.string.set_osc52_title),
                    description = stringResource(R.string.set_osc52_desc),
                    checked = osc52,
                ) {
                    osc52 = it
                    Prefs.setOsc52Clipboard(context, it)
                    TerminalEmulator.allowOsc52Clipboard = it
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showLang = true }
                        .padding(vertical = 2.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_language_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            LocaleManager.displayName(context, Prefs.appLang(context)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    stringResource(R.string.set_tips),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )

    if (showLang) {
        LanguageDialog(
            current = Prefs.appLang(context),
            onDismiss = { showLang = false },
            onPick = { tag ->
                showLang = false
                if (tag != Prefs.appLang(context)) {
                    Prefs.setAppLang(context, tag)
                    onDismiss()
                    // 语言切换后整活动重建,让所有 stringResource 走新 locale
                    context.findActivity()?.recreate()
                }
            },
        )
    }
}

@Composable
private fun LanguageDialog(
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_language_title)) },
        text = {
            Column {
                LocaleManager.TAGS.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(tag) }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = tag == current, onClick = { onPick(tag) })
                        Spacer(Modifier.width(8.dp))
                        Text(LocaleManager.displayName(context, tag))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HostEditDialog(
    initial: HostProfile?,
    onDismiss: () -> Unit,
    onSave: (HostProfile, String) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var useTmux by remember { mutableStateOf(initial?.useTmux ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_host_title else R.string.edit_host_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.field_label)) }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text(stringResource(R.string.field_host)) }, singleLine = true)
                OutlinedTextField(
                    port, { port = it }, label = { Text(stringResource(R.string.field_port)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.field_username)) }, singleLine = true)
                OutlinedTextField(
                    password, { password = it },
                    label = { Text(stringResource(if (initial == null) R.string.field_password else R.string.field_password_keep)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useTmux, onCheckedChange = { useTmux = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.field_tmux))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank() && username.isNotBlank(),
                onClick = {
                    val profile = (initial ?: HostProfile()).copy(
                        label = label.trim(),
                        host = host.trim(),
                        port = (port.toIntOrNull() ?: 22).coerceIn(1, 65535),
                        username = username.trim(),
                        useTmux = useTmux,
                    )
                    onSave(profile, password)
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PasswordDialog(
    host: HostProfile,
    onDismiss: () -> Unit,
    onConnect: (password: String, save: Boolean) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_to_title, host.displayName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    password, { password = it }, label = { Text(stringResource(R.string.field_password)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = save, onCheckedChange = { save = it })
                    Text(stringResource(R.string.remember_password))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onConnect(password, save) }) {
                Text(stringResource(R.string.action_connect))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
