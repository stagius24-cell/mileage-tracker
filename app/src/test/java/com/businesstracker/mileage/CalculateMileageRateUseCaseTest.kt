package com.businesstracker.mileage

import com.businesstracker.mileage.domain.usecase.CalculateMileageRateUseCase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CalculateMileageRateUseCaseTest {

    private lateinit var useCase: CalculateMileageRateUseCase

    @Before
    fun setUp() {
        useCase = CalculateMileageRateUseCase()
    }

    @Test
    fun getApplicableRate_beforeJuly2026_returns72Point5Cents() {
        val rate = useCase.getApplicableRate("2026-06-30")
        assertEquals(0.725, rate, 0.0001)
    }

    @Test
    fun getApplicableRate_onJuly1st2026_returns76Cents() {
        val rate = useCase.getApplicableRate("2026-07-01")
        assertEquals(0.760, rate, 0.0001)
    }

    @Test
    fun getApplicableRate_afterJuly2026_returns76Cents() {
        val rate = useCase.getApplicableRate("2026-11-05")
        assertEquals(0.760, rate, 0.0001)
    }

    @Test
    fun calculateDeduction_computesCorrectly() {
        val miles = 100.0
        val rate = 0.725
        val deduction = useCase.calculateDeduction(miles, rate)
        assertEquals(72.50, deduction, 0.01)

        val newDeduction = useCase.calculateDeduction(miles, 0.760)
        assertEquals(76.00, newDeduction, 0.01)
    }
}
