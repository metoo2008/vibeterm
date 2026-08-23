package dev.vibeterm.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
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

    /** 忙碌后静默启发式命中;若屏幕停在确认提示上,标题给出更明确的提醒。 */
    fun onPossiblyFinished(session: SshTerminalSession) {
        if (!Prefs.notifySilence(SessionManager.appContext)) return
        if (looksLikeWaitingForConfirmation(session)) {
            notifyActivity(session, "可能在等你确认", "${session.displayName} 停在确认提示,可直接在通知上回复")
        } else {
            notifyActivity(session, "任务可能已完成", "${session.displayName} 已停止输出")
        }
    }

    /** 读取可见屏幕末尾文本,判断是否停在 y/n、选项列表等待确认的界面。仅在主线程调用。 */
    private fun looksLikeWaitingForConfirmation(session: SshTerminalSession): Boolean {
        return try {
            val emulator = session.emulator ?: return false
            val text = emulator.screen
                .getSelectedText(0, 0, emulator.mColumns, emulator.mRows)
                ?.trimEnd() ?: return false
            val tail = text.takeLast(500)
            CONFIRM_PATTERNS.any { tail.contains(it) }
        } catch (_: Exception) {
            false
        }
    }

    private val CONFIRM_PATTERNS = listOf(
        "y/n", "Y/n", "(y/N", "[y/N", "[Y/n",
        "Do you want", "Would you like",
        "1. Yes", "❯ 1", "2. No",
        "continue?", "Continue?", "proceed?", "Proceed?",
        "是否继续", "确认",
    )

    private fun notifyActivity(session: SshTerminalSession, title: String, text: String) {
        // 会话正显示在屏幕上(含分屏的任一面板)时不打扰
        if (SessionManager.appVisible && session.attachedView != null) return

        val context = SessionManager.appContext
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationId = session.mHandle.hashCode()

        val contentIntent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_SESSION_HANDLE, session.mHandle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentPending = PendingIntent.getActivity(
            context, notificationId, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = Icon.createWithResource(context, R.drawable.ic_notification)
        // 默认要求先解锁(指纹即可)才发送输入,防止拿到设备的人替 AI 放行;可在设置里关闭。
        val requireAuth = !Prefs.lockscreenApproveWithoutAuth(context)
        // setAuthenticationRequired 仅 API 31+ 有效。API 26–30 无法在锁屏拦截动作,
        // 若要求认证却又加了直连按键的动作,拿到设备的人可绕过 —— 此时干脆不加动作,
        // 只保留点击进入 App(进 App 前会经系统锁屏),把认证交给 keyguard。
        val canEnforceAuth = Build.VERSION.SDK_INT >= 31
        val addActions = !requireAuth || canEnforceAuth

        val builder = Notification.Builder(context, CHANNEL_ACTIVITY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
        if (addActions) {
            builder.addAction(
                notificationAction(icon, "✅ 确认(回车)",
                    sendInputPending(context, session, "\r", notificationId, requestCodeOffset = 1), requireAuth)
            )
            builder.addAction(
                notificationAction(icon, "✋ 打断(Esc)",
                    sendInputPending(context, session, "\u001b", notificationId, requestCodeOffset = 2), requireAuth)
            )
        }
        context.getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
    }

    private fun notificationAction(
        icon: Icon,
        title: String,
        pending: PendingIntent,
        requireAuth: Boolean,
    ): Notification.Action {
        val builder = Notification.Action.Builder(icon, title, pending)
        // API 31+:动作触发前要求设备认证(锁屏时会先弹解锁/生物识别)
        if (requireAuth && Build.VERSION.SDK_INT >= 31) builder.setAuthenticationRequired(true)
        return builder.build()
    }

    private fun sendInputPending(
        context: Context,
        session: SshTerminalSession,
        payload: String,
        notificationId: Int,
        requestCodeOffset: Int,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_SEND)
            .putExtra(EXTRA_SESSION_HANDLE, session.mHandle)
            .putExtra(NotificationActionReceiver.EXTRA_PAYLOAD, payload)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        return PendingIntent.getBroadcast(
            context, notificationId + requestCodeOffset, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
