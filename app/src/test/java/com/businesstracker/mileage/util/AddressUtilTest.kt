package com.businesstracker.mileage.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressUtilTest {

    @Test
    fun testAbbreviateAddress_avenueAndCalifornia() {
        val input = "2392 Bailey Avenue, San Jose, California"
        val expected = "2392 Bailey Ave, San Jose, CA"
        assertEquals(expected, AddressUtil.abbreviateAddress(input))
    }

    @Test
    fun testAbbreviateAddress_streetAndMultipleWords() {
        val input = "470 North Santa Cruz Avenue, Los Gatos, California"
        val expected = "470 North Santa Cruz Ave, Los Gatos, CA"
        assertEquals(expected, AddressUtil.abbreviateAddress(input))
    }

    @Test
    fun testAbbreviateAddress_otherSuffixesAndStates() {
        assertEquals("100 Market St, New York, NY", AddressUtil.abbreviateAddress("100 Market Street, New York, New York"))
        assertEquals("500 Ocean Blvd, Miami, FL", AddressUtil.abbreviateAddress("500 Ocean Boulevard, Miami, Florida"))
        assertEquals("12 Texas Dr, Austin, TX", AddressUtil.abbreviateAddress("12 Texas Drive, Austin, Texas"))
        assertEquals("78 Pine Rd, Seattle, WA", AddressUtil.abbreviateAddress("78 Pine Road, Seattle, Washington"))
    }

    @Test
    fun testAbbreviateAddress_nullOrBlank() {
        assertEquals("", AddressUtil.abbreviateAddress(""))
        assertEquals("", AddressUtil.abbreviateAddress(null))
        assertEquals("Approximate destination unavailable", AddressUtil.abbreviateAddress("Approximate destination unavailable"))
    }
}
