package com.noobexon.xposedfakelocation.manager.ui.settings

import androidx.annotation.StringRes
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_BROADCAST_CONTROL
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_SYSTEM_HOOKS
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_WIFI_IDENTITY
import com.noobexon.xposedfakelocation.data.DEFAULT_GEOCODING_SERVER_URL
import com.noobexon.xposedfakelocation.data.DEFAULT_HIDE_FAKE_LOCATION_TOAST
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_MAX_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_BIKE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_DRIVE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_RUN
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_WALK
import com.noobexon.xposedfakelocation.data.DEFAULT_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_ROUTING_SERVER_URL
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_THEME_OPTION
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_RANDOMIZE
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_BSSID
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_SSID
import com.noobexon.xposedfakelocation.data.MPS_TO_KMH
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.MapRenderer
import com.noobexon.xposedfakelocation.manager.localization.LanguageOption
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeOption
import kotlin.math.round

/**
 * Visual grouping and display order for the settings screen (top to bottom). Spoofing categories
 * come first, followed by behavioral and app-level preferences.
 *
 * @property titleRes String resource for the uppercase section header rendered above each card.
 */
enum class SettingsCategory(@StringRes val titleRes: Int) {
    LOCATION(R.string.category_location),
    ALTITUDE(R.string.category_altitude),
    MOVEMENT(R.string.category_movement),
    JOYSTICK(R.string.category_joystick),
    NOTIFICATIONS(R.string.category_notifications),
    SYSTEM_HOOKS(R.string.category_system_hooks),
    WIFI_IDENTITY(R.string.category_wifi_identity),
    EXTERNAL_CONTROL(R.string.category_external_control),
    SEARCH(R.string.category_search),
    MAP(R.string.category_map),
    APPEARANCE(R.string.category_appearance),
    LANGUAGE(R.string.category_language)
}

/**
 * Compile-time metadata for a numeric (field + slider) setting row. The UI works uniformly in
 * [Float]; callers convert back to the ViewModel's persisted type in the `onValueChange` lambda.
 *
 * @property titleRes String resource for the setting name shown next to the enable switch.
 * @property descriptionRes String resource for the help text shown when the info icon is tapped.
 * @property labelRes String resource for the short label used as the value field's floating label.
 * @property unitRes String resource for the unit suffix appended inside the value field
 *   (e.g. [R.string.unit_meters], [R.string.unit_meters_per_second]). Using a resource keeps unit
 *   strings consistent between locales without duplicating them in translation files.
 * @property min Inclusive lower bound of the slider and value field.
 * @property max Inclusive upper bound of the slider and value field.
 * @property precision Granularity hint; used only to derive [decimals] — values `>= 1f` give zero
 *   decimal places, smaller values give one.
 * @property default Value written (and persisted) when the user clears the field and blurs without
 *   entering a number. Sourced directly from `data/Constants.kt` so it stays in sync with the
 *   value produced by [SettingsViewModel.resetToDefaults].
 */
enum class NumericSetting(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val labelRes: Int,
    @StringRes val unitRes: Int,
    val min: Float,
    val max: Float,
    val precision: Float,
    val default: Float
) {
    RANDOMIZE_RADIUS(
        R.string.setting_randomize_title,
        R.string.setting_randomize_description,
        R.string.setting_randomize_radius_label,
        unitRes = R.string.unit_meters, min = 0f, max = 2000f, precision = 0.1f, default = DEFAULT_RANDOMIZE_RADIUS.toFloat()
    ),
    HORIZONTAL_ACCURACY(
        R.string.setting_horizontal_accuracy_title,
        R.string.setting_horizontal_accuracy_description,
        R.string.setting_horizontal_accuracy_label,
        unitRes = R.string.unit_meters, min = 0f, max = 100f, precision = 0.01f, default = DEFAULT_ACCURACY.toFloat()
    ),
    VERTICAL_ACCURACY(
        R.string.setting_vertical_accuracy_title,
        R.string.setting_vertical_accuracy_description,
        R.string.setting_vertical_accuracy_label,
        unitRes = R.string.unit_meters, min = 0f, max = 100f, precision = 0.01f, default = DEFAULT_VERTICAL_ACCURACY
    ),
    ALTITUDE(
        R.string.setting_altitude_title,
        R.string.setting_altitude_description,
        R.string.setting_altitude_label,
        unitRes = R.string.unit_meters, min = 0f, max = 2000f, precision = 0.5f, default = DEFAULT_ALTITUDE.toFloat()
    ),
    MEAN_SEA_LEVEL(
        R.string.setting_msl_title,
        R.string.setting_msl_description,
        R.string.setting_msl_label,
        unitRes = R.string.unit_meters, min = -400f, max = 2000f, precision = 0.5f, default = DEFAULT_MEAN_SEA_LEVEL.toFloat()
    ),
    MEAN_SEA_LEVEL_ACCURACY(
        R.string.setting_msl_accuracy_title,
        R.string.setting_msl_accuracy_description,
        R.string.setting_msl_accuracy_label,
        unitRes = R.string.unit_meters, min = 0f, max = 100f, precision = 0.01f, default = DEFAULT_MEAN_SEA_LEVEL_ACCURACY
    ),
    SPEED(
        R.string.setting_speed_title,
        R.string.setting_speed_description,
        R.string.setting_speed_label,
        unitRes = R.string.unit_meters_per_second, min = 0f, max = 30f, precision = 0.1f, default = DEFAULT_SPEED
    ),
    SPEED_ACCURACY(
        R.string.setting_speed_accuracy_title,
        R.string.setting_speed_accuracy_description,
        R.string.setting_speed_accuracy_label,
        unitRes = R.string.unit_meters_per_second, min = 0f, max = 100f, precision = 0.01f, default = DEFAULT_SPEED_ACCURACY
    ),
    JOYSTICK_MAX_SPEED(
        R.string.setting_joystick_max_speed_title,
        R.string.setting_joystick_max_speed_description,
        R.string.setting_joystick_max_speed_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_MAX_SPEED * MPS_TO_KMH
    ),
    JOYSTICK_PRESET_WALK(
        R.string.setting_joystick_walk_title,
        R.string.setting_joystick_walk_description,
        R.string.setting_joystick_walk_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_PRESET_WALK * MPS_TO_KMH
    ),
    JOYSTICK_PRESET_RUN(
        R.string.setting_joystick_run_title,
        R.string.setting_joystick_run_description,
        R.string.setting_joystick_run_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_PRESET_RUN * MPS_TO_KMH
    ),
    JOYSTICK_PRESET_BIKE(
        R.string.setting_joystick_bike_title,
        R.string.setting_joystick_bike_description,
        R.string.setting_joystick_bike_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_PRESET_BIKE * MPS_TO_KMH
    ),
    JOYSTICK_PRESET_DRIVE(
        R.string.setting_joystick_drive_title,
        R.string.setting_joystick_drive_description,
        R.string.setting_joystick_drive_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_PRESET_DRIVE * MPS_TO_KMH
    );

    /**
     * Number of fractional digits used when formatting or rounding a value for this setting.
     * Derived from [precision]: `>= 1f` → 0, `>= 0.1f` → 1, `< 0.1f` → 2. Consistent between
     * the field label and [roundValue] so the displayed and persisted values always agree.
     */
    val decimals: Int get() = when {
        precision >= 1f -> 0
        precision >= 0.1f -> 1
        else -> 2
    }

    /**
     * Rounds [value] to [decimals] fractional digits, eliminating the floating-point drift that
     * would otherwise cause the displayed field text and the persisted [Double]/[Float] to diverge.
     *
     * @param value raw value to round (e.g. from a slider drag or typed input).
     * @return value rounded to the precision implied by [precision].
     */
    fun roundValue(value: Float): Float = when (decimals) {
        0 -> round(value)
        1 -> round(value * 10f) / 10f
        else -> round(value * 100f) / 100f
    }
}

/**
 * Stable string keys that uniquely identify each [SettingEntry] row within the settings list.
 * Used by Compose's `key {}` block to anchor remembered state across recompositions (e.g. when
 * search filtering reorders rows). These are internal identifiers — not shown to users.
 */
object SettingKeys {
    const val HIDE_TOAST = "hide_toast"
    const val JOYSTICK_CONSTANT_MODE = "joystick_constant_mode"
    const val JOYSTICK_PRESET = "joystick_preset"
    const val SYSTEM_HOOKS = "system_hooks"
    const val WIFI_IDENTITY = "wifi_identity"
    const val WIFI_SSID = "wifi_ssid"
    const val WIFI_BSSID = "wifi_bssid"
    const val WIFI_RSSI = "wifi_rssi"
    const val BROADCAST = "broadcast"
    const val SEARCH_SERVER = "search_server"
    const val ROUTING_SERVER = "routing_server"
    const val MAP_RENDERER = "map_renderer"
    const val MAP_STYLE_URL = "map_style_url"
    const val THEME = "theme"
    const val LANGUAGE = "language"
}

enum class TextInputKind {
    TEXT,
    SIGNED_NUMBER,
    URL
}

/** Every variant exposes a stable [key]
 * (used by Compose's `key {}` block to anchor remembered state across recompositions, e.g. during
 * search filtering) and the string resources required for search matching.
 *
 * @property key Stable, unique identifier for this row within the full settings list.
 * @property titleRes String resource for the setting's primary label.
 * @property descriptionRes String resource for the setting's help text, shown via the info icon.
 */
sealed interface SettingEntry {
    val key: String
    @get:StringRes val titleRes: Int
    @get:StringRes val descriptionRes: Int

    /**
     * Optional per-row "reset to default" action. When non-null, the row renders a reset icon that
     * invokes this callback; when `null` (the default), no reset icon is shown. Rows whose reset
     * would be disruptive or meaningless (system hooks, theme, language) leave this `null`.
     */
    val onReset: (() -> Unit)? get() = null

    /**
     * A boolean on/off row backed by a Switch.
     *
     * @property key Stable row identifier.
     * @property titleRes Primary label resource.
     * @property descriptionRes Help text resource.
     * @property checked Current toggle state.
     * @property onCheckedChange Callback invoked with the new [Boolean] when the switch is toggled.
     */
    data class Switch(
        override val key: String,
        @StringRes override val titleRes: Int,
        @StringRes override val descriptionRes: Int,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : SettingEntry

    /**
     * A numeric row with an enable Switch, an editable value field, and a continuous Slider.
     * All display and interaction logic is driven by [setting]'s static metadata so the row is
     * fully data-driven.
     *
     * @property setting Static metadata (titles, unit, range, default, precision) for this row.
     * @property enabled Whether the setting's value is currently active (controls switch + field/slider visibility).
     * @property onEnabledChange Callback invoked with the new [Boolean] when the enable switch is toggled.
     * @property value Currently committed value in the unit described by [NumericSetting.unitRes].
     * @property onValueChange Callback invoked with the new [Float] once the user finishes editing
     *   (field blur / IME Done / slider release), never on every keystroke or drag frame.
     * @property showToggle When `false` the row has no enable switch and its value controls are
     *   always visible (for settings that are always in effect).
     */
    data class Numeric(
        val setting: NumericSetting,
        val enabled: Boolean,
        val onEnabledChange: (Boolean) -> Unit,
        val value: Float,
        val onValueChange: (Float) -> Unit,
        override val onReset: (() -> Unit)? = null,
        val showToggle: Boolean = true
    ) : SettingEntry {
        override val key: String get() = setting.name
        override val titleRes: Int get() = setting.titleRes
        override val descriptionRes: Int get() = setting.descriptionRes
    }

    /**
     * A plain text row for settings that do not need a toggle or slider but still need a labelled,
     * searchable input field.
     *
     * @property errorRes Returns an error message for the current text, or `null` when it is valid.
     *   Invalid text is shown with the error and is not committed.
     */
    data class Text(
        override val key: String,
        @StringRes override val titleRes: Int,
        @StringRes override val descriptionRes: Int,
        @StringRes val labelRes: Int,
        val value: String,
        val inputKind: TextInputKind = TextInputKind.TEXT,
        val onValueChange: (String) -> Unit,
        override val onReset: (() -> Unit)? = null,
        val errorRes: (String) -> Int? = { null },
    ) : SettingEntry

    /**
     * The language picker row. Tapping anywhere on the row opens a language selection dialog;
     * selecting an option there applies it immediately and recreates the host Activity.
     *
     * @property selected The currently active [LanguageOption], shown collapsed in the row.
     * @property onSelected Callback invoked with the chosen [LanguageOption] when the user confirms
     *   in the dialog. The caller is responsible for recreating the Activity so the locale applies.
     */
    data class Language(
        val selected: LanguageOption,
        val onSelected: (LanguageOption) -> Unit
    ) : SettingEntry {
        override val key: String get() = SettingKeys.LANGUAGE
        override val titleRes: Int get() = R.string.setting_language_title
        override val descriptionRes: Int get() = R.string.setting_language_description
    }

    /**
     * The theme picker row. Tapping anywhere on the row opens a theme selection dialog; selecting
     * an option there applies it immediately — no Activity recreation required.
     *
     * @property selected The currently active [ThemeOption], shown collapsed in the row.
     * @property onSelected Callback invoked with the chosen [ThemeOption] when the user taps an
     *   item in the dialog.
     */
    data class Theme(
        val selected: ThemeOption,
        val onSelected: (ThemeOption) -> Unit
    ) : SettingEntry {
        override val key: String get() = SettingKeys.THEME
        override val titleRes: Int get() = R.string.setting_theme_title
        override val descriptionRes: Int get() = R.string.setting_theme_description
    }

    /** The map renderer picker row. */
    data class MapRendererChoice(
        val selected: MapRenderer,
        val onSelected: (MapRenderer) -> Unit
    ) : SettingEntry {
        override val key: String get() = SettingKeys.MAP_RENDERER
        override val titleRes: Int get() = R.string.setting_map_renderer_title
        override val descriptionRes: Int get() = R.string.setting_map_renderer_description
    }

    /**
     * The joystick's Constant-mode speed preset picker. Tapping the row opens a selection dialog.
     *
     * @property selected The preset the joystick currently moves at.
     * @property onSelected Callback invoked with the chosen preset.
     */
    data class JoystickPresetChoice(
        val selected: JoystickPreset,
        val onSelected: (JoystickPreset) -> Unit
    ) : SettingEntry {
        override val key: String get() = SettingKeys.JOYSTICK_PRESET
        override val titleRes: Int get() = R.string.setting_joystick_preset_title
        override val descriptionRes: Int get() = R.string.setting_joystick_preset_description
    }
}

/** Short display name of a joystick speed preset. */
@get:StringRes
val JoystickPreset.labelRes: Int
    get() = when (this) {
        JoystickPreset.WALK -> R.string.setting_joystick_walk_label
        JoystickPreset.RUN -> R.string.setting_joystick_run_label
        JoystickPreset.BIKE -> R.string.setting_joystick_bike_label
        JoystickPreset.DRIVE -> R.string.setting_joystick_drive_label
    }

/** Display name of a map renderer. */
@get:StringRes
val MapRenderer.labelRes: Int
    get() = when (this) {
        MapRenderer.OSMDROID -> R.string.map_renderer_osmdroid
        MapRenderer.MAPLIBRE -> R.string.map_renderer_maplibre
    }

/**
 * Immutable snapshot of all spoofing and app preferences consumed by the settings UI. Every
 * numeric field is [Float] so the UI layer works uniformly without per-field
 * [Double]/[Float] conversions. [Double]-typed repository values are converted once in
 * [SettingsViewModel.uiState] before reaching this class.
 *
 * Default values mirror the constants in `data/Constants.kt` so that
 * [SettingsViewModel.uiState]'s `initialValue` is always consistent with
 * [SettingsViewModel.resetToDefaults].
 */
data class SettingsUiState(
    // Location
    val useRandomize: Boolean = DEFAULT_USE_RANDOMIZE,
    val randomizeRadius: Float = DEFAULT_RANDOMIZE_RADIUS.toFloat(),
    val useAccuracy: Boolean = DEFAULT_USE_ACCURACY,
    val accuracy: Float = DEFAULT_ACCURACY.toFloat(),
    val useVerticalAccuracy: Boolean = DEFAULT_USE_VERTICAL_ACCURACY,
    val verticalAccuracy: Float = DEFAULT_VERTICAL_ACCURACY,
    // Altitude
    val useAltitude: Boolean = DEFAULT_USE_ALTITUDE,
    val altitude: Float = DEFAULT_ALTITUDE.toFloat(),
    val useMeanSeaLevel: Boolean = DEFAULT_USE_MEAN_SEA_LEVEL,
    val meanSeaLevel: Float = DEFAULT_MEAN_SEA_LEVEL.toFloat(),
    val useMeanSeaLevelAccuracy: Boolean = DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY,
    val meanSeaLevelAccuracy: Float = DEFAULT_MEAN_SEA_LEVEL_ACCURACY,
    // Movement
    val useSpeed: Boolean = DEFAULT_USE_SPEED,
    val speed: Float = DEFAULT_SPEED,
    val useSpeedAccuracy: Boolean = DEFAULT_USE_SPEED_ACCURACY,
    val speedAccuracy: Float = DEFAULT_SPEED_ACCURACY,
    // Behaviour
    val hideFakeLocationToast: Boolean = DEFAULT_HIDE_FAKE_LOCATION_TOAST,
    val enableBroadcastControl: Boolean = DEFAULT_ENABLE_BROADCAST_CONTROL,
    val systemHooksEnabled: Boolean = DEFAULT_ENABLE_SYSTEM_HOOKS,
    // Wi-Fi identity
    val wifiIdentityEnabled: Boolean = DEFAULT_ENABLE_WIFI_IDENTITY,
    val wifiSsid: String = DEFAULT_WIFI_SSID,
    val wifiBssid: String = DEFAULT_WIFI_BSSID,
    val wifiRssi: Int = DEFAULT_WIFI_RSSI,
    // Address search and routing
    val geocodingServerUrl: String = DEFAULT_GEOCODING_SERVER_URL,
    val routingServerUrl: String = DEFAULT_ROUTING_SERVER_URL,
    // Map
    val mapRenderer: MapRenderer = MapRenderer.OSMDROID,
    val mapStyleUrl: String = "",
    // App
    val languageTag: String = DEFAULT_LANGUAGE_TAG,
    val themeOption: ThemeOption = ThemeOption.fromTag(DEFAULT_THEME_OPTION),
)
