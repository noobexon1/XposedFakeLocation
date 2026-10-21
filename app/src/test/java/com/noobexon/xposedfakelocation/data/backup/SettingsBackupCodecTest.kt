package com.noobexon.xposedfakelocation.data.backup

import com.noobexon.xposedfakelocation.data.model.AppearanceBackup
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.SETTINGS_BACKUP_VERSION
import com.noobexon.xposedfakelocation.data.model.SettingsBackup
import com.noobexon.xposedfakelocation.data.model.SpoofingBackup
import org.junit.Assert.assertEquals
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
    )

    @Test
    fun roundTripProducesEqualBackup() {
        val original = sampleBackup()
        val restored = codec.fromJson(codec.toJson(original)).getOrThrow()
        assertEquals(original, restored)
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val json = codec.toJson(sampleBackup()).replace(
            "\"version\":1", "\"version\":999"
        )
        assertTrue(codec.fromJson(json).isFailure)
    }

    @Test
    fun malformedJsonIsRejectedWithoutThrowing() {
        assertTrue(codec.fromJson("not valid json at all").isFailure)
        assertTrue(codec.fromJson("").isFailure)
        assertTrue(codec.fromJson("{}").isFailure)
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
}
