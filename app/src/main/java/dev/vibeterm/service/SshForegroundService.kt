package dev.vibeterm.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.vibeterm.MainActivity
import dev.vibeterm.R
import dev.vibeterm.notify.Notifications

/**
 * 有活跃 SSH 会话时运行的前台服务:提升进程优先级,尽量避免后台被杀导致连接中断。
 * (真正的兜底是服务端 tmux;本服务只是“尽力而为”那一层。)
 */
class SshForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(this, count), type)
        return START_STICKY
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_COUNT = "count"

        fun update(context: Context, sessionCount: Int) {
            if (sessionCount <= 0) {
                context.stopService(Intent(context, SshForegroundService::class.java))
            } else {
                val intent = Intent(context, SshForegroundService::class.java)
                    .putExtra(EXTRA_COUNT, sessionCount)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        private fun buildNotification(context: Context, count: Int): Notification {
            val pending = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return Notification.Builder(context, Notifications.CHANNEL_SESSIONS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("VibeTerm")
                .setContentText("$count 个 SSH 会话运行中")
                .setOngoing(true)
                .setContentIntent(pending)
                .build()
        }
    }
}
