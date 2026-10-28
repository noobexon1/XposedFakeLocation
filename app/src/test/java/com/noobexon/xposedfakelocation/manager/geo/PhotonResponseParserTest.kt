package com.noobexon.xposedfakelocation.manager.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PhotonResponseParserTest {
    private fun feature(lon: Any, lat: Any, properties: String) =
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{$properties}}"""

    private fun collection(vararg features: String) =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    @Test
    fun poiWithNameUsesNameAsTitle() {
        val json = collection(
            feature(34.7745, 32.0754, """"name":"Dizengoff Center","street":"Dizengoff","housenumber":"50","city":"Tel Aviv","state":"Tel Aviv District","country":"Israel"""")
        )
        assertEquals(
            listOf(Place("Dizengoff Center", "Tel Aviv, Tel Aviv District, Israel", 32.0754, 34.7745)),
            PhotonResponseParser.parse(json),
        )
    }

    @Test
    fun addressWithoutNameUsesStreetAndHouseNumber() {
        val json = collection(feature(13.4, 52.5, """"street":"Unter den Linden","housenumber":"77","city":"Berlin","country":"Germany""""))
        assertEquals("Unter den Linden 77", PhotonResponseParser.parse(json).single().title)
    }

    @Test
    fun cityOnlyResultUsesCityAsTitleAndDropsItFromSubtitle() {
        val json = collection(feature(2.35, 48.85, """"city":"Paris","state":"Ile-de-France","country":"France""""))
        val place = PhotonResponseParser.parse(json).single()
        assertEquals("Paris", place.title)
        assertEquals("Ile-de-France, France", place.subtitle)
    }

    @Test
    fun duplicateSubtitlePartsAreRemoved() {
        val json = collection(feature(103.8, 1.29, """"name":"Marina Bay","city":"Singapore","state":"Singapore","country":"Singapore""""))
        assertEquals("Singapore", PhotonResponseParser.parse(json).single().subtitle)
    }

    @Test
    fun featuresWithoutTitleOrWithBadCoordinatesAreSkipped() {
        val json = collection(
            feature(1.0, 2.0, """"country":"Nowhere""""),
            feature(1.0, 95.0, """"name":"Too far north""""),
            feature("\"x\"", 2.0, """"name":"Not a number""""),
            """{"type":"Feature","properties":{"name":"No geometry"}}""",
            feature(1.0, 2.0, """"name":"Good""""),
        )
        assertEquals(listOf("Good"), PhotonResponseParser.parse(json).map { it.title })
    }

    @Test
    fun emptyFeatureListGivesEmptyResult() {
        assertTrue(PhotonResponseParser.parse(collection()).isEmpty())
    }

    @Test
    fun malformedJsonThrowsParseError() {
        listOf("", "not json", "[]", """{"features":"nope"}""").forEach { json ->
            try {
                PhotonResponseParser.parse(json)
                fail("Expected GeocodingException for: $json")
            } catch (e: GeocodingException) {
                assertEquals(GeocodingException.Kind.PARSE, e.kind)
            }
        }
    }
}
