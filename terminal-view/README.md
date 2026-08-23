# terminal-view(vendored)

Fork 自 [termux/termux-app](https://github.com/termux/termux-app) 的 `terminal-view` 模块
(master 快照,2026-08-22)。上游版权归 Termux 及 Terminal Emulator for Android
(jackpal,Apache-2.0)各贡献者所有,许可信息见根目录 [NOTICE.md](../NOTICE.md)。

## 本项目所做修改(GPLv3 §5 变更声明)

全部修改集中在 `TerminalView.java`,均以 `VibeTerm` 注释标记:

- **`onCreateInputConnection()` 重写**——本项目的核心差异化:
  - 上游默认声明 `TYPE_NULL`(仅原始按键),导致中文/日文/韩文输入法退化为纯英文模式;
    本 fork 始终声明 `TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_NO_SUGGESTIONS`,让 CJK 输入法启用组合文本;
  - 刻意**不使用** `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`(上游治三星键盘的旧方案):密码类
    variation 会让中文输入法强制英文键盘,MIUI 还会进入安全输入模式导致截图全黑(真机实测);
  - 新增 `setComposingText / setComposingRegion / performEditorAction` 处理。
- **IME 预编辑悬浮渲染**:组合中的拼音串绝不发往远端,在光标处绘制本地悬浮预览
  (`drawImePreedit()`),上屏(commit)才写入 SSH 通道。
- **`onKeyPreIme()` 拦截 Ctrl+空格**:抢在输入法之前把系统惯例的切输入法快捷键交给客户端
  (呼出输入法选择器);终端语义下的 NUL 改用 Ctrl+2 输入。

其余文件(渲染、手势、滚动、文本选择)零修改。
