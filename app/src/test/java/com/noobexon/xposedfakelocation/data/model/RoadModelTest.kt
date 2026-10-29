package com.noobexon.xposedfakelocation.data.model

import com.noobexon.xposedfakelocation.data.EncodedPolyline
import com.noobexon.xposedfakelocation.manager.route.RouteEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadModelTest {
    private val a = RoutePoint("a", 1.0, 2.0)
    private val b = RoutePoint("b", 1.1, 2.1)
    private val c = RoutePoint("c", 1.2, 2.2)
    private val polyline = EncodedPolyline.encode(listOf(1.0 to 2.0, 1.05 to 2.02, 1.1 to 2.1))
    private fun path(profile: RoadProfile = RoadProfile.FOOT) =
        RoadPath(profile, 1.0, 2.0, 1.1, 2.1, polyline, 15_000.0)
    private fun route(vararg points: RoutePoint) = Route(id = "r", name = "Trip", points = points.toList())

    @Test
    fun presetsMapToProfiles() {
        assertEquals(RoadProfile.FOOT, SpeedChoice.Preset(JoystickPreset.WALK).roadProfile())
        assertEquals(RoadProfile.FOOT, SpeedChoice.Preset(JoystickPreset.RUN).roadProfile())
        assertEquals(RoadProfile.BIKE, SpeedChoice.Preset(JoystickPreset.BIKE).roadProfile())
        assertEquals(RoadProfile.CAR, SpeedChoice.Preset(JoystickPreset.DRIVE).roadProfile())
        assertEquals(RoadProfile.CAR, SpeedChoice.Custom(5f).roadProfile())
        assertEquals(RoadProfile.BIKE, SpeedChoice.Custom(5f, RoadProfile.BIKE).roadProfile())
    }

    @Test
    fun segmentOverridesRouteModeAndSpeed() {
        val r = route(a, b, c).copy(
            followRoads = true,
            segments = mapOf(
                segmentKey("b", "c") to SegmentSettings(followRoads = false, speed = SpeedChoice.Preset(JoystickPreset.DRIVE)),
            ),
        )
        assertTrue(r.followsRoads("a", "b"))
        assertFalse(r.followsRoads("c", "b"))
        assertEquals(RoadProfile.FOOT, r.effectiveProfile("a", "b"))
        assertEquals(RoadProfile.CAR, r.effectiveProfile("b", "c"))
    }

    @Test
    fun sanitizeDropsBadPathsButKeepsTheRoute() {
        val r = route(a, b).copy(
            roadPaths = mapOf(
                "a>b" to path(),
                "b>a" to path().copy(polyline = "~~~~"),
                "a>x" to path(),
                "ab" to path(),
                "b>a " to path(),
            ),
        )
        assertEquals(setOf("a>b"), r.sanitizeRoadPaths().roadPaths.keys)
        assertFalse(path().copy(distanceM = Double.NaN).isValid())
        assertFalse(path().copy(fromLatitude = 91.0).isValid())
        assertFalse(path().copy(polyline = EncodedPolyline.encode(listOf(1.0 to 2.0))).isValid())
    }

    @Test
    fun oldJsonLoadsWithRoadsOffAndNoPaths() {
        val json = """[{"id":"r","name":"Old","points":[{"id":"a","latitude":1.0,"longitude":2.0},""" +
            """{"id":"b","latitude":1.1,"longitude":2.1}],"segments":{},""" +
            """"defaultSpeed":{"type":"preset","preset":"WALK"},"loop":false,"reversed":false}]"""
        val loaded = RouteJson.parseRoutes(json).single()
        assertFalse(loaded.followRoads)
        assertEquals(emptyMap<String, RoadPath>(), loaded.roadPaths)
    }

    @Test
    fun customProfileAndPathsRoundTrip() {
        val r = route(a, b).copy(
            followRoads = true,
            defaultSpeed = SpeedChoice.Custom(8f, RoadProfile.BIKE),
            roadPaths = mapOf("a>b" to path(RoadProfile.BIKE)),
        )
        val loaded = RouteJson.parseRoutes(RouteJson.gson.toJson(listOf(r))).single()
        assertEquals(r, loaded)
    }

    @Test
    fun draftIsSanitizedOnLoad() {
        val draft = RouteDraft(route(a, b).copy(roadPaths = mapOf("a>b" to path(), "b>a" to path().copy(polyline = "!!"))))
        val loaded = RouteJson.parseDraft(RouteJson.gson.toJson(draft))!!
        assertEquals(setOf("a>b"), loaded.route.roadPaths.keys)
    }

    @Test
    fun pruneDropsPathsOfGoneSegments() {
        val r = route(a, b, c).copy(roadPaths = mapOf("a>b" to path(), "b>a" to path(), "a>c" to path()))
        assertEquals(setOf("a>b", "b>a"), RouteEditor.pruneSegments(r).roadPaths.keys)
    }

    @Test
    fun setProfileEditsTheCustomSpeedInUse() {
        val own = route(a, b).copy(segments = mapOf(segmentKey("a", "b") to SegmentSettings(speed = SpeedChoice.Custom(5f))))
        assertEquals(
            SpeedChoice.Custom(5f, RoadProfile.FOOT),
            RouteEditor.setProfile(own, "a", "b", RoadProfile.FOOT).segmentSettings("a", "b").speed,
        )
        val viaDefault = route(a, b).copy(defaultSpeed = SpeedChoice.Custom(5f))
        assertEquals(
            SpeedChoice.Custom(5f, RoadProfile.BIKE),
            RouteEditor.setProfile(viaDefault, "b", "a", RoadProfile.BIKE).defaultSpeed,
        )
        val preset = route(a, b)
        assertEquals(preset, RouteEditor.setProfile(preset, "a", "b", RoadProfile.CAR))
    }
}
