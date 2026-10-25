package com.noobexon.xposedfakelocation.manager.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.noobexon.xposedfakelocation.data.DEFAULT_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.KEY_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.SHARED_PREFS_FILE
import com.noobexon.xposedfakelocation.manager.localization.LocaleController.attachBaseContext
import java.util.Locale

/**
 * Applies and persists the manager app's UI language independently of the device locale.
 *
 * The selected language is stored as a BCP-47 tag in shared preferences and re-applied on every
 * activity creation by wrapping the base [Context] with an overridden locale. An empty tag
 * ([DEFAULT_LANGUAGE_TAG]) means "follow the system locale".
 */
object LocaleController {
    /**
     * Wraps [context] so resources resolve in the persisted UI language. Call from
     * `Activity.attachBaseContext` so the whole activity tree is localized.
     *
     * @param context the base context being attached.
     * @return a locale-overridden context, or [context] unchanged when following the system locale.
     */
    fun attachBaseContext(context: Context): Context {
        return localizedContext(context, readLanguageTag(context))
    }

    /**
     * The device's actual system language written in its own script (autonym), e.g. "English".
     * Read from the system resources so it reflects the real device locale even when the app has
     * overridden its own locale. Used to show what the "System default" option resolves to.
     */
    fun systemLanguageAutonym(): String {
        val locale = Resources.getSystem().configuration.locales[0]
        return locale.getDisplayLanguage(locale)
            .ifBlank { locale.getDisplayName(locale) }
            .replaceFirstChar { it.titlecase(locale) }
    }

    /**
     * Persists the chosen UI language so it survives restarts and is picked up by
     * [attachBaseContext]. The caller is responsible for re-creating the activity to apply it.
     *
     * @param context any context (used to access shared preferences).
     * @param languageTag BCP-47 tag to store, or empty to follow the system locale.
     */
    fun persistLanguageTag(context: Context, languageTag: String) {
        context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, languageTag)
            .apply()
    }

    /**
     * Reads the persisted UI language tag, defaulting to [DEFAULT_LANGUAGE_TAG] (follow system)
     * when none has been set.
     *
     * @param context any context (used to access shared preferences).
     * @return the stored BCP-47 tag, or [DEFAULT_LANGUAGE_TAG] if unset.
     */
    fun readLanguageTag(context: Context): String {
        return context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG)
            ?: DEFAULT_LANGUAGE_TAG
    }

    /**
     * Builds a configuration context whose locale and layout direction are overridden to
     * [languageTag]. Returns [context] unchanged for a blank tag (i.e. follow the system locale).
     */
    private fun localizedContext(context: Context, languageTag: String): Context {
        if (languageTag.isBlank()) return context

        val locale = Locale.forLanguageTag(languageTag)
        // Override only locale fields: a full copy would freeze orientation, screen size and
        // night mode for contexts that outlive configuration changes (e.g. the overlay service).
        // fontScale 0 means "undefined", so the user's font scale still applies.
        val configuration = Configuration().apply { fontScale = 0f }
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
