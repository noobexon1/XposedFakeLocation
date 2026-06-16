package com.noobexon.xposedfakelocation.manager.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.noobexon.xposedfakelocation.BuildConfig
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.Discord
import compose.icons.lineawesomeicons.Github
import compose.icons.lineawesomeicons.HeartSolid
import compose.icons.lineawesomeicons.InfoCircleSolid
import compose.icons.lineawesomeicons.MapSolid
import compose.icons.lineawesomeicons.MobileAltSolid
import compose.icons.lineawesomeicons.RouteSolid
import compose.icons.lineawesomeicons.Telegram

/** Centralised spacing and size constants for the navigation drawer layout. */
private object DrawerDimensions {
    val SECTION_SPACING = 24.dp
    val ITEM_SPACING = 4.dp
    val ICON_SIZE = 24.dp
    val SECTION_PADDING = 8.dp
    val HEADER_PADDING = 16.dp
    val DRAWER_PADDING = 16.dp
    val ITEM_PADDING = 12.dp
    val ITEM_CORNER_RADIUS = 12.dp
}

// TODO: Think on how to ask users for stars on github if they like the module.

/**
 * Content of the [ModalNavigationDrawer] used throughout the app.
 *
 * Renders three sections — Navigation, Community, and App Info — plus a sticky version footer at
 * the bottom. Navigation items highlight the currently active destination reactively via
 * [currentBackStackEntryAsState].
 *
 * **Navigation behaviour**: tapping a navigation item calls the internal `navigateTo` helper which:
 * - Skips [NavController.navigate] if the destination is already active (avoids duplicate back-
 *   stack entries) but still closes the drawer.
 * - Calls [onNavigate] before navigating to a *different* destination so [MapScreen] can record
 *   the drawer-reopen intent (see [MapViewModel.requestReopenDrawer]).
 * - Uses `launchSingleTop = true` to prevent multiple copies of the same screen on the back stack.
 *
 * Community items (Telegram, Discord, GitHub) open an [Intent.ACTION_VIEW] external link and then
 * close the drawer; they do not trigger [onNavigate].
 *
 * @param navController Used to read the current destination and perform in-app navigation.
 * @param onCloseDrawer Callback that closes the [ModalNavigationDrawer]; called after every item
 *   tap (navigation or external link).
 * @param onNavigate Callback invoked before navigating to a *different* screen. Used by
 *   [MapScreen] to set the drawer-reopen flag so the drawer is restored when the user goes back.
 */
@Composable
fun DrawerContent(
    navController: NavController,
    onCloseDrawer: () -> Unit = {},
    onNavigate: () -> Unit = {}
) {
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val navigateTo: (String) -> Unit = { route ->
        if (route != currentRoute) {
            onNavigate()
            navController.navigate(route) { launchSingleTop = true }
        }
        onCloseDrawer()
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(DrawerDimensions.DRAWER_PADDING)
        ) {
            DrawerHeader()
            Spacer(modifier = Modifier.height(DrawerDimensions.SECTION_SPACING))
            DrawerSectionHeader(stringResource(R.string.drawer_navigation))

            DrawerItem(
                icon = LineAwesomeIcons.MapSolid,
                label = stringResource(R.string.drawer_map),
                onClick = { navigateTo(Screen.Map.route) },
                isSelected = currentRoute == Screen.Map.route
            )

            DrawerItem(
                icon = LineAwesomeIcons.HeartSolid,
                label = stringResource(R.string.screen_favorites),
                onClick = { navigateTo(Screen.Favorites.route) },
                isSelected = currentRoute == Screen.Favorites.route
            )

            DrawerItem(
                icon = LineAwesomeIcons.RouteSolid,
                label = stringResource(R.string.screen_routes),
                onClick = { navigateTo(Screen.Routes.route) },
                isSelected = currentRoute == Screen.Routes.route
            )

            DrawerItem(
                icon = LineAwesomeIcons.MobileAltSolid,
                label = stringResource(R.string.screen_target_apps),
                onClick = { navigateTo(Screen.TargetApps.route) },
                isSelected = currentRoute == Screen.TargetApps.route
            )

            DrawerItem(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.screen_settings),
                onClick = { navigateTo(Screen.Settings.route) },
                isSelected = currentRoute == Screen.Settings.route
            )

            Spacer(modifier = Modifier.height(DrawerDimensions.SECTION_SPACING))
            DrawerSectionHeader(stringResource(R.string.drawer_community))

            DrawerItem(
                icon = LineAwesomeIcons.Telegram,
                label = "Telegram",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/XposedFakeLocationChat"))
                    context.startActivity(intent)
                    onCloseDrawer()
                }
            )

            DrawerItem(
                icon = LineAwesomeIcons.Discord,
                label = "Discord",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/8eCRU3KzVS"))
                    context.startActivity(intent)
                    onCloseDrawer()
                }
            )

            DrawerItem(
                icon = LineAwesomeIcons.Github,
                label = "GitHub",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/noobexon1/XposedFakeLocation"))
                    context.startActivity(intent)
                    onCloseDrawer()
                }
            )

            Spacer(modifier = Modifier.height(DrawerDimensions.SECTION_SPACING))
            DrawerSectionHeader(stringResource(R.string.drawer_app_info))

            DrawerItem(
                icon = LineAwesomeIcons.InfoCircleSolid,
                label = stringResource(R.string.screen_about),
                onClick = { navigateTo(Screen.About.route) },
                isSelected = currentRoute == Screen.About.route
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.drawer_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(DrawerDimensions.SECTION_PADDING)
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}

/**
 * Sticky header at the top of the drawer sheet showing the app name and a short subtitle.
 */
@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DrawerDimensions.HEADER_PADDING)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.drawer_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Small, coloured section heading rendered above a group of [DrawerItem]s.
 *
 * @param title The section label (e.g. "Navigation", "Community").
 */
@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = DrawerDimensions.SECTION_PADDING,
            bottom = DrawerDimensions.SECTION_PADDING
        )
    )
}

/**
 * A single tappable row in the navigation drawer.
 *
 * When [isSelected] is `true`, the row is rendered with a filled `primaryContainer` background and
 * `onPrimaryContainer` tint to provide active-destination feedback. Otherwise it renders on a
 * transparent background with the default `onSurface` tint.
 *
 * @param icon Leading icon for the item.
 * @param label Display label text.
 * @param onClick Action invoked when the row is tapped.
 * @param isSelected Whether this item represents the currently active destination.
 * @param trailingIcon Optional composable placed at the end of the row (e.g. a badge or arrow).
 */
@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DrawerDimensions.ITEM_SPACING)
            .clip(RoundedCornerShape(DrawerDimensions.ITEM_CORNER_RADIUS))
            .clickable(onClick = onClick),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DrawerDimensions.ITEM_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(DrawerDimensions.ICON_SIZE),
                tint = contentColor
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            trailingIcon?.invoke()
        }
    }
}
