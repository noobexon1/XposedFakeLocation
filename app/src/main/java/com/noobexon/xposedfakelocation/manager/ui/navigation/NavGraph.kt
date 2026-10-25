package com.noobexon.xposedfakelocation.manager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.noobexon.xposedfakelocation.data.model.MapMode
import com.noobexon.xposedfakelocation.manager.route.RoutePlaybackState
import com.noobexon.xposedfakelocation.manager.ui.about.AboutScreen
import com.noobexon.xposedfakelocation.manager.ui.favorites.FavoritesScreen
import com.noobexon.xposedfakelocation.manager.ui.favorites.FavoritesTab
import com.noobexon.xposedfakelocation.manager.ui.map.MapScreen
import com.noobexon.xposedfakelocation.manager.ui.map.MapViewModel
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteEditorViewModel
import com.noobexon.xposedfakelocation.manager.ui.permissions.PermissionsScreen
import com.noobexon.xposedfakelocation.manager.ui.settings.SettingsScreen
import com.noobexon.xposedfakelocation.manager.ui.targetapps.TargetAppsScreen
import org.osmdroid.util.GeoPoint

@Composable
fun AppNavGraph(
    navController: NavHostController,
) {
    val mapViewModel: MapViewModel = viewModel()
    val routeEditorViewModel: RouteEditorViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Permissions.route,
    ) {
        composable(route = Screen.About.route) {
            AboutScreen(navController = navController)
        }
        composable(route = Screen.Favorites.route) {
            val initialTab = remember {
                if (routeEditorViewModel.consumeOpenRoutesTabRequest()) FavoritesTab.ROUTES else FavoritesTab.LOCATIONS
            }
            FavoritesScreen(
                navController = navController,
                onFavoriteSelected = { favorite ->
                    mapViewModel.updateClickedLocation(GeoPoint(favorite.latitude, favorite.longitude))
                    if (RoutePlaybackState.current.value == null) routeEditorViewModel.setMapMode(MapMode.LOCATION)
                },
                onRouteSelected = routeEditorViewModel::requestLoadRoute,
                initialTab = initialTab,
            )
        }
        composable(route = Screen.Map.route) {
            MapScreen(navController = navController, mapViewModel, routeEditorViewModel)
        }
        composable(route = Screen.Permissions.route) {
            PermissionsScreen(navController = navController)
        }
        composable(route = Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(route = Screen.TargetApps.route) {
            TargetAppsScreen(navController = navController)
        }
    }
}
