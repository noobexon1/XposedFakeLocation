package com.noobexon.xposedfakelocation.manager.localization

import androidx.annotation.StringRes
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.manager.localization.LanguageOption.Companion.fromTag
import java.util.Locale

private const val ENGLISH_LANGUAGE_TAG = "en"
private const val CHINESE_LANGUAGE_TAG = "zh-CN"
private const val GERMAN_LANGUAGE_TAG = "de-DE"

/**
 * The set of UI languages the user can choose from in settings.
 *
 * Each entry pairs a BCP-47 [tag] (persisted via [LocaleController]) with a localized display
 * [labelRes]. To add a language, declare a new entry with its tag and label string; the picker and
 * [fromTag] lookup pick it up automatically.
 *
 * @property tag BCP-47 language tag, or [DEFAULT_LANGUAGE_TAG] (empty) for [SYSTEM] to follow the
 * device locale.
 * @property labelRes string resource for this language's name, shown in the current UI language.
 */
enum class LanguageOption(
    val tag: String,
    @StringRes val labelRes: Int
) {
    /** Follow the device's system locale; carries the empty [DEFAULT_LANGUAGE_TAG]. */
    SYSTEM(DEFAULT_LANGUAGE_TAG, R.string.language_system),

    /** English (`en`). */
    ENGLISH(ENGLISH_LANGUAGE_TAG, R.string.language_english),

    /** Simplified Chinese (`zh-CN`). */
    CHINESE(CHINESE_LANGUAGE_TAG, R.string.language_chinese),

    /** German (`de-DE`). */
    GERMAN(GERMAN_LANGUAGE_TAG, R.string.language_german);

    /**
     * The language's name written in its own script (autonym), e.g. "English" or "中文", so a user
     * can recognize it regardless of the current UI language. `null` for [SYSTEM], whose effective
     * language depends on the device locale.
     */
    val autonym: String?
        get() = when (this) {
            SYSTEM -> null
            else -> Locale.forLanguageTag(tag).let { locale ->
                locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
            }
        }

    companion object {
        /**
         * Returns the option whose [tag] matches [tag], falling back to [SYSTEM] for unknown or
         * empty tags (so a removed/unrecognized persisted value degrades gracefully).
         *
         * @param tag a persisted BCP-47 language tag.
         */
        fun fromTag(tag: String): LanguageOption {
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }
    }
}
