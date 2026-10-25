package com.noobexon.xposedfakelocation.manager.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_THEME_OPTION
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeOption.Companion.fromTag

/**
 * The set of UI themes the user can choose from in settings.
 *
 * Each entry pairs a persisted [tag] with a localized display [labelRes]. To add a theme variant,
 * declare a new entry with its tag and label string; the picker and [fromTag] lookup pick it up
 * automatically.
 *
 * @property tag Short string key persisted to [SharedPreferences], or [DEFAULT_THEME_OPTION]
 *   (empty) for [SYSTEM] to follow the device theme.
 * @property labelRes String resource for this option's name, shown in the settings row and picker.
 */
enum class ThemeOption(
    val tag: String,
    @StringRes val labelRes: Int
) {
    /** Follow the device's system dark-mode setting. Carries the empty [DEFAULT_THEME_OPTION]. */
    SYSTEM(DEFAULT_THEME_OPTION, R.string.theme_system),

    /** Always use the light colour scheme. */
    LIGHT("light", R.string.theme_light),

    /** Always use the dark colour scheme. */
    DARK("dark", R.string.theme_dark);

    companion object {
        /**
         * Returns the option whose [tag] matches [tag], falling back to [SYSTEM] for unknown or
         * empty tags so a removed/unrecognized persisted value degrades gracefully.
         *
         * @param tag a persisted theme tag.
         */
        fun fromTag(tag: String): ThemeOption =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/** Whether this option resolves to the dark colour scheme; [ThemeOption.SYSTEM] follows the device setting. */
@Composable
fun ThemeOption.isDark(): Boolean = when (this) {
    ThemeOption.SYSTEM -> isSystemInDarkTheme()
    ThemeOption.LIGHT -> false
    ThemeOption.DARK -> true
}
