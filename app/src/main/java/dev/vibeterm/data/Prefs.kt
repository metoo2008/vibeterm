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

    /** OSC 52:允许远端程序写入本机剪贴板。默认关(失陷服务器可劫持剪贴板诱导粘贴恶意命令)。 */
    fun osc52Clipboard(context: Context) = prefs(context).getBoolean("osc52_clipboard", false)
    fun setOsc52Clipboard(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("osc52_clipboard", value).apply()
    }

    /** 锁屏免解锁批准。默认关:锁屏点通知按钮需先解锁(指纹即可),防止拿到设备的人替 AI 放行。 */
    fun lockscreenApproveWithoutAuth(context: Context) =
        prefs(context).getBoolean("lockscreen_noauth", false)
    fun setLockscreenApproveWithoutAuth(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("lockscreen_noauth", value).apply()
    }

    /** 界面语言。"system" 跟随系统;否则为 BCP-47 标签(en / zh-CN / ja / ko),见 [LocaleManager]。 */
    fun appLang(context: Context): String =
        prefs(context).getString("app_lang", LocaleManager.SYSTEM) ?: LocaleManager.SYSTEM
    fun setAppLang(context: Context, tag: String) {
        prefs(context).edit().putString("app_lang", tag).apply()
    }
}
