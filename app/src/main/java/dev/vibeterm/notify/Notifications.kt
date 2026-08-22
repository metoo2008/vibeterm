package dev.vibeterm.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.vibeterm.MainActivity
import dev.vibeterm.R
import dev.vibeterm.data.Prefs
import dev.vibeterm.ssh.SessionManager
import dev.vibeterm.ssh.SshTerminalSession

object Notifications {
    const val CHANNEL_SESSIONS = "sessions"
    const val CHANNEL_ACTIVITY = "activity"
    const val EXTRA_SESSION_HANDLE = "session_handle"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSIONS, "SSH 会话保活", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTIVITY, "任务提醒", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** 终端响铃(Claude Code 等工具的精确完成信号)。 */
    fun onBell(session: SshTerminalSession) {
        if (!Prefs.notifyBell(SessionManager.appContext)) return
        notifyActivity(session, "终端响铃", "${session.displayName} 需要你的注意")
    }

    /** 忙碌后静默启发式命中。 */
    fun onPossiblyFinished(session: SshTerminalSession) {
        if (!Prefs.notifySilence(SessionManager.appContext)) return
        notifyActivity(session, "任务可能已完成", "${session.displayName} 已停止输出")
    }

    private fun notifyActivity(session: SshTerminalSession, title: String, text: String) {
        // 会话正显示在屏幕上(含分屏的任一面板)时不打扰
        if (SessionManager.appVisible && session.attachedView != null) return

        val context = SessionManager.appContext
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_SESSION_HANDLE, session.mHandle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, session.mHandle.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ACTIVITY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(session.mHandle.hashCode(), notification)
    }
}
