package com.reststop.countdown.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun `decodes the canonical Google polyline algorithm example`() {
        // From https://developers.google.com/maps/documentation/utilities/polylinealgorithm
        val decoded = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, decoded.size)
        assertEquals(38.5, decoded[0].latitude, 1e-5)
        assertEquals(-120.2, decoded[0].longitude, 1e-5)
        assertEquals(40.7, decoded[1].latitude, 1e-5)
        assertEquals(-120.95, decoded[1].longitude, 1e-5)
        assertEquals(43.252, decoded[2].latitude, 1e-5)
        assertEquals(-126.453, decoded[2].longitude, 1e-5)
    }

    @Test
    fun `empty string decodes to no points`() {
        assertEquals(emptyList<Any>(), PolylineDecoder.decode(""))
    }
}
