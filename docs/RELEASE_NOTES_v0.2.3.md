# VibeTerm v0.2.3 — 安全修复版 / Security Hardening Release

**为 vibe coding 而生的 Android SSH 终端**:原生中文输入、tmux 断线保活、多窗口/平板分屏、锁屏批准 AI 代理。

*An Android SSH terminal built for vibe coding — native CJK input, tmux-backed session persistence, split-screen, and lock-screen approval for AI coding agents.*

> 本版本经过**四轮独立安全审计**,已修复截至发布时所有已知的高/中风险审计发现;第四轮未再发现高风险问题。安全是持续过程,欢迎通过 Issue 反馈新发现。
> Incorporates fixes from **four rounds of independent security audit**; all known high/medium findings to date are resolved and the fourth round surfaced no high-risk issues. Security is an ongoing process — please report new findings via Issues.

---

## 🔒 安全加固亮点(v0.2.0 → v0.2.3)

- **主机公钥确认**:首次连接与密钥变更时展示 SHA256 指纹,由你人工核对确认后才发送密码——不再静默信任,杜绝首连中间人窃密码。
- **修复 Terrapin 漏洞**:SSH 库升级到 2.2.22,修复 CVE-2023-48795。
- **重连世代隔离**:每次重连封装为独立不可变会话对象,「切代 + 发布连接」用锁做成原子操作,彻底消除旧连接线程误关/覆盖新连接的竞态(快速切网压力测试通过)。
- **指纹保存失败即拒连**:点「信任并连接」但公钥未能落盘时,连接被取消并明确提示,不再出现「看似已信任、实则未固定」的静默不一致。
- **依赖完整性校验**:新增 `gradle/verification-metadata.xml`,对全部依赖构件做 SHA-256 校验,防镜像被污染;SSH 库跟进上游维护版本 2.2.48。(注:校验值由 Gradle 自动生成,已独立核对 sshlib 与 Maven Central 一致,其余构件来源复核为持续项。)
- **并发健壮性(第四轮)**:主机指纹弹窗加世代标记与容量 1 决策队列,消除切代竞态与「决定先于等待被丢弃」;状态提示改后台线程写入,杜绝主线程写满输出队列的死锁;前台服务空 Intent 重启时自停,避免「0 会话常驻通知耗电」。
- **锁屏批准需认证**:通知的「确认/打断」按钮默认要求先解锁(指纹即可);低于 Android 12 无法强制认证的设备上则不提供直连按键动作。
- **OSC 52 默认关闭**:失陷服务器无法再劫持你的剪贴板,需要时可在设置中开启。
- **编辑主机清旧密码**:改动地址/端口/用户名后强制重新输入密码,避免把旧凭据发往新服务器。
- **凭据不外带**:关闭备份 + 显式数据提取规则,主机清单与加密密码不参与 adb/云备份及设备迁移。
- **健壮性**:配置文件原子写、连接失败指数退避防每秒重连、写队列有界防 OOM、端口合法性校验等。

密码始终以 Android Keystore AES-256-GCM 加密存储;应用无任何遥测,只连接你自己配置的服务器。

## ✨ 完整功能

- 🀄 **原生中文输入** — 自研 IME 输入链路,百度/搜狗/讯飞/Gboard 开箱即用(真机验证)
- 🔌 **tmux 断线保活** — 关 App、换网、重启手机,重连都无缝回到原会话
- 🪟 **多窗口 + 平板分屏** — 标签页多会话,宽屏双栏并排
- 🔔 **任务完成通知** — 终端响铃 + 忙碌后静默双信号
- ✅ **锁屏批准** — 检测到确认提示时,通知直接带按钮批准 Claude 的工具调用
- ⌘ **快捷命令面板**、⌨️ **附加键条**(Esc/Shift+Tab/Ctrl 等)、🚀 **冷启动自动恢复**、🌐 **网络秒重连**
- 🎨 JetBrains Mono 字体 + GitHub-Dark 配色;Ctrl+空格切输入法

## 📦 安装

下载下方 `app-release.apk`,在手机上允许「安装未知来源应用」后安装。要求 Android 8.0 (API 26) 及以上。

**SHA-256 校验**(安全版建议核对):
```
482fbb53318771f922b6f931b32d196a46574c95fa2f9faadff92dc8bd04f407
```

**服务器建议**:安装 tmux、UTF-8 locale;Claude Code 设置 `preferredNotifChannel: terminal_bell` 可获得精确的任务完成通知。

## ⚠️ 已知事项

- 本 APK 与开发调试版签名不同,**不能覆盖安装**;若之前装过调试版,需先卸载(会清空主机列表与密码)。
- 首次连接每台服务器会弹指纹确认框,这是安全设计,请核对指纹后再信任。

## 🔜 后续计划

SSH 密钥认证(ed25519)、服务器 tmux 会话浏览、mosh 传输、并发时序自动化测试覆盖。

---

GPL-3.0 · 完整变更见 `git log v0.2.0..v0.2.3` · Issues 与 PR 欢迎
