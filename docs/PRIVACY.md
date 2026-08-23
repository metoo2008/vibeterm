# VibeTerm 隐私政策 / Privacy Policy

最后更新 / Last updated: 2026-08-23

## 中文

VibeTerm 是一个开源的 Android SSH 终端客户端。我们高度重视你的隐私。

**我们不收集任何数据。** VibeTerm 没有任何分析、遥测、广告或崩溃上报 SDK,不会把任何信息发送给开发者或任何第三方。

- **你的凭据(SSH 密码)**:仅保存在你的设备本地,使用 Android Keystore 硬件级密钥进行 AES-256-GCM 加密。它们**绝不会**离开你的设备,也不会发送给我们。
- **主机配置(地址、用户名、端口)**:仅保存在你设备的应用私有目录中,不参与云备份或设备迁移。
- **网络连接**:VibeTerm 只连接**你自己配置**的 SSH 服务器。除此之外不与任何服务器通信。你在终端里输入和看到的内容,只在你的设备与你的服务器之间传输,我们无法访问。
- **权限说明**:
  - 网络(INTERNET):建立 SSH 连接。
  - 通知(POST_NOTIFICATIONS):任务完成提醒、会话保活前台通知。
  - 前台服务 / 唤醒锁(FOREGROUND_SERVICE / WAKE_LOCK):在后台维持 SSH 连接不被系统中断。

因为我们不收集数据,也就没有数据可供出售、共享或删除。卸载应用即彻底清除所有本地数据。

本应用不面向 13 岁以下儿童。

如有疑问,请通过项目仓库提交 Issue:https://github.com/metoo2008/vibeterm

---

## English

VibeTerm is an open-source Android SSH terminal client. We take your privacy seriously.

**We collect no data.** VibeTerm contains no analytics, telemetry, advertising, or crash-reporting SDKs, and sends no information to the developer or any third party.

- **Your credentials (SSH passwords)** are stored only locally on your device, encrypted with AES-256-GCM using a hardware-backed Android Keystore key. They never leave your device and are never sent to us.
- **Host configuration (address, username, port)** is stored only in the app's private storage on your device and is excluded from cloud backup and device transfer.
- **Network connections** are made only to the SSH servers **you configure**. VibeTerm communicates with no other servers. What you type and see in the terminal travels only between your device and your server; we have no access to it.
- **Permissions**: INTERNET (SSH connections); POST_NOTIFICATIONS (task-done alerts, keep-alive foreground notification); FOREGROUND_SERVICE / WAKE_LOCK (keep SSH sessions alive in the background).

Because we collect no data, there is no data to sell, share, or delete. Uninstalling the app removes all local data.

This app is not directed at children under 13.

Questions? Open an issue at https://github.com/metoo2008/vibeterm
