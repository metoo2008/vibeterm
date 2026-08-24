package dev.vibeterm.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibeterm.R

/** 附加键条按键。Claude Code 刚需:Esc 打断、Shift+Tab 切模式、方向键、Ctrl 组合。 */
enum class BarKey(val label: String) {
    ESC("Esc"),
    CTRL("Ctrl"),
    ALT("Alt"),
    TAB("Tab"),
    SHIFT_TAB("⇧Tab"),
    UP("↑"),
    DOWN("↓"),
    LEFT("←"),
    RIGHT("→"),
    PGUP("PgUp"),
    PGDN("PgDn"),
    DASH("-"),
    SLASH("/"),
    PIPE("|"),
    CMDS("⌘"),
    PASTE("Paste"), // 显示时用 stringResource(R.string.key_paste) 覆盖,此处仅为占位
    KEYBOARD("⌨"),
}

@Composable
fun ExtraKeysBar(
    ctrlActive: Boolean,
    altActive: Boolean,
    onKey: (BarKey) -> Unit,
    onKeyLongPress: (BarKey) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        BarKey.entries.forEach { key ->
            val active = (key == BarKey.CTRL && ctrlActive) || (key == BarKey.ALT && altActive)
            // 只有「粘贴」是文字标签需本地化;其余为符号/ASCII,跨语言一致
            val label = if (key == BarKey.PASTE) stringResource(R.string.key_paste) else key.label
            KeyButton(
                label = label,
                active = active,
                onClick = { onKey(key) },
                // ⌨ 长按呼出系统输入法选择器:接实体键盘时导航栏不显示切换图标,这是唯一入口
                onLongClick = if (key == BarKey.KEYBOARD) ({ onKeyLongPress(key) }) else null,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            // 键条按钮不参与硬件键盘焦点链,终端界面里实体键盘专属 TerminalView
            .focusProperties { canFocus = false }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}
