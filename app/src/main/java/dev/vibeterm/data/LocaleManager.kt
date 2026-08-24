package dev.vibeterm.data

import android.content.Context
import android.content.res.Configuration
import dev.vibeterm.R
import java.util.Locale

/**
 * In-app language switching. The app UI is not tied to the system locale: the user can pick a
 * language in Settings, we persist the BCP-47 tag, and every UI-facing context is wrapped with a
 * matching [Configuration] so `stringResource`/`getString` resolve to the chosen `values-*` folder.
 *
 * Applied in [dev.vibeterm.MainActivity.attachBaseContext] and
 * [dev.vibeterm.service.SshForegroundService.attachBaseContext]; code that builds user-visible text
 * off the application context (notifications, terminal status banners) wraps it via [wrap] at use.
 */
object LocaleManager {
    const val SYSTEM = "system"

    /** Selectable tags, in menu order. "system" follows the device locale. */
    val TAGS = listOf(SYSTEM, "en", "zh-CN", "ja", "ko")

    /** Language name shown in its own script (except "system", which is localized). */
    fun displayName(context: Context, tag: String): String = when (tag) {
        SYSTEM -> context.getString(R.string.lang_system)
        "en" -> "English"
        "zh-CN" -> "简体中文"
        "ja" -> "日本語"
        "ko" -> "한국어"
        else -> tag
    }

    /** Return [base] re-based on the user-selected locale, or [base] unchanged when following system. */
    fun wrap(base: Context): Context {
        val tag = Prefs.appLang(base)
        if (tag == SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
