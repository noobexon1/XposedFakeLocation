//Constants.kt
package com.noobexon.xposedfakelocation.data

// APP
const val MANAGER_APP_PACKAGE_NAME = "com.noobexon.xposedfakelocation"
const val SHARED_PREFS_FILE = "xposed_shared_prefs"
const val REMOTE_PREFS_GROUP = "settings"

// KEYS
const val KEY_IS_PLAYING = "is_playing"

const val KEY_LAST_CLICKED_LOCATION = "last_clicked_location"

const val KEY_USE_ACCURACY = "use_accuracy"
const val KEY_ACCURACY  = "accuracy"

const val KEY_USE_ALTITUDE = "use_altitude"
const val KEY_ALTITUDE  = "altitude"

const val KEY_USE_RANDOMIZE  = "use_randomize"
const val KEY_RANDOMIZE_RADIUS = "randomize_radius"

const val KEY_USE_VERTICAL_ACCURACY = "use_vertical_accuracy"
const val KEY_VERTICAL_ACCURACY = "vertical_accuracy"

const val KEY_USE_MEAN_SEA_LEVEL = "use_mean_sea_level"
const val KEY_MEAN_SEA_LEVEL = "mean_sea_level"

const val KEY_USE_MEAN_SEA_LEVEL_ACCURACY = "use_mean_sea_level_accuracy"
const val KEY_MEAN_SEA_LEVEL_ACCURACY = "mean_sea_level_accuracy"

const val KEY_USE_SPEED = "use_speed"
const val KEY_SPEED = "speed"

const val KEY_USE_SPEED_ACCURACY = "use_speed_accuracy"
const val KEY_SPEED_ACCURACY = "speed_accuracy"

const val KEY_FAVORITES = "favorites"

const val KEY_TARGET_APPS = "target_apps"

const val KEY_HIDE_FAKE_LOCATION_TOAST = "hide_fake_location_toast"

const val KEY_ENABLE_BROADCAST_CONTROL = "enable_broadcast_control"
const val KEY_LANGUAGE_TAG = "language_tag"

const val KEY_ENABLE_SYSTEM_HOOKS = "enable_system_hooks"
const val KEY_ENABLE_WIFI_IDENTITY = "enable_wifi_identity"

const val KEY_THEME_OPTION = "theme_option"

const val KEY_WIFI_SSID = "wifi_ssid"
const val KEY_WIFI_BSSID = "wifi_bssid"
const val KEY_WIFI_RSSI = "wifi_rssi"

// Packages added/removed from module scope when system-level hooks are toggled.
// Modern libxposed uses `system` as the virtual package name for system_server.
val SYSTEM_HOOK_PACKAGES = listOf("system", "com.android.phone")

 // DEFAULT VALUES
const val DEFAULT_USE_ACCURACY = false
const val DEFAULT_ACCURACY = 0.0

const val DEFAULT_USE_ALTITUDE = false
const val DEFAULT_ALTITUDE = 0.0

const val DEFAULT_USE_RANDOMIZE = false
const val DEFAULT_RANDOMIZE_RADIUS = 0.0

const val DEFAULT_USE_VERTICAL_ACCURACY = false
const val DEFAULT_VERTICAL_ACCURACY = 0.0f

const val DEFAULT_USE_MEAN_SEA_LEVEL = false
const val DEFAULT_MEAN_SEA_LEVEL = 0.0

const val DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY = false
const val DEFAULT_MEAN_SEA_LEVEL_ACCURACY = 0.0f

const val DEFAULT_USE_SPEED = false
const val DEFAULT_SPEED = 0.0f

const val DEFAULT_USE_SPEED_ACCURACY = false
const val DEFAULT_SPEED_ACCURACY = 0.0f

const val DEFAULT_HIDE_FAKE_LOCATION_TOAST = false

const val DEFAULT_ENABLE_BROADCAST_CONTROL = false
const val DEFAULT_LANGUAGE_TAG = ""

const val DEFAULT_ENABLE_SYSTEM_HOOKS = false
const val DEFAULT_ENABLE_WIFI_IDENTITY = false

const val DEFAULT_THEME_OPTION = ""

const val DEFAULT_WIFI_SSID = "AndroidAP"
const val DEFAULT_WIFI_BSSID = "02:00:00:00:00:00"
const val DEFAULT_WIFI_RSSI = -60
const val MAX_WIFI_SSID_BYTES = 32
const val MIN_WIFI_RSSI = -127
const val MAX_WIFI_RSSI = 0
val MAC_ADDRESS_REGEX = Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")

fun normalizeWifiSsid(rawSsid: String?): String {
    val trimmed = rawSsid?.trim().orEmpty()
    val utf8 = trimmed.toByteArray(Charsets.UTF_8)
    val isValidUtf8 = utf8.toString(Charsets.UTF_8) == trimmed
    return if (trimmed.isNotEmpty() && isValidUtf8 && utf8.size <= MAX_WIFI_SSID_BYTES) {
        trimmed
    } else {
        DEFAULT_WIFI_SSID
    }
}

// MATH & PHYS
const val PI = 3.14159265359
const val RADIUS_EARTH = 6378137.0 // Approximately Earth's radius in meters

// MAP SETTINGS
const val KEY_MAP_ZOOM = "map_zoom"
const val DEFAULT_MAP_ZOOM = 18.0
const val WORLD_MAP_ZOOM = 2.0
const val LOCATION_DETECTION_MAX_ATTEMPTS = 80
const val LOCATION_DETECTION_DELAY_MS = 100L

// JOYSTICK
const val KEY_JOYSTICK_STATE = "joystick_state"
const val KEY_JOYSTICK_MODE = "joystick_mode"
const val KEY_JOYSTICK_MAX_SPEED = "joystick_max_speed"
const val KEY_JOYSTICK_PRESET_WALK = "joystick_preset_walk"
const val KEY_JOYSTICK_PRESET_RUN = "joystick_preset_run"
const val KEY_JOYSTICK_PRESET_BIKE = "joystick_preset_bike"
const val KEY_JOYSTICK_PRESET_DRIVE = "joystick_preset_drive"
const val KEY_JOYSTICK_SELECTED_PRESET = "joystick_selected_preset"
const val KEY_JOYSTICK_OVERLAY_X = "joystick_overlay_x"
const val KEY_JOYSTICK_OVERLAY_Y = "joystick_overlay_y"
const val KEY_ROUTES = "routes"
const val KEY_ROUTE_DRAFT = "route_draft"
const val KEY_MAP_MODE = "map_mode"
const val KEY_ROUTE_OVERLAY_X = "route_overlay_x"
const val KEY_ROUTE_OVERLAY_Y = "route_overlay_y"

const val MPS_TO_KMH = 3.6f
const val DEFAULT_JOYSTICK_MAX_SPEED = 50f / MPS_TO_KMH
const val DEFAULT_JOYSTICK_PRESET_WALK = 1.4f
const val DEFAULT_JOYSTICK_PRESET_RUN = 3.0f
const val DEFAULT_JOYSTICK_PRESET_BIKE = 5.5f
const val DEFAULT_JOYSTICK_PRESET_DRIVE = 14.0f

/** Hooks ignore a joystick state older than this, so a killed overlay can't freeze the motion override. */
const val JOYSTICK_STALE_MS = 5_000L
const val JOYSTICK_SPEED_ACCURACY = 0.5f
/** Accepted joystick speed range (m/s), matching the 1..200 km/h Settings sliders. */
const val JOYSTICK_MIN_SPEED_MPS = 1f / MPS_TO_KMH
const val JOYSTICK_MAX_SPEED_MPS = 200f / MPS_TO_KMH
const val ROUTE_MIN_POINTS = 2
const val ROUTE_MAX_DWELL_SECONDS = 3_600
const val ROUTE_MAX_VARIATION_PERCENT = 50
