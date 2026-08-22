package dev.vibeterm.ui.theme

import com.termux.terminal.TerminalColors
import java.util.Properties

/**
 * GitHub Dark 风格的终端 ANSI 配色,与 App 主题(0D1117 底、7EE787 强调绿)统一。
 * 进程启动时应用一次到全局静态方案,之后创建的所有 emulator 生效。
 */
fun applyTerminalPalette() {
    val props = Properties().apply {
        setProperty("background", "#0D1117")
        setProperty("foreground", "#E6EDF3")
        setProperty("cursor", "#7EE787")
        // 标准 8 色
        setProperty("color0", "#484F58")
        setProperty("color1", "#FF7B72")
        setProperty("color2", "#3FB950")
        setProperty("color3", "#D29922")
        setProperty("color4", "#58A6FF")
        setProperty("color5", "#BC8CFF")
        setProperty("color6", "#39C5CF")
        setProperty("color7", "#B1BAC4")
        // 亮 8 色
        setProperty("color8", "#6E7681")
        setProperty("color9", "#FFA198")
        setProperty("color10", "#56D364")
        setProperty("color11", "#E3B341")
        setProperty("color12", "#79C0FF")
        setProperty("color13", "#D2A8FF")
        setProperty("color14", "#56D4DD")
        setProperty("color15", "#F0F6FC")
    }
    TerminalColors.COLOR_SCHEME.updateWith(props)
}
