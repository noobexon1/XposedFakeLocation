package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.RoadProfile
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.manager.geo.RoutingException
import com.noobexon.xposedfakelocation.manager.joystick.displayKmh
import com.noobexon.xposedfakelocation.manager.route.RouteFormat
import com.noobexon.xposedfakelocation.manager.ui.settings.labelRes

@Composable
fun distanceText(meters: Double): String = stringResource(R.string.route_distance_km, RouteFormat.km(meters))

@Composable
fun durationText(seconds: Double): String {
    val minutes = RouteFormat.minutes(seconds)
    return if (minutes < 60) {
        stringResource(R.string.route_duration_min, minutes)
    } else {
        stringResource(R.string.route_duration_h_min, minutes / 60, minutes % 60)
    }
}

@Composable
fun speedChoiceText(choice: SpeedChoice): String = when (choice) {
    is SpeedChoice.Preset -> stringResource(choice.preset.labelRes)
    is SpeedChoice.Custom -> "${displayKmh(choice.speedMps)} ${stringResource(R.string.unit_kilometers_per_hour)}"
}

@get:StringRes
val RoadProfile.labelRes: Int
    get() = when (this) {
        RoadProfile.CAR -> R.string.route_profile_car
        RoadProfile.BIKE -> R.string.route_profile_bike
        RoadProfile.FOOT -> R.string.route_profile_foot
    }

@StringRes
fun roadErrorRes(kind: RoutingException.Kind): Int = when (kind) {
    RoutingException.Kind.NO_ROUTE -> R.string.route_road_no_route
    RoutingException.Kind.NETWORK -> R.string.route_road_network
    RoutingException.Kind.SERVER -> R.string.route_road_server
    RoutingException.Kind.PARSE -> R.string.route_road_parse
}
