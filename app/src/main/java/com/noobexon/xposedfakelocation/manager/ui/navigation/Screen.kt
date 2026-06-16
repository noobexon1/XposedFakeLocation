package com.noobexon.xposedfakelocation.manager.ui.navigation

sealed class Screen(val route: String) {
    object About : Screen("about")
    object Favorites : Screen("favorites")
    object Map : Screen("map")
    object Permissions : Screen("permissions")
    object Routes : Screen("routes")
    object RouteDetail : Screen("route_detail/{routeName}") {
        fun createRoute(routeName: String): String = "route_detail/${java.net.URLEncoder.encode(routeName, "UTF-8")}"
    }
    object Settings : Screen("settings")
    object TargetApps : Screen("target_apps")
}
