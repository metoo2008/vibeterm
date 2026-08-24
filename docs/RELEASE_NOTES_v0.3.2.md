# VibeTerm v0.3.2

首个公开发布。为 vibe coding 而生的 Android SSH 终端 —— 在手机/平板上舒服地跑 Claude Code、Codex。

*First public release. An Android SSH terminal built for vibe coding — run Claude Code / Codex comfortably from your phone or tablet.*

## ✨ 核心特性

- **完整 Android IME / Unicode 输入** — 多数终端 App 只当"键盘设备"、收不了输入法提交的文本,导致中文、日文、韩文、印地语、阿拉伯语、俄语等打不了。VibeTerm 完整实现 Android IME/InputConnection 协议,这些语言均可输入(见 README 的输入语言支持与"输入 vs 显示"说明)。
- **tmux 断线保活** — 关 App、切换网络、重启手机,重连后无缝回到原会话,长任务不中断。
- **多窗口 + 平板分屏** — 标签页多会话,宽屏左右分屏。
- **锁屏批准 AI 工具调用** — 检测到确认提示时,通知直接带「确认/打断」按钮(默认需解锁认证)。
- **快捷命令面板、附加键条**(Esc / Shift+Tab / Ctrl / 方向键)、**任务完成通知、冷启动自动恢复会话、网络切换秒重连**。
- JetBrains Mono 字体 + 深色终端配色;已适配 Android 16 全面屏。

## 🔒 隐私与安全

- 不收集任何数据,无广告无遥测;密码仅在本机以 Android Keystore 加密存储;只连接你自己配置的服务器。
- 首次连接每台服务器展示 SHA256 指纹,核对确认后才发送密码。
- 经多轮独立安全审计,已修复所有已知高/中风险问题(细节见 [SECURITY.md](https://github.com/metoo2008/vibeterm/blob/master/SECURITY.md))。

## 📦 安装

下载下方 `app-release.apk`,允许"安装未知来源应用"后安装。要求 Android 8.0 (API 26) 及以上,已适配至 Android 16。

**SHA-256**(核对下载的 APK 与本页二进制一致;APK 默认不逐字节可复现,该值对应本次上传的文件):
```
e5c1f3c4f996d5deb5012185401de1972e42707e3caa0890980ca308c95dcfcb
```

**服务器建议**:安装 tmux(用于断线保活)、UTF-8 locale;Claude Code 设 `preferredNotifChannel: terminal_bell` 可获得精确的完成通知。

## ⚠️ 说明

- 本项目为 **GPL-3.0**(内嵌 Termux 终端引擎),走 GPL 友好渠道:**F-Droid + GitHub**,不上 Google Play。
- F-Droid 与 GitHub 的 APK 由不同密钥签名,不能相互覆盖升级,请择一渠道安装。
- 首次连接会弹主机指纹确认框,这是安全设计,请核对后再信任。

---

GPL-3.0 · 源码与文档见仓库 · Issues 与 PR 欢迎
