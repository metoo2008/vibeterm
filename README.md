<div align="center">

# ❯ VibeTerm

**An Android SSH terminal built for vibe coding — run Claude Code / Codex comfortably from your phone or tablet.**

*Full Android IME / Unicode input (Chinese, Japanese, Korean, Indic, Arabic and more), tmux-backed session persistence, multi-window & split-screen, and lock-screen approval for AI coding agents.*

GPL-3.0 · minSdk 26 (Android 8.0+) · Kotlin + Jetpack Compose

**English** · [中文](README.zh-CN.md)

</div>

---

## Why another SSH client?

Three problems that mainstream Android SSH clients never fix:

1. **You can't type Chinese — or, really, most non-English text.** Terminal-style apps tend to act like a *keyboard device*: they either declare `InputType.TYPE_NULL` (raw key events only) or accept only `KeyEvent`s and ignore text the IME delivers via `commitText()`. The result: **Chinese, Japanese, Korean, Hindi, Arabic, Russian… every language that relies on IME composition or non-ASCII Unicode is broken**, not just Chinese. VibeTerm **implements Android's IME / InputConnection contract properly** (real `TYPE_CLASS_TEXT` + `setComposingText` preedit + `commitText` → UTF-8 per Unicode code point) while still passing hardware control keys (Ctrl/Alt/Esc/Tab) through. This is a far more general fix than "Chinese support" — see [IME & language support](#ime--language-support). Verified on-device with Gboard/Sogou/Baidu/iFlytek.
2. **Switching windows is painful.** Vibe coding means watching several sessions at once. VibeTerm gives you tabbed multi-sessions, side-by-side split-screen on wide screens, and connections that live in a foreground service so they survive rotation and backgrounding.
3. **Disconnects kill your jobs.** Close the app or change networks and that 30-minute task is gone. VibeTerm wraps each window in `tmux -u new-session -A -D`, so **killing the app, switching WiFi↔cellular, or rebooting your phone all reconnect straight back into the same session** — the server holds the state.

## Built for AI coding agents

- 🔔 **Task-done notifications** — terminal bell + a "busy-then-silent" heuristic, so you know when the AI finishes even with the screen off.
- ✅ **Lock-screen approval** — when a confirmation prompt (`y/n`, `Do you want…`) is detected, the notification carries **Confirm (Enter) / Interrupt (Esc)** actions to approve an agent's tool call. Device authentication is required by default (a fingerprint tap) so someone holding your phone can't approve for you; it can be switched to no-auth in settings (only Android 12+ can truly enforce authentication).
- ⌘ **Quick-command palette** — one tap to send `claude -c`, `/compact`, `git status`, and your own custom commands.
- ⌨️ **Extra-keys bar** — Esc (interrupt), **Shift+Tab** (Claude Code mode switch), latching Ctrl/Alt, arrows, bracketed-safe paste.
- 🚀 **Cold-start session restore** — reopen the app and every previous window reconnects to its own tmux session.
- 🌐 **Instant reconnect on network change** — watches the system default network and rebuilds the connection immediately instead of waiting for a timeout.
- 🎨 JetBrains Mono font + GitHub-Dark palette; Ctrl+Space switches IME (the hardware-keyboard convention).

## IME & language support

VibeTerm doesn't fix "Chinese input" — it fixes **Android terminal IME/InputConnection input in general**. Any keyboard that commits text through the standard `setComposingText`/`commitText` path works: VibeTerm sends it to SSH as UTF-8 per Unicode code point (including surrogate pairs beyond the BMP). So all of these benefit — not only Chinese:

| Tier | Languages | Notes |
|---|---|---|
| Composition + candidate conversion (most affected) | **Chinese** (Pinyin/Zhuyin), **Japanese** (kana→kanji) | Best showcase; Chinese verified on-device, Japanese uses the same mechanism |
| Composition | **Korean** (Hangul), **Hindi/Bengali/Tamil** and other Indic scripts, transliteration IMEs | Composing text is previewed locally, committed on selection |
| Non-ASCII Unicode commit | **Russian/Ukrainian/Greek**, **Arabic/Persian/Hebrew**, **Thai/Vietnamese**, accented **French/German/Spanish** | No candidate picking, but the IME must hand Unicode to the app |
| Baseline | English / ASCII | Always worked |

**Input vs. display (an honest note):** VibeTerm makes **input** correct — the bytes reach your server. **Display** follows the usual terminal-emulator limits: CJK wide characters and combining marks render fine, but right-to-left scripts (Arabic/Hebrew) and complex Indic ligatures are drawn cell-by-cell left-to-right, as in virtually all terminal emulators. So these scripts **input correctly and arrive at the server**, but may render in a simplified / left-to-right form in the terminal.

> Tested: the "multi-language input" screenshot above shows Chinese, Japanese, Korean, Hindi, and Arabic entered via the IME `commitText` path and echoed back by SSH (the same Android API that Gboard/Baidu use to commit text); Chinese candidate composition was additionally verified end-to-end on a real device (Baidu IME). Reports for other IMEs are welcome.

See [docs/FAQ.md](docs/FAQ.md) for why Android SSH terminals can't type CJK/Unicode, in several languages.

## Screenshots

| Multi-language input (zh / ja / ko / hi / ar) | Lock-screen approval |
|---|---|
| ![multi-language input](docs/screenshots/language-input.png) | ![lock-screen approval](docs/screenshots/lockscreen-approve.png) |

| Terminal | Tablet split-screen |
|---|---|
| ![terminal](docs/screenshots/terminal.png) | ![split-screen](docs/screenshots/split-screen.png) |

## Server requirements

- A standard Linux box with sshd and a UTF-8 locale (e.g. `en_US.UTF-8` / `zh_CN.UTF-8`).
- `tmux` installed (optional, but required for keep-alive across disconnects).
- Recommended: set `preferredNotifChannel: terminal_bell` in Claude Code for precise task-done notifications.

## Install

- **GitHub Releases** — download the signed APK ([Releases](https://github.com/metoo2008/vibeterm/releases)). Current channel.
- **F-Droid** — planned (build recipe and store assets are ready, see [docs/FDROID.md](docs/FDROID.md)); once listed, search "VibeTerm" in the F-Droid client.

> Note: this project vendors Termux's GPLv3 terminal engine, so the whole app is **GPL-3.0**. GPLv3 has a known conflict with Google Play's distribution terms, and that engine's copyright belongs to the Termux authors — so VibeTerm is **not published on Google Play**, only through GPL-friendly channels (F-Droid / GitHub). The F-Droid and GitHub APKs are signed with different keys and cannot upgrade over each other; pick one channel.

## Build

```bash
# Requires JDK 17 and the Android SDK (compileSdk 36, Android 16)
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

> Official Maven repositories are used by default (clean builds for F-Droid / CI / everyone). Developers in mainland China can set `VIBETERM_CN_MIRROR=true` to enable the Aliyun mirror; the Gradle wrapper uses the Tencent mirror (same content as official, pinned with a SHA-256 checksum).

## Architecture

See [docs/DESIGN.md](docs/DESIGN.md) for the full decision log and milestone verification. In short:

- `terminal-emulator/`, `terminal-view/` — vendored from [termux/termux-app](https://github.com/termux/termux-app) with two sets of patches: the local PTY/JNI is replaced with an abstract transport layer (SSH), and the IME input layer is rewritten for native CJK/Unicode input. Per-module READMEs list the changes (GPLv3 §5 change notices).
- `app/` — Kotlin + Jetpack Compose. SSH transport uses ConnectBot's [sshlib](https://github.com/connectbot/sshlib); reconnection uses a per-generation transport so stale I/O threads exit cleanly, the emulator is created once (scrollback preserved), passwords are encrypted with Android Keystore AES-256-GCM, and host keys are pinned TOFU.

## Roadmap

- [ ] SSH key authentication (ed25519)
- [ ] Browse/attach any existing tmux session on the server
- [ ] mosh transport (layered with tmux for flaky networks)
- [ ] Arrow-key auto-repeat, jump-to-bottom button, customizable key bar

## License & credits

Released under **GPL-3.0-only** (it vendors Termux's terminal core, see [LICENSE](LICENSE)). Third-party components are listed in [NOTICE.md](NOTICE.md). Special thanks to
[Termux](https://github.com/termux/termux-app),
[jackpal/Android-Terminal-Emulator](https://github.com/jackpal/Android-Terminal-Emulator),
[ConnectBot sshlib](https://github.com/connectbot/sshlib), and
[JetBrains Mono](https://www.jetbrains.com/lp/mono/).

Issues and PRs welcome — please make sure `./gradlew :app:assembleDebug` passes first.
