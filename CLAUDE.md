# VibeTerm — 为 vibe coding 优化的 Android SSH 终端

## 项目定位
Android SSH 客户端,核心三卖点:原生中文输入(自研 IME InputConnection)、多窗口、tmux 自动断线保活。
设计文档在 `docs/DESIGN.md`,**开发以它为准绳,完成里程碑要更新其中 §9 的勾选状态**。

## 构建
- 工具链全部在 `D:\dev-tools`(便携安装):JDK 17(`jdk-17.0.20.1+1`)、Android SDK(`android-sdk`)、MinGit(`mingit\cmd\git.exe`)
- 构建命令:`$env:JAVA_HOME='D:\dev-tools\jdk-17.0.20.1+1'; .\gradlew.bat :app:assembleDebug`
- **所有下载必须优先国内镜像**(用户硬性要求):Gradle→腾讯,Maven→阿里云(settings.gradle.kts 已配),工具→npmmirror/华为云;GitHub 直连不通,要走 gh-proxy.com 等代理

## 模块
- `terminal-emulator/`、`terminal-view/`:**vendor 自 termux-app(GPLv3)**,基线 master@2026-08-22。
  改动最小化原则,便于同步上游。已改:删 JNI、`TerminalSession` 抽象为传输无关基类(onTransport* SPI)、
  `TerminalView.onCreateInputConnection` 重写(TYPE_CLASS_TEXT,中文 IME 核心)+ 预编辑悬浮绘制。
  其余 vendored 文件不要动。
- `app/`:Kotlin + Compose。`ssh/SshTerminalSession` 是传输实现(sshlib/trilead);
  重连用 generation 递增让旧 IO 线程自然退出;emulator 只建一次,断线重连不重建(保留滚回历史)。

## 关键约束
- 项目整体 GPLv3(vendor 传染);sshlib 是 Apache 2.0 兼容
- 预编辑(拼音组合串)绝不发往远端,只本地绘制;commitText 才发送
- 断线保活的根本机制是服务端 `tmux -u new-session -A -s vtN`,客户端保活只是尽力层
