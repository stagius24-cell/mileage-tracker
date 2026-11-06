package com.businesstracker.mileage

import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateMileageRateHistoricalTest {

    private lateinit var useCase: CalculateMileageRateUseCase

    @Before
    fun setUp() {
        useCase = CalculateMileageRateUseCase()
    }

    @Test
    fun getApplicableRate_year2023_returns65Point5Cents() {
        assertEquals(0.655, useCase.getApplicableRate("2023-04-15"), 0.0001)
        assertEquals(0.655, useCase.getApplicableRate("2023-12-31"), 0.0001)
    }

    @Test
    fun getApplicableRate_year2024_returns67Cents() {
        assertEquals(0.670, useCase.getApplicableRate("2024-01-01"), 0.0001)
        assertEquals(0.670, useCase.getApplicableRate("2024-07-04"), 0.0001)
        assertEquals(0.670, useCase.getApplicableRate("2024-12-31"), 0.0001)
    }

    @Test
    fun getApplicableRate_year2025_returns70Cents() {
        assertEquals(0.700, useCase.getApplicableRate("2025-03-20"), 0.0001)
        assertEquals(0.700, useCase.getApplicableRate("2025-11-15"), 0.0001)
    }

    @Test
    fun getApplicableRate_year2026_preAndPostJulySplit() {
        assertEquals(0.725, useCase.getApplicableRate("2026-01-01"), 0.0001)
        assertEquals(0.725, useCase.getApplicableRate("2026-06-30"), 0.0001)
        assertEquals(0.760, useCase.getApplicableRate("2026-07-01"), 0.0001)
        assertEquals(0.760, useCase.getApplicableRate("2026-12-31"), 0.0001)
    }

    @Test
    fun getApplicableRate_invalidOrEmptyDate_defaultsSafely() {
        assertEquals(0.725, useCase.getApplicableRate("invalid-date"), 0.0001)
        assertEquals(0.725, useCase.getApplicableRate(""), 0.0001)
    }

    @Test
    fun calculateDeduction_confirmedBusinessOnly() {
        val miles = 150.5
        val rate2024 = 0.670
        val deduction = useCase.calculateDeduction(miles, rate2024)
        // 150.5 * 0.67 = 100.835 -> rounded to 100.84
        assertEquals(100.84, deduction, 0.001)
    }

    @Test
    fun getHistoricalRates_containsExpectedRecords() {
        val rates = useCase.getHistoricalRates()
        assertTrue(rates.size >= 5)
        assertTrue(rates.any { it.year == 2023 && it.rate == 0.655 })
        assertTrue(rates.any { it.year == 2024 && it.rate == 0.670 })
        assertTrue(rates.any { it.year == 2025 && it.rate == 0.700 })
        assertTrue(rates.any { it.year == 2026 && it.rate == 0.725 })
        assertTrue(rates.any { it.year == 2026 && it.rate == 0.760 })
    }
}
