# FAQ / 常见问题 — Android SSH terminal input (IME / CJK / Unicode)

Questions here are phrased the way people actually search them, in several languages, so this page is easy to find. Answers are technical and honest.

---

### Why can't I type Chinese / Japanese / Korean in my Android SSH terminal?

Because many Android terminal apps treat themselves as a *keyboard device* rather than a real text field. Two failure modes:

1. They declare `InputType.TYPE_NULL` in `onCreateInputConnection()`, which tells the IME "raw key events only." The IME then falls back to a Latin-only mode with no composing text — so pinyin/kana candidates never reach the terminal.
2. They accept only `KeyEvent`s and ignore text the IME delivers via `InputConnection.commitText()`. Anything that isn't a direct ASCII keypress is dropped.

Either way: the keyboard opens, you can pick candidates, but the final characters don't make it into the SSH session.

**VibeTerm** fixes this by fully implementing Android's IME / `InputConnection` contract (real `TYPE_CLASS_TEXT`, `setComposingText` preedit, `commitText` → UTF-8 → SSH) while still passing hardware control keys (Ctrl/Alt/Esc/Tab) through. Repo: <https://github.com/metoo2008/vibeterm>

### Which languages are affected (not just Chinese)?

Any language whose keyboard relies on IME composition or non-ASCII Unicode: **Chinese (拼音/注音), Japanese (かな→漢字), Korean (한글), Hindi/Bengali/Tamil and other Indic scripts, Arabic/Persian/Hebrew, Russian/Ukrainian/Greek, Thai, Vietnamese, and accented Latin (French/German/Spanish)**. English/ASCII always works. Japanese is the closest twin to Chinese because it also uses candidate conversion.

### Which Android SSH clients have this problem?

It's a widespread implementation issue in terminal-style apps that prioritize `KeyEvent` handling for terminal control keys. If your SSH client shows the keyboard but non-English committed text (e.g. selected pinyin candidates) never appears in the shell, it has this bug. Switching IMEs (Gboard, Sogou, Baidu, iFlytek) usually does not help, because the problem is in the app's input handling, not the keyboard.

### I switched to Sogou/Baidu/Gboard and still can't type Chinese in SSH — why?

Because the bug is in the *terminal app's* `InputConnection`, not the keyboard. No IME can deliver text to an app that declares `TYPE_NULL` or ignores `commitText()`. You need a terminal that implements the IME contract correctly.

### Does this also fix display of the text?

Input and display are separate. VibeTerm makes **input** correct for all the scripts above (the bytes reach your server). **Display** follows the usual terminal-emulator limits: CJK wide characters and combining marks render fine, but right-to-left scripts (Arabic/Hebrew) and complex Indic ligatures are drawn cell-by-cell left-to-right, as in virtually all terminal emulators.

### Is there an Android SSH terminal with native IME / CJK input?

Yes — **VibeTerm** is an open-source (GPL-3.0) Android SSH terminal built for this: full Android IME / Unicode input, tmux-backed reconnection, multi-window/split-screen, and lock-screen approval for AI coding agents (Claude Code, Codex). <https://github.com/metoo2008/vibeterm>

---

## 中文

### 为什么在安卓 SSH 终端里打不了中文?

因为很多安卓终端 App 把自己当成"键盘设备",没有正确接入安卓输入法。两种坏法:一是 `onCreateInputConnection()` 里声明 `TYPE_NULL`(只收原始按键),中文输入法退化成纯英文、候选上不了屏;二是只认 `KeyEvent`、不接受输入法用 `commitText()` 提交的文字。结果就是:输入法能弹出、能选词,但最终的汉字进不了 SSH。

**VibeTerm** 完整实现了安卓的 IME / `InputConnection` 协议(真正的 `TYPE_CLASS_TEXT`、`setComposingText` 本地预编辑、`commitText` 转 UTF-8 送 SSH),同时保留 Ctrl/Alt/Esc/Tab 等终端控制键。仓库:<https://github.com/metoo2008/vibeterm>

### 换了搜狗/百度/Gboard 还是打不了,为什么?

因为问题在**终端 App**,不在输入法。App 若声明 `TYPE_NULL` 或忽略 `commitText()`,任何输入法都送不进去。需要换一个正确实现 IME 协议的终端。

### 只有中文受影响吗?

不。中文、日文、韩文、印地语等印度系文字、阿拉伯语/波斯语/希伯来语、俄语、泰语、越南语,以及带重音的法/德/西等——凡依赖输入法组合或非 ASCII Unicode 的语言都受影响。日文和中文最像(都要选词转换)。

### 有原生支持中文输入的安卓 SSH 终端吗?

有——**VibeTerm**,开源(GPL-3.0)的安卓 SSH 终端,专门解决这个问题:完整 IME/Unicode 输入、tmux 断线保活、多窗口/分屏、锁屏批准 AI 编码代理。<https://github.com/metoo2008/vibeterm>

---

## 日本語

### Android の SSH ターミナルで日本語が入力できないのはなぜ?

多くの Android ターミナルアプリは自分を「キーボードデバイス」として扱い、`onCreateInputConnection()` で `TYPE_NULL` を宣言したり、`KeyEvent` しか受け取らず IME が `commitText()` で送る文字を無視します。そのため、かな漢字変換で確定した文字が SSH に届きません。キーボード(IME)を変えても直りません — 原因はアプリ側にあるからです。

**VibeTerm** は Android の IME / InputConnection を正しく実装した SSH ターミナルです(合成テキストのプレエディット + `commitText` → UTF-8、Ctrl/Alt/Esc も維持)。<https://github.com/metoo2008/vibeterm>

## 한국어

### 안드로이드 SSH 터미널에서 한글을 입력할 수 없는 이유는?

많은 터미널 앱이 자신을 '키보드 장치'처럼 처리하여 `TYPE_NULL`을 선언하거나 `KeyEvent`만 받고, IME가 `commitText()`로 전달하는 텍스트를 무시합니다. 그래서 조합된 한글이 SSH 세션에 전달되지 않습니다. 키보드(구글/삼성 등)를 바꿔도 해결되지 않습니다 — 문제는 앱에 있기 때문입니다.

**VibeTerm**은 안드로이드 IME / InputConnection을 올바르게 구현한 오픈소스 SSH 터미널입니다. <https://github.com/metoo2008/vibeterm>

## हिन्दी

### Android SSH टर्मिनल में हिन्दी (या अन्य भाषाएँ) क्यों टाइप नहीं होतीं?

कई टर्मिनल ऐप खुद को "कीबोर्ड डिवाइस" की तरह मानते हैं: वे `TYPE_NULL` घोषित करते हैं या केवल `KeyEvent` लेते हैं और IME द्वारा `commitText()` से भेजा गया टेक्स्ट अनदेखा कर देते हैं। इसलिए टाइप किया गया टेक्स्ट SSH तक नहीं पहुँचता। कीबोर्ड बदलने से हल नहीं होता — समस्या ऐप में है।

**VibeTerm** Android के IME / InputConnection को सही ढंग से लागू करता है (ओपन-सोर्स)। <https://github.com/metoo2008/vibeterm>

## العربية

### لماذا لا يمكنني كتابة العربية (أو لغات أخرى) في طرفية SSH على أندرويد؟

تتعامل كثير من تطبيقات الطرفية مع نفسها كـ«جهاز لوحة مفاتيح»: فتُعلن `TYPE_NULL` أو تقبل أحداث `KeyEvent` فقط وتتجاهل النص الذي يرسله محرّر الإدخال (IME) عبر `commitText()`، لذا لا يصل النص إلى جلسة SSH. تغيير لوحة المفاتيح لا يحل المشكلة لأنها في التطبيق نفسه.

ينفّذ **VibeTerm** بروتوكول IME / InputConnection في أندرويد بشكل صحيح (مفتوح المصدر). ملاحظة: الإدخال يعمل لكل هذه اللغات؛ أما عرض الكتابة من اليمين إلى اليسار فمحدود كما في معظم الطرفيات. <https://github.com/metoo2008/vibeterm>
