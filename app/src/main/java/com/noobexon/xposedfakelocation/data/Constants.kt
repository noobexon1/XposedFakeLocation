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

const val KEY_THEME_OPTION = "theme_option"

// ROUTES
const val KEY_ROUTES = "routes"
const val KEY_ROUTE_PLAYING = "route_playing"
const val KEY_ACTIVE_ROUTE_NAME = "active_route_name"
const val KEY_ACTIVE_ROUTE_WAYPOINTS = "active_route_waypoints"
const val KEY_ACTIVE_ROUTE_WAYPOINT_INDEX = "active_route_waypoint_index"
const val KEY_ACTIVE_ROUTE_PROGRESS = "active_route_progress"
const val KEY_ROUTE_PLAYBACK_SPEED = "route_playback_speed"
const val KEY_ROUTE_LOOP = "route_loop"
const val KEY_CURRENT_ROUTE_LAT = "current_route_lat"
const val KEY_CURRENT_ROUTE_LON = "current_route_lon"

// Packages added/removed from module scope when system-level hooks are toggled.
val SYSTEM_HOOK_PACKAGES = listOf("android", "com.android.phone")

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

const val DEFAULT_THEME_OPTION = ""

// ROUTES DEFAULTS
const val DEFAULT_ROUTE_PLAYBACK_SPEED = 10.0
const val DEFAULT_ROUTE_LOOP = false
const val DEFAULT_ACTIVE_ROUTE_WAYPOINT_INDEX = 0
const val DEFAULT_ACTIVE_ROUTE_PROGRESS = 0.0

// MATH & PHYS
const val PI = 3.14159265359
const val RADIUS_EARTH = 6378137.0 // Approximately Earth's radius in meters

// MAP SETTINGS
const val KEY_MAP_ZOOM = "map_zoom"
const val DEFAULT_MAP_ZOOM = 18.0
const val WORLD_MAP_ZOOM = 2.0
const val LOCATION_DETECTION_MAX_ATTEMPTS = 80
const val LOCATION_DETECTION_DELAY_MS = 100L
