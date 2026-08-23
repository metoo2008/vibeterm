# VibeTerm v0.3.1 — Android 16 / Google Play 就绪 · Security Hardening

## 本版重点

- **targetSdk / compileSdk 升到 36(Android 16)**,满足 Google Play 自 2026-08-31 起对新应用与更新的 API 36 要求;工具链同步升级(AGP 8.11.1 / Gradle 8.13),已在 Android 16 模拟器实测 edge-to-edge 与连接。
- **剪贴板粘贴加固**:统一有界读取入口——只接收纯文本(URI/Intent 型剪贴板直接拒绝,不经 ContentProvider 在主线程读整段);长度在 `.toString()` 之前检查;**超限整次拒绝并提示,绝不截断**(避免把半条命令/半个引号发到远端)。三处粘贴入口共用同一 helper 并补齐单测。
- CI 全部 Action 升级到 Node 24 版本并重新固定 commit SHA;库模块 compileSdk 同步 36;文档校准。
- 以下为累计的安全加固说明。

---



**为 vibe coding 而生的 Android SSH 终端**:原生中文输入、tmux 断线保活、多窗口/平板分屏、锁屏批准 AI 代理。

*An Android SSH terminal built for vibe coding — native CJK input, tmux-backed session persistence, split-screen, and lock-screen approval for AI coding agents.*

> 本版本经过**六轮独立安全审计**:所有已知**高风险**问题均已修复,近三轮未再发现高风险;中风险问题已基本修复,剩余供应链依赖校验一项作为已记录的发布后跟进项(见 [SECURITY.md](../SECURITY.md))。安全是持续过程,欢迎通过 Issue 反馈。
> Hardened across **six rounds of independent security audit**: all known high-risk issues are fixed (none in the last three rounds); medium-risk issues are largely resolved, with full dependency-verification tracked as a documented post-release follow-up (see SECURITY.md).

---

## 🔒 安全加固亮点(v0.2.0 → v0.2.5)

- **主机公钥确认**:首次连接与密钥变更时展示 SHA256 指纹,由你人工核对确认后才发送密码——不再静默信任,杜绝首连中间人窃密码。
- **修复 Terrapin 漏洞**:SSH 库升级到 2.2.22,修复 CVE-2023-48795。
- **重连世代隔离**:每次重连封装为独立不可变会话对象,「切代 + 发布连接」用锁做成原子操作,彻底消除旧连接线程误关/覆盖新连接的竞态(快速切网压力测试通过)。
- **指纹保存失败即拒连**:点「信任并连接」但公钥未能落盘时,连接被取消并明确提示,不再出现「看似已信任、实则未固定」的静默不一致。
- **供应链**:SSH 库跟进上游维护版本 2.2.48;Gradle wrapper 固定 SHA-256、GitHub Actions 全部固定 commit SHA、依赖全部锁定精确版本(无动态版本区间)、仓库仅走 HTTPS。(完整 `verification-metadata.xml` 依赖校验因「镜像生成 / CI 异环境校验」的跨环境字节差异导致构建脆弱,暂缓,待在 CI 环境内生成维护后再启用。)
- **并发健壮性(第四轮)**:主机指纹弹窗加世代标记与容量 1 决策队列,消除切代竞态与「决定先于等待被丢弃」;状态提示改后台线程写入,杜绝主线程写满输出队列的死锁;前台服务空 Intent 重启时自停,避免「0 会话常驻通知耗电」。
- **内存健壮性(第五轮)**:状态提示线程改为**有界队列 + 满时丢弃**并对「缓冲已满」提示限频;SSH 输入队列改为**按总字节数限流(约 4 MiB)**而非仅限条数——远端停止读取时连续粘贴大文本不再堆积 OOM。
- **超大粘贴防护(第六轮)**:在粘贴统一入口(所有粘贴路径的收敛点)先把文本截断到 100 万字符,**在正则清洗与 UTF-8 编码之前**生效,避免单次几百 MiB 剪贴板在限流前制造多份大副本触发 OOM;`onTransportWrite` 也在复制数组前先按字节数判断。
- **构建完整性(第六轮)**:修正 Gradle wrapper JAR 与声明版本(8.9)不一致(此前误用了 9.x 的 wrapper),CI 新增 wrapper 哈希校验;修正 keystore 文档措辞(硬件保护取决于设备,不再绝对化)。
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

下载下方 `app-release.apk`,在手机上允许「安装未知来源应用」后安装。要求 Android 8.0 (API 26) 及以上,已适配至 Android 16。

**SHA-256 校验**(用于核对下载的 APK 与发布者上传的二进制一致;注:Android APK 默认不逐字节可复现,该值对应本次发布上传的具体文件,不代表可由源码重建出相同哈希):
```
e89e368aefa3b2f85d4905784c4ff6ca7dd4ba29f3c58774f26248acf29634ad
```

**服务器建议**:安装 tmux、UTF-8 locale;Claude Code 设置 `preferredNotifChannel: terminal_bell` 可获得精确的任务完成通知。

## ⚠️ 已知事项

- 本 APK 与开发调试版签名不同,**不能覆盖安装**;若之前装过调试版,需先卸载(会清空主机列表与密码)。
- 首次连接每台服务器会弹指纹确认框,这是安全设计,请核对指纹后再信任。

## 🔜 后续计划

SSH 密钥认证(ed25519)、服务器 tmux 会话浏览、mosh 传输、并发时序自动化测试覆盖。

---

GPL-3.0 · 完整变更见 `git log v0.2.0..v0.3.1` · 供应链取舍见 SECURITY.md · Issues 与 PR 欢迎
