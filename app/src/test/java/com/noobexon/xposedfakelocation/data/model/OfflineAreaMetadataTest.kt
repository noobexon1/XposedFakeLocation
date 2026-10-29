package com.noobexon.xposedfakelocation.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineAreaMetadataTest {
    private val sample = OfflineAreaMetadata(
        areaId = "area-1",
        name = "Tel Aviv",
        createdAt = 1_760_000_000_000L,
        style = OfflineAreaStyle.DARK,
        styleUrl = "https://tiles.openfreemap.org/styles/dark",
    )

    @Test
    fun roundTrips() {
        assertEquals(sample, OfflineAreaMetadataCodec.decode(OfflineAreaMetadataCodec.encode(sample)))
    }

    @Test
    fun unreadableBytesGiveNull() {
        assertNull(OfflineAreaMetadataCodec.decode(null))
        assertNull(OfflineAreaMetadataCodec.decode(ByteArray(0)))
        assertNull(OfflineAreaMetadataCodec.decode("not json".toByteArray()))
        assertNull(OfflineAreaMetadataCodec.decode("[1, 2]".toByteArray()))
    }

    @Test
    fun incompleteMetadataGivesNull() {
        assertNull(OfflineAreaMetadataCodec.decode("""{"areaId":"a","createdAt":1,"style":"light","styleUrl":"u"}""".toByteArray()))
        assertNull(OfflineAreaMetadataCodec.decode("""{"areaId":"a","name":"  ","createdAt":1,"style":"light","styleUrl":"u"}""".toByteArray()))
        assertNull(OfflineAreaMetadataCodec.decode("""{"name":"n","createdAt":1,"style":"light","styleUrl":"u"}""".toByteArray()))
    }
}
