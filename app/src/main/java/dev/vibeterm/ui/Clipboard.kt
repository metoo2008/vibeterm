package dev.vibeterm.ui

import android.content.ClipboardManager
import android.content.Context
import com.termux.terminal.TerminalEmulator

/**
 * 有界读取剪贴板的统一入口(三处粘贴共用,避免再遗漏)。
 *
 * 关键:**只取 `item.text`**,不调 `coerceToText()`。coerceToText 会为 URI 型剪贴板经
 * ContentProvider 读取整段文本流并复制成 String——恶意/异常 Provider 可借此在主线程制造巨大
 * 分配(paste() 的字符上限拦不住,因为那时大 String 已经存在)。因此 URI/Intent 型直接拒绝。
 * 长度检查也发生在 `.toString()` 之前。
 */
object Clipboard {

    sealed interface Result {
        data class Text(val text: String) : Result
        data object Empty : Result        // 剪贴板为空
        data object Unsupported : Result  // 非纯文本(URI/Intent),拒绝
        data object TooLarge : Result     // 超过粘贴上限,整次拒绝(不截断)
    }

    /** 纯逻辑分类,便于单测:在 toString 之前按 CharSequence 长度判定。 */
    fun classify(text: CharSequence?): Result = when {
        text == null -> Result.Unsupported
        text.isEmpty() -> Result.Empty
        text.length > TerminalEmulator.MAX_PASTE_CHARS -> Result.TooLarge
        else -> Result.Text(text.toString())
    }

    fun read(context: Context): Result {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return Result.Empty
        val clip = cm.primaryClip ?: return Result.Empty
        if (clip.itemCount == 0) return Result.Empty
        // 只看 item.text;URI/Intent 型 text 为 null → Unsupported,不触碰 ContentProvider
        return classify(clip.getItemAt(0)?.text)
    }
}
