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
}
