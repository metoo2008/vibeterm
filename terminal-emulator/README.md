# terminal-emulator(vendored)

Fork 自 [termux/termux-app](https://github.com/termux/termux-app) 的 `terminal-emulator` 模块
(master 快照,2026-08-22)。上游版权归 Termux 及 Terminal Emulator for Android
(jackpal,Apache-2.0)各贡献者所有,许可信息见根目录 [NOTICE.md](../NOTICE.md)。

## 本项目所做修改(GPLv3 §5 变更声明)

- **删除 `JNI.java` 及全部本地 PTY/子进程支持**(含 NDK 依赖)——VibeTerm 是纯 SSH 客户端,不运行本地 shell。
- **`TerminalSession.java` 重写为传输无关的抽象基类**:
  - 移除 fork/waitpid/文件描述符逻辑;
  - 新增传输 SPI:`onTransportStart / onTransportWrite / onTransportResize / onTransportKill`;
  - 新增传输回调:`onTransportData(bytes)`(任意线程喂远端数据)、`onTransportExited(status, message)`;
  - 保留上游线程模型:传输线程写入 `ByteQueue`,终端仿真始终在主线程执行。
- 其余文件(`TerminalEmulator`、`TerminalBuffer`、`KeyHandler`、`WcWidth` 等)**零修改**,便于跟进上游。

SSH 传输实现位于 app 模块:`dev.vibeterm.ssh.SshTerminalSession`。
