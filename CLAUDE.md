# VibeTerm — 为 vibe coding 优化的 Android SSH 终端

## 项目定位
Android SSH 客户端,核心卖点:原生中文输入(自研 IME InputConnection)、多窗口/分屏、tmux 自动断线保活、
锁屏批准 AI 代理。设计文档在 `docs/DESIGN.md`,**开发以它为准绳,完成里程碑要更新其中的勾选状态**。

## 构建
- JDK 17 + Android SDK(compileSdk 36):`./gradlew :app:assembleDebug`(AGP 8.11.1 / Gradle 8.13)
- Maven 仓库**默认官方**(F-Droid/CI 干净);国内加速设环境变量 `VIBETERM_CN_MIRROR=true`。Gradle wrapper 用腾讯镜像(有 SHA-256 校验)
- 机器相关的路径与环境写在 `CLAUDE.local.md`(不入库)

## 分发(重要)
- GPL-3.0(内嵌 Termux 引擎)。**不上 Google Play**(GPLv3×Play 条款冲突 + Termux 版权非本人),只走 **F-Droid + GitHub Releases**;流程见 `docs/FDROID.md`。
- 新增依赖前必须确认是自由软件许可(不能引入 Google 专有库),否则 F-Droid 拒收。

## 模块
- `terminal-emulator/`、`terminal-view/`:**vendor 自 termux-app(GPLv3,基线 master@2026-08-22)**。
  改动最小化原则,便于同步上游;修改清单见各模块 README(GPLv3 §5 要求)。
  已改:删 JNI、`TerminalSession` 抽象为传输无关基类(onTransport* SPI)、
  `TerminalView` IME 层重写(TYPE_CLASS_TEXT + 预编辑悬浮 + Ctrl+空格拦截)。其余 vendored 文件不要动。
- `app/`:Kotlin + Compose。`ssh/SshTerminalSession` 是传输实现(sshlib/trilead);
  重连用 generation 递增让旧 IO 线程自然退出;emulator 只建一次,断线重连不重建(保留滚回历史)。

## 关键约束(改代码前必读)
- 项目整体 GPL-3.0-only(vendor 传染);新增依赖须 GPL 兼容
- 终端 inputType **绝不能加 `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`**:中文输入法会强制英文键盘,
  MIUI 进入安全输入模式截图全黑(真机验证过的教训)
- 预编辑(拼音组合串)绝不发往远端,只本地绘制;commitText 才发送
- 断线保活的根本机制是服务端 `tmux -u new-session -A -D -s vtN`(-D 踢幽灵客户端防窗口尺寸拖小);
  客户端保活只是尽力层
- Kotlin 字符串里的 ESC 必须写 `\u001b`,不要嵌入字面控制字符
