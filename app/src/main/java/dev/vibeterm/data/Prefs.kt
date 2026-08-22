package dev.vibeterm.data

import android.content.Context

/** 轻量 UI 偏好(SharedPreferences "ui",与字号共用同一文件)。 */
object Prefs {
    private fun prefs(context: Context) =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun notifyBell(context: Context) = prefs(context).getBoolean("notify_bell", true)
    fun setNotifyBell(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notify_bell", value).apply()
    }

    fun notifySilence(context: Context) = prefs(context).getBoolean("notify_silence", true)
    fun setNotifySilence(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notify_silence", value).apply()
    }

    private const val DEFAULT_QUICK_COMMANDS =
        "claude\nclaude -c\n/compact\ngit status\ngit diff\nnpm test"

    fun quickCommands(context: Context): List<String> =
        prefs(context).getString("quick_cmds", DEFAULT_QUICK_COMMANDS)!!
            .split("\n").filter { it.isNotBlank() }

    fun setQuickCommands(context: Context, commands: List<String>) {
        prefs(context).edit().putString("quick_cmds", commands.joinToString("\n")).apply()
    }
}
