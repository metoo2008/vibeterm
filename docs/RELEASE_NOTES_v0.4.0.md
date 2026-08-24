# VibeTerm v0.4.0

In-app UI language switching. The interface is no longer tied to the system locale — pick your language in Settings.

*界面多语言切换。App 界面不再绑定系统语言,可在设置里自选。*

## ✨ What's new

- **In-app language switch** — the app UI ships in **English, 简体中文, 日本語, and 한국어**. Choose it in **Settings → Language** (Follow system / English / 简体中文 / 日本語 / 한국어); it also appears in the Android 13+ per-app language setting. Defaults to following the system locale.
- Settings dialog is now scrollable (so every option is reachable on short screens / in landscape).

*新增:App 内语言切换,界面提供 English / 简体中文 / 日本語 / 한국어,在「设置 → 语言」里切换(也支持 Android 13+ 的按应用语言设置),默认跟随系统;设置对话框改为可滚动。*

> Note: this switches the **app's own UI language**. Typing CJK/Unicode **into the terminal** already worked in previous versions (that's the core IME feature) and is unchanged.

## 📦 Everything from v0.3.2

Full Android IME / Unicode input (Chinese, Japanese, Korean, Indic, Arabic and more), tmux disconnect keep-alive, multi-window + tablet split-screen, lock-screen approval for AI agents, quick-command palette, task-done notifications, cold-start session restore, instant reconnect on network change. Android 8.0+ (API 26), tested up to Android 16.

## 🔒 Privacy & security

- No data collection, no ads, no telemetry; passwords are stored only on-device, encrypted with the Android Keystore; connects only to servers you configure.
- Shows each server's SHA256 fingerprint on first connect; the password is sent only after you confirm.
- Hardened across multiple independent security-audit rounds (see [SECURITY.md](https://github.com/metoo2008/vibeterm/blob/master/SECURITY.md)).

## 📥 Install

Download `vibeterm-0.4.0-release.apk` below, allow "install unknown apps", and install. Requires Android 8.0 (API 26)+.

**SHA-256** (verify your download matches this page's binary; the APK is not byte-reproducible, so this value corresponds to the uploaded file):
```
8147708794d622d3282be4d6b0abc54857b09387abc35cb86d45387eda49a8c6
```

**Server tips:** install tmux (for disconnect keep-alive) and a UTF-8 locale; set `preferredNotifChannel: terminal_bell` in Claude Code for precise done-notifications.

## ⚠️ Notes

- **GPL-3.0** (vendors the Termux terminal engine), distributed via **F-Droid + GitHub**, not Google Play.
- The F-Droid and GitHub APKs are signed with different keys and can't upgrade over each other — pick one channel.
- The first connection shows a host-fingerprint confirmation dialog — this is by design; verify before trusting.

---

GPL-3.0 · source & docs in the repo · issues and PRs welcome
