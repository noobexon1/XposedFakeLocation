package com.noobexon.xposedfakelocation.data.backup

import com.noobexon.xposedfakelocation.data.EncodedPolyline
import com.noobexon.xposedfakelocation.data.model.AppearanceBackup
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.JoystickBackup
import com.noobexon.xposedfakelocation.data.model.JoystickMode
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.RoadPath
import com.noobexon.xposedfakelocation.data.model.RoadProfile
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.data.model.SETTINGS_BACKUP_VERSION
import com.noobexon.xposedfakelocation.data.model.SettingsBackup
import com.noobexon.xposedfakelocation.data.model.SpoofingBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCodecTest {

    private val codec = SettingsBackupCodec()

    private fun sampleBackup() = SettingsBackup(
        version = SETTINGS_BACKUP_VERSION,
        exportedAt = 1_760_000_000_000L,
        appVersion = "1.2.3",
        spoofing = SpoofingBackup(
            useRandomize = true, randomizeRadius = 12.5,
            useAccuracy = true, accuracy = 5.0,
            useAltitude = false, altitude = 0.0,
            useVerticalAccuracy = false, verticalAccuracy = 0.0f,
            useMeanSeaLevel = false, meanSeaLevel = 0.0,
            useMeanSeaLevelAccuracy = false, meanSeaLevelAccuracy = 0.0f,
            useSpeed = true, speed = 3.5f,
            useSpeedAccuracy = false, speedAccuracy = 0.0f,
            hideFakeLocationToast = true,
            targetApps = listOf("com.example.a", "com.example.b"),
            wifiIdentityEnabled = true,
            wifiSsid = "MyNet", wifiBssid = "02:00:00:00:00:00", wifiRssi = -55,
        ),
        favorites = listOf(
            FavoriteLocation("Home", 40.0, -73.0, "desc"),
            FavoriteLocation("Work", 41.0, -74.0),
        ),
        appearance = AppearanceBackup(themeOption = "DARK", languageTag = "de"),
        joystick = JoystickBackup(
            mode = "CONSTANT", maxSpeed = 4f, walkSpeed = 1.5f, runSpeed = 3.2f,
            bikeSpeed = 6f, driveSpeed = 15f, selectedPreset = "BIKE",
        ),
        routes = listOf(
            Route(
                id = "r1", name = "Loop",
                points = listOf(RoutePoint("a", 1.0, 2.0, label = "Dizengoff Center"), RoutePoint("b", 1.1, 2.1)),
                defaultSpeed = SpeedChoice.Custom(4f), loop = true,
            ),
        ),
        geocodingServerUrl = "https://photon.example.org",
        routingServerUrl = "https://osrm.example.org/{profile}",
        mapRenderer = "maplibre",
        mapStyleUrl = "https://tiles.example.org/style.json?key=abc",
    )

    @Test
    fun roundTripProducesEqualBackup() {
        val original = sampleBackup()
        val restored = codec.fromJson(codec.toJson(original)).getOrThrow()
        assertEquals(original, restored)
    }

    @Test
    fun invalidRoutesAreDroppedOnImport() {
        val single = sampleBackup().routes!!.first().copy(id = "r2", points = listOf(RoutePoint("a", 1.0, 2.0)))
        val backup = sampleBackup().copy(routes = sampleBackup().routes!! + single)
        val restored = codec.fromJson(codec.toJson(backup)).getOrThrow()
        assertEquals(sampleBackup().routes, restored.routes)
    }

    @Test
    fun backupWithoutRoutesImportsWithNullRoutes() {
        val json = codec.toJson(sampleBackup().copy(routes = null))
        assertNull(codec.fromJson(json).getOrThrow().routes)
    }

    @Test
    fun routePointLabelsAreSanitizedOnImport() {
        val route = sampleBackup().routes!!.first().copy(
            points = listOf(
                RoutePoint("a", 1.0, 2.0, label = "  Home  "),
                RoutePoint("b", 1.1, 2.1, label = "   "),
                RoutePoint("c", 1.2, 2.2, label = "x".repeat(250)),
            )
        )
        val json = codec.toJson(sampleBackup().copy(routes = listOf(route)))
        val labels = codec.fromJson(json).getOrThrow().routes!!.single().points.map { it.label }
        assertEquals(listOf("Home", null, "x".repeat(200)), labels)
    }

    @Test
    fun invalidGeocodingServerUrlIsDropped() {
        val json = codec.toJson(sampleBackup().copy(geocodingServerUrl = "ftp://nope"))
        assertNull(codec.fromJson(json).getOrThrow().geocodingServerUrl)
    }

    @Test
    fun geocodingServerUrlIsNormalizedOnImport() {
        val json = codec.toJson(sampleBackup().copy(geocodingServerUrl = " https://photon.example.org/ "))
        assertEquals("https://photon.example.org", codec.fromJson(json).getOrThrow().geocodingServerUrl)
    }

    @Test
    fun backupWithoutGeocodingServerUrlImportsWithNull() {
        val json = codec.toJson(sampleBackup().copy(geocodingServerUrl = null))
        assertNull(codec.fromJson(json).getOrThrow().geocodingServerUrl)
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val json = codec.toJson(sampleBackup()).replace(
            "\"version\":1", "\"version\":999"
        )
        val result = codec.fromJson(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SettingsBackupException)
    }

    @Test
    fun malformedJsonIsRejectedWithoutThrowing() {
        assertTrue(codec.fromJson("not valid json at all").isFailure)
        assertTrue(codec.fromJson("").isFailure)
        // "{}" parses but version == 0 → SettingsBackupException from version gate
        val emptyObjResult = codec.fromJson("{}")
        assertTrue(emptyObjResult.isFailure)
        assertTrue(emptyObjResult.exceptionOrNull() is SettingsBackupException)
    }

    // Shared minimal spoofing/appearance JSON fragment reused in raw-JSON tests.
    private val minimalSpoofingJson = """
        "spoofing":{"useRandomize":false,"randomizeRadius":0.0,"useAccuracy":false,"accuracy":0.0,
          "useAltitude":false,"altitude":0.0,"useVerticalAccuracy":false,"verticalAccuracy":0.0,
          "useMeanSeaLevel":false,"meanSeaLevel":0.0,"useMeanSeaLevelAccuracy":false,"meanSeaLevelAccuracy":0.0,
          "useSpeed":false,"speed":0.0,"useSpeedAccuracy":false,"speedAccuracy":0.0,
          "hideFakeLocationToast":false,"targetApps":[],"wifiIdentityEnabled":false,
          "wifiSsid":"AndroidAP","wifiBssid":"02:00:00:00:00:00","wifiRssi":-60}
    """.trimIndent()

    @Test
    fun nullNameFavoriteIsSkippedNotFailed() {
        // The first favorite omits "name" entirely; Gson leaves it null at runtime.
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             $minimalSpoofingJson,
             "favorites":[{"latitude":1.0,"longitude":2.0,"description":""},
                          {"name":"Valid","latitude":3.0,"longitude":4.0,"description":""}],
             "appearance":{"themeOption":"","languageTag":""}}
        """.trimIndent()
        val result = codec.fromJson(json)
        assertTrue(result.isSuccess)
        val restored = result.getOrThrow()
        assertEquals(1, restored.favorites.size)
        assertEquals("Valid", restored.favorites.first().name)
    }

    @Test
    fun missingSpoofingSectionIsRejected() {
        // version:1 passes the version gate but the absent spoofing section must be rejected.
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             "favorites":[],
             "appearance":{"themeOption":"","languageTag":""}}
        """.trimIndent()
        val result = codec.fromJson(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SettingsBackupException)
    }

    @Test
    fun nonFiniteCoordinateFavoriteIsDropped() {
        // 1e999 overflows to Double.POSITIVE_INFINITY when Gson parses it; isFinite() == false.
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             $minimalSpoofingJson,
             "favorites":[{"name":"Bad","latitude":1e999,"longitude":0.0,"description":""},
                          {"name":"Good","latitude":1.0,"longitude":2.0,"description":""}],
             "appearance":{"themeOption":"","languageTag":""}}
        """.trimIndent()
        val result = codec.fromJson(json)
        assertTrue(result.isSuccess)
        val restored = result.getOrThrow()
        assertEquals(1, restored.favorites.size)
        assertEquals("Good", restored.favorites.first().name)
    }

    @Test
    fun corruptFavoritesAreSkipped() {
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             "spoofing":{"useRandomize":false,"randomizeRadius":0.0,"useAccuracy":false,"accuracy":0.0,
               "useAltitude":false,"altitude":0.0,"useVerticalAccuracy":false,"verticalAccuracy":0.0,
               "useMeanSeaLevel":false,"meanSeaLevel":0.0,"useMeanSeaLevelAccuracy":false,"meanSeaLevelAccuracy":0.0,
               "useSpeed":false,"speed":0.0,"useSpeedAccuracy":false,"speedAccuracy":0.0,
               "hideFakeLocationToast":false,"targetApps":[],"wifiIdentityEnabled":false,
               "wifiSsid":"AndroidAP","wifiBssid":"02:00:00:00:00:00","wifiRssi":-60},
             "favorites":[{"name":"Good","latitude":1.0,"longitude":2.0,"description":""},
                          {"name":"","latitude":3.0,"longitude":4.0,"description":""}],
             "appearance":{"themeOption":"","languageTag":""}}
        """.trimIndent()
        val restored = codec.fromJson(json).getOrThrow()
        assertEquals(1, restored.favorites.size)
        assertEquals("Good", restored.favorites.first().name)
    }

    @Test
    fun fileWithoutJoystickSectionImportsWithNullJoystick() {
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             $minimalSpoofingJson,
             "favorites":[],
             "appearance":{"themeOption":"","languageTag":""}}
        """.trimIndent()
        val restored = codec.fromJson(json).getOrThrow()
        assertNull(restored.joystick)
    }

    @Test
    fun invalidJoystickSpeedsDropOnlyTheJoystickSection() {
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             $minimalSpoofingJson,
             "favorites":[{"name":"Good","latitude":1.0,"longitude":2.0,"description":""}],
             "appearance":{"themeOption":"","languageTag":""},
             "joystick":{"mode":"DYNAMIC","maxSpeed":-1.0,"walkSpeed":1.4,"runSpeed":3.0,
                         "bikeSpeed":5.5,"driveSpeed":14.0,"selectedPreset":"WALK"}}
        """.trimIndent()
        val restored = codec.fromJson(json).getOrThrow()
        assertNull(restored.joystick)
        assertEquals(1, restored.favorites.size)
    }

    @Test
    fun joystickSpeedAboveSettingsRangeDropsTheJoystickSection() {
        // 60 m/s = 216 km/h, above the 200 km/h Settings slider maximum.
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             $minimalSpoofingJson,
             "favorites":[],
             "appearance":{"themeOption":"","languageTag":""},
             "joystick":{"mode":"DYNAMIC","maxSpeed":60.0,"walkSpeed":1.4,"runSpeed":3.0,
                         "bikeSpeed":5.5,"driveSpeed":14.0,"selectedPreset":"WALK"}}
        """.trimIndent()
        assertNull(codec.fromJson(json).getOrThrow().joystick)
    }

    @Test
    fun unknownJoystickEnumNamesFallBackToDefaults() {
        val settings = JoystickBackup("TELEPORT", 3f, 1.4f, 3f, 5.5f, 14f, "JETPACK").toSettings()
        assertEquals(JoystickMode.DYNAMIC, settings.mode)
        assertEquals(JoystickPreset.WALK, settings.selectedPreset)
    }

    @Test
    fun roadPathsRoundTripAndBadOnesAreDropped() {
        val good = RoadPath(RoadProfile.CAR, 1.0, 2.0, 1.1, 2.1, EncodedPolyline.encode(listOf(1.0 to 2.0, 1.1 to 2.1)), 15_000.0)
        val route = sampleBackup().routes!!.first().copy(
            followRoads = true,
            roadPaths = mapOf("a>b" to good, "b>a" to good.copy(polyline = "~~~~"), "a>zz" to good),
        )
        val restored = codec.fromJson(codec.toJson(sampleBackup().copy(routes = listOf(route)))).getOrThrow()
        val loaded = restored.routes!!.single()
        assertTrue(loaded.followRoads)
        assertEquals(mapOf("a>b" to good), loaded.roadPaths)
    }

    @Test
    fun routingServerUrlIsNormalizedOnImport() {
        val trailing = codec.toJson(sampleBackup().copy(routingServerUrl = " https://osrm.example.org/{profile}/ "))
        assertEquals("https://osrm.example.org/{profile}", codec.fromJson(trailing).getOrThrow().routingServerUrl)
        val invalid = codec.toJson(sampleBackup().copy(routingServerUrl = "https://a.org/{x}"))
        assertNull(codec.fromJson(invalid).getOrThrow().routingServerUrl)
    }

    @Test
    fun backupWithoutRoutingServerImportsWithNull() {
        val json = codec.toJson(sampleBackup().copy(routingServerUrl = null))
        assertNull(codec.fromJson(json).getOrThrow().routingServerUrl)
    }

    @Test
    fun unknownMapRendererIsDropped() {
        val json = codec.toJson(sampleBackup().copy(mapRenderer = "leaflet"))
        assertNull(codec.fromJson(json).getOrThrow().mapRenderer)
    }

    @Test
    fun mapStyleUrlIsNormalizedOnImport() {
        val trimmed = codec.toJson(sampleBackup().copy(mapStyleUrl = " https://tiles.example.org/style.json "))
        assertEquals("https://tiles.example.org/style.json", codec.fromJson(trimmed).getOrThrow().mapStyleUrl)
        val blank = codec.toJson(sampleBackup().copy(mapStyleUrl = ""))
        assertEquals("", codec.fromJson(blank).getOrThrow().mapStyleUrl)
        val invalid = codec.toJson(sampleBackup().copy(mapStyleUrl = "ftp://nope"))
        assertNull(codec.fromJson(invalid).getOrThrow().mapStyleUrl)
    }

    @Test
    fun backupWithoutMapSettingsImportsWithNulls() {
        val restored = codec.fromJson(codec.toJson(sampleBackup().copy(mapRenderer = null, mapStyleUrl = null))).getOrThrow()
        assertNull(restored.mapRenderer)
        assertNull(restored.mapStyleUrl)
    }
}
