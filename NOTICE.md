# 第三方组件与致谢 / Third-Party Notices

VibeTerm 整体以 **GPL-3.0-only** 发布(见 [LICENSE](LICENSE))。它站在下列优秀开源项目的肩膀上:

## 内嵌源码(vendored)

### termux/termux-app — `terminal-emulator/`、`terminal-view/`
- 来源:<https://github.com/termux/termux-app>(master 快照,2026-08-22)
- 许可:termux-app 整体为 GPL-3.0-only;其 LICENSE 注明 `terminal-emulator` 与 `terminal-view`
  两个库包含源自 [jackpal/Android-Terminal-Emulator](https://github.com/jackpal/Android-Terminal-Emulator)
  的 Apache-2.0 代码
- 本项目对这两个模块的修改清单见各模块内 `README.md`(依 GPLv3 §5 声明变更)

## 依赖库

### ConnectBot sshlib
- <https://github.com/connectbot/sshlib> · Apache-2.0
- SSH 传输层(基于 Trilead SSH2)

### AndroidX / Jetpack Compose
- Apache-2.0

## 字体

### JetBrains Mono (No Ligatures)
- <https://www.jetbrains.com/lp/mono/> · SIL Open Font License 1.1
- 许可全文:`app/src/main/assets/fonts/JetBrainsMono-OFL.txt`
