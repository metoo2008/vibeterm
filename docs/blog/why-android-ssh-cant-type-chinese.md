# Why you can't type Chinese or Japanese in Android SSH terminals — and how to fix it

*A deep dive into `TYPE_NULL`, `InputConnection`, and `commitText()` — and why switching keyboards never helps.*

> 📝 Published on dev.to: <https://dev.to/metooyang2008/why-you-cant-type-chinese-or-japanese-in-android-ssh-terminals-and-how-to-fix-it-p2a> — please treat that as the canonical version.

If you've ever opened an SSH client on Android, switched to a Chinese, Japanese, or Korean keyboard, and watched your carefully-composed text simply **not appear** in the shell — you're not imagining it, and it's not your keyboard's fault. It's a bug in how most terminal apps handle text input, and once you understand it, the fix is obvious.

This post explains the root cause, why it affects far more languages than just Chinese, why changing your IME doesn't help, and how to implement it correctly.

## The symptom

You SSH into a server that fully supports UTF-8. You tap the terminal, your keyboard pops up, you switch to Pinyin, you type `nihao`, the candidate bar shows 你好 — and when you tap it, **nothing** reaches the terminal. English works fine. The keyboard clearly works in every other app. But in this one, non-English text evaporates.

## Two layers of Android text input

To see why, you need to know that Android has **two completely different input paths**, and a terminal has to deal with both:

1. **Hardware / raw key events** — `KeyEvent`s delivered to `View.onKeyDown()`. This is how a terminal gets `Ctrl`, `Alt`, `Esc`, `Tab`, arrow keys, function keys — the control keys a shell needs.
2. **The input method (IME) framework** — the on-screen keyboard talks to your app through an `InputConnection`. This is how *text* is entered, including everything that needs composition:

   ```
   pinyin:   nihao → [candidate bar] → 你好   → commitText("你好")
   japanese: nihongo → にほんご → 日本語        → commitText("日本語")
   ```

The IME never sends a `KeyEvent` for 你好 — there is no "你好 key." It sends the finished text through `InputConnection.commitText()`, after showing you a live preview via `setComposingText()`.

## The root cause: terminals that behave like a keyboard, not a text field

Terminal apps lean heavily on path #1 because they *must* — a shell is useless without `Ctrl-C`, `Tab`, and `Esc`. So many implementations take a shortcut and treat the terminal as a **keyboard device** rather than a real text editor. That shortcut shows up in one of two ways:

### Failure mode A: declaring `TYPE_NULL`

```java
@Override
public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    outAttrs.inputType = InputType.TYPE_NULL; // "just give me raw key events"
    return new BaseInputConnection(this, false);
}
```

`TYPE_NULL` tells the IME: *this field doesn't accept composed text; send me key events instead.* Modern IMEs respond by falling back to a **Latin-only, no-composition** mode. The Chinese/Japanese candidate machinery is disabled at the source. You can still switch to the Chinese keyboard visually, but it can't compose — so nothing meaningful gets committed.

### Failure mode B: ignoring `commitText()`

Even with a non-null input type, some terminals only wire up `KeyEvent` handling and never properly handle the `InputConnection` callbacks. The IME calls `commitText("你好")`, the app… does nothing with it. The text is dropped on the floor.

Either way, the observable result is identical: **English (which arrives as key events) works; anything that arrives via `commitText()` doesn't.**

## Why switching keyboards doesn't help

This is the part that trips everyone up. People try Gboard, then Sogou, then Baidu, then iFlytek — same result — and conclude their phone is broken.

But the bug is in the **app's `InputConnection`**, not the keyboard. No IME can deliver composed text to a field that declared `TYPE_NULL` or that throws away `commitText()`. The keyboard is doing its job correctly; the app refuses to listen.

## It's not a "Chinese" problem — it's an IME problem

Because the real issue is the IME text path, the blast radius is huge. Anything that isn't a direct ASCII keypress is affected:

| Category | Languages | Why |
|---|---|---|
| Composition + candidate conversion | Chinese (Pinyin/Zhuyin), Japanese (kana→kanji) | Rely entirely on composing text + candidates |
| Composition | Korean (Hangul), Hindi/Bengali/Tamil, transliteration IMEs | Build characters via composition |
| Non-ASCII Unicode commit | Russian, Greek, Arabic, Persian, Hebrew, Thai, Vietnamese, accented French/German/Spanish | No candidates, but still delivered via `commitText()` |
| Works everywhere | English / ASCII | Arrives as key events |

If you build "Chinese input" and stop there, you've solved a sliver of the actual problem. The correct framing is **full Android IME / Unicode input**.

## How to fix it

The terminal has to serve **both** input paths at once: keep raw key events for control keys, *and* implement the `InputConnection` contract for text.

**1. Declare a real text input type** (not `TYPE_NULL`):

```java
@Override
public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    // A real editable text field, so IMEs enable composition for CJK & friends.
    // NO_SUGGESTIONS avoids autocorrect meddling with commands.
    outAttrs.inputType = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
            | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
    return new TerminalInputConnection(this, /* fullEditor = */ true);
}
```

> A subtle trap: don't reach for `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` to "stabilize" the keyboard. Password variations make many CJK IMEs force a Latin-only layout — and on some OEM skins (e.g. MIUI) put the field into a secure mode. `NO_SUGGESTIONS` alone is enough to stop autocorrect.

**2. Handle composing text — but keep it local.** The composing (preedit) string is *not* final; the remote shell has no concept of "text being composed." So draw it yourself at the cursor and only send text on commit:

```java
@Override
public boolean setComposingText(CharSequence text, int newCursorPosition) {
    boolean r = super.setComposingText(text, newCursorPosition);
    updatePreeditOverlay();  // draw the in-progress "nihao"/"にほん" near the cursor
    return r;                // do NOT send to the remote yet
}

@Override
public boolean commitText(CharSequence text, int newCursorPosition) {
    super.commitText(text, newCursorPosition);
    sendToTerminal(getEditable());  // now it's final → send it
    getEditable().clear();
    clearPreeditOverlay();
    return true;
}
```

**3. Encode by Unicode code point, not `char`.** Java strings are UTF-16; emoji and many CJK extension characters are surrogate pairs. Iterate code points so you don't split them:

```java
void sendToTerminal(CharSequence text) {
    int n = text.length();
    for (int i = 0; i < n; i++) {
        char c = text.charAt(i);
        int cp;
        if (Character.isHighSurrogate(c) && i + 1 < n) {
            cp = Character.toCodePoint(c, text.charAt(++i));
        } else {
            cp = c;
        }
        writeCodePointAsUtf8(cp);  // → SSH
    }
}
```

**4. Leave the key-event path intact.** `Ctrl`, `Alt`, `Esc`, `Tab`, arrows, `Ctrl-C` etc. still flow through `onKeyDown()`/your key handler. The two paths coexist: control keys via `KeyEvent`, text via `InputConnection`.

That's the whole fix. The moment the app declares itself a real text field and honors `commitText()`, every IME — Gboard, Sogou, Baidu, a Hindi or Arabic keyboard — starts working, because you're finally speaking the protocol they've been speaking all along.

## One honest caveat: input vs. display

Getting the bytes *in* is solved by the above. **Displaying** complex scripts is a separate, older terminal-emulator limitation: CJK wide characters and combining marks are fine, but right-to-left scripts (Arabic, Hebrew) and complex Indic ligatures are typically rendered cell-by-cell, left-to-right, without bidi reordering — in almost every terminal emulator, not just Android ones. So Arabic input reaches your server correctly; it may just look simplified in the terminal view. Worth stating plainly so you're not surprised.

## Wrapping up

"I can't type Chinese in my SSH client" is really "this terminal declared `TYPE_NULL` / ignores `commitText()`." It's an app bug, not a keyboard problem, and it quietly breaks a dozen writing systems, not one. The fix is to implement Android's `InputConnection` contract properly while keeping the raw key-event path for control keys.

---

*I hit this exact wall doing "vibe coding" from my phone — running Claude Code and Codex over SSH — so I built **[VibeTerm](https://github.com/metoo2008/vibeterm)**, an open-source (GPL-3.0) Android SSH terminal with the IME implementation described above, plus tmux-backed reconnection, split-screen, and lock-screen approval for AI agents. (Disclosure: I'm the author.) If you've been fighting this bug, I'd love your feedback.*
