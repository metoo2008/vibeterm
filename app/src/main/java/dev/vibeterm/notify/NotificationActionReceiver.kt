package dev.vibeterm.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.vibeterm.ssh.SessionManager

/** 通知按钮 → 会话输入:不打开界面,直接把回车/Esc 写进对应 SSH 会话。 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND) return
        val handle = intent.getStringExtra(Notifications.EXTRA_SESSION_HANDLE) ?: return
        val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: return

        SessionManager.init(context)
        SessionManager.findByHandle(handle)?.write(payload)

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) {
            context.getSystemService(NotificationManager::class.java).cancel(notificationId)
        }
    }

    companion object {
        const val ACTION_SEND = "dev.vibeterm.action.SEND_INPUT"
        const val EXTRA_PAYLOAD = "payload"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
