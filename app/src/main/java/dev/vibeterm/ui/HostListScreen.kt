package dev.vibeterm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.vibeterm.data.HostProfile
import dev.vibeterm.data.HostStore
import dev.vibeterm.data.SecureStore
import dev.vibeterm.ssh.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(onOpenSession: () -> Unit) {
    val context = LocalContext.current
    val hosts = remember { mutableStateListOf<HostProfile>().apply { addAll(HostStore.load(context)) } }
    var editing by remember { mutableStateOf<HostProfile?>(null) }
    var showAdd by remember { mutableStateOf(false) }
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
                title = { Text("VibeTerm") },
                actions = {
                    if (SessionManager.sessions.isNotEmpty()) {
                        TextButton(onClick = onOpenSession) {
                            Text("会话 (${SessionManager.sessions.size})")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加主机")
            }
        },
    ) { padding ->
        if (hosts.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有主机", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text("点右下角 + 添加你的开发服务器", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                items(hosts, key = { it.id }) { host ->
                    ListItem(
                        headlineContent = { Text(host.displayName) },
                        supportingContent = {
                            Text(
                                "${host.username}@${host.host}:${host.port}" +
                                    if (host.useTmux) " · tmux 保活" else ""
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = host }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                                }
                                IconButton(onClick = {
                                    hosts.remove(host)
                                    SecureStore.removePassword(context, host.id)
                                    persist()
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable { connectTo(host) },
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
                val index = hosts.indexOfFirst { it.id == profile.id }
                if (index >= 0) hosts[index] = profile else hosts.add(profile)
                if (password.isNotEmpty()) SecureStore.putPassword(context, profile.id, password)
                persist()
                showAdd = false
                editing = null
            },
        )
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
        title = { Text(if (initial == null) "添加主机" else "编辑主机") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("名称(可选)") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("主机地址") }, singleLine = true)
                OutlinedTextField(
                    port, { port = it }, label = { Text("端口") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(username, { username = it }, label = { Text("用户名") }, singleLine = true)
                OutlinedTextField(
                    password, { password = it },
                    label = { Text(if (initial == null) "密码" else "密码(留空则不修改)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useTmux, onCheckedChange = { useTmux = it })
                    Spacer(Modifier.width(8.dp))
                    Text("tmux 断线保活(推荐)")
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
                        port = port.toIntOrNull() ?: 22,
                        username = username.trim(),
                        useTmux = useTmux,
                    )
                    onSave(profile, password)
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
        title = { Text("连接到 ${host.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    password, { password = it }, label = { Text("密码") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = save, onCheckedChange = { save = it })
                    Text("记住密码(Keystore 加密)")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onConnect(password, save) }) {
                Text("连接")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
