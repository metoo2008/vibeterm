# VibeTerm

为 vibe coding 优化的 Android SSH 终端 —— 在手机上舒服地跑 Claude Code / Codex。

## 为什么再造一个 SSH 客户端?

市面上的 Android SSH 客户端有三个治不好的病:

1. **打不了中文**:终端 App 向输入法声明 `TYPE_NULL`,中文输入法直接失效。VibeTerm 自研 IME 输入链路
   (真正的 `TYPE_CLASS_TEXT` + 本地预编辑悬浮),任何中文输入法开箱即用。
2. **切窗口费劲**:vibe coding 要同时开好几个窗口。VibeTerm 标签页多会话,连接活在前台服务里,
   转屏/切后台不断线。
3. **断线杀进程**:关 App/换网络,跑了半小时的测试就没了。VibeTerm 每个窗口自动
   `tmux -u new-session -A -s vtN`,断线重连无缝回到原会话 —— 服务端兜底,怎么断都不怕。

再加上为 CLI 编码代理准备的细节:附加键条(Esc / Ctrl / **Shift+Tab**(Claude Code 切模式)/ 方向键)、
bracketed paste 多行安全粘贴、任务完成通知(终端 bell + 忙碌后静默检测)。

## 服务器要求

- 标准 Linux + sshd,UTF-8 locale(如 `zh_CN.UTF-8` / `en_US.UTF-8`)
- 装有 `tmux`(没有也能用,但失去断线保活)
- 建议:Claude Code 里设置 `preferredNotifChannel: terminal_bell`,任务完成即收到手机通知

## 构建

```powershell
# 需要 JDK 17 + Android SDK(compileSdk 34)
$env:JAVA_HOME='<jdk17 路径>'
.\gradlew.bat :app:assembleDebug
# APK 产物:app\build\outputs\apk\debug\app-debug.apk
```

国内网络已配置镜像:Gradle 走腾讯镜像、Maven 走阿里云(见 `settings.gradle.kts`)。

## 架构

见 [docs/DESIGN.md](docs/DESIGN.md)。要点:

- `terminal-emulator/`、`terminal-view/` vendor 自 [termux-app](https://github.com/termux/termux-app)(GPLv3),
  打了两个关键补丁:本地 PTY/JNI 替换为抽象传输层(SSH);IME 输入层重写以原生支持中文。
- `app/` 为 Kotlin + Jetpack Compose,SSH 传输用 ConnectBot 维护的
  [sshlib](https://github.com/connectbot/sshlib)(Apache 2.0)。

## 许可证

GPLv3(因 vendor Termux 内核)。见 [LICENSE.md](LICENSE.md)。
