package com.example.skilkavach

import com.example.skilkavach.ar.HazardDiffusionSimulator
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the 3D Gas Leak & Confined Space Hazard Diffusion Simulator.
 *
 * Verifies:
 * - 3D dispersion profiles and distance attenuation
 * - Stratified gas accumulation (heavier-than-air H2S pooling in sump floor)
 * - Atmospheric testing depth samples (Top, Middle, Bottom)
 * - Multi-gas sensor readings (O2 displacement, combustible LEL, H2S ppm, CO ppm)
 * - Hazard zone classification and alarm triggers
 * - Mechanical ventilation effects
 */
class GasHazardSimulationTest {

    @Test
    fun testStratifiedGasAccumulation() {
        val simulator = HazardDiffusionSimulator()

        // Sample at 3 standardized depths
        val topReading = simulator.getStratifiedSample(HazardDiffusionSimulator.SamplingLevel.TOP)
        val midReading = simulator.getStratifiedSample(HazardDiffusionSimulator.SamplingLevel.MIDDLE)
        val bottomReading = simulator.getStratifiedSample(HazardDiffusionSimulator.SamplingLevel.BOTTOM)

        // Sump floor (bottom) should have significantly higher toxic H2S concentration than top rim
        assertTrue(
            "Bottom H2S (${bottomReading.h2sPpm}) must be higher than Top H2S (${topReading.h2sPpm}) due to vapor density pooling",
            bottomReading.h2sPpm > topReading.h2sPpm
        )

        // Middle should be between Top and Bottom
        assertTrue(
            "Middle H2S (${midReading.h2sPpm}) must be >= Top H2S (${topReading.h2sPpm})",
            midReading.h2sPpm >= topReading.h2sPpm
        )

        // Oxygen displacement: bottom should have lower or equal O2 than top
        assertTrue(
            "Oxygen at bottom (${bottomReading.oxygenPercent}) must be lower or equal to top (${topReading.oxygenPercent}) due to gas displacement",
            bottomReading.oxygenPercent <= topReading.oxygenPercent
        )

        // Bottom reading must trigger critical or high alarm due to H2S accumulation
        assertTrue(
            "Bottom atmosphere in confined space sump pit must trigger alarm",
            bottomReading.isAlarm
        )
    }

    @Test
    fun testDistanceAttenuation() {
        val simulator = HazardDiffusionSimulator()

        // Near source (0, -0.5, -1.2)
        val nearConcentration = simulator.getConcentrationAt(0.0f, -0.5f, -1.2f)

        // Far away outside perimeter (3.0, 0.0, -1.2)
        val farConcentration = simulator.getConcentrationAt(3.0f, 0.0f, -1.2f)

        assertTrue(
            "Concentration near source ($nearConcentration) must be higher than far perimeter ($farConcentration)",
            nearConcentration > farConcentration
        )
        assertEquals("Far away concentration should be near zero", 0f, farConcentration, 0.05f)
    }

    @Test
    fun testMechanicalVentilationEffect() {
        val simulator = HazardDiffusionSimulator()

        val unventilatedConcentration = simulator.getConcentrationAt(0.0f, -0.3f, -1.2f)

        simulator.isVentilated = true
        val ventilatedConcentration = simulator.getConcentrationAt(0.0f, -0.3f, -1.2f)

        assertTrue(
            "Ventilation must reduce hazardous gas concentration (unventilated=$unventilatedConcentration, ventilated=$ventilatedConcentration)",
            ventilatedConcentration < unventilatedConcentration
        )
    }

    @Test
    fun testMultiGasReadingsPhysicalBounds() {
        val simulator = HazardDiffusionSimulator()

        for (level in HazardDiffusionSimulator.SamplingLevel.values()) {
            val readings = simulator.getStratifiedSample(level)

            // O2 baseline bounds: between 16.0% (severe deficiency) and 20.9% (normal atmosphere)
            assertTrue("O2 must be in reasonable range: ${readings.oxygenPercent}", readings.oxygenPercent in 16.0f..21.0f)

            // LEL bounds: >= 0%
            assertTrue("LEL must be non-negative: ${readings.lelPercent}", readings.lelPercent >= 0.0f)

            // H2S bounds: >= 0 ppm
            assertTrue("H2S must be non-negative: ${readings.h2sPpm}", readings.h2sPpm >= 0.0f)

            // CO bounds: >= 0 ppm
            assertTrue("CO must be non-negative: ${readings.coPpm}", readings.coPpm >= 0.0f)

            // Status message must not be empty
            assertNotNull(readings.statusMessage)
            assertTrue(readings.statusMessage.isNotEmpty())
        }
    }

    @Test
    fun testHazardZoneClassifications() {
        val simulator = HazardDiffusionSimulator()

        // Critical zone at the sump floor
        val bottomReadings = simulator.getStratifiedSample(HazardDiffusionSimulator.SamplingLevel.BOTTOM)
        assertTrue(
            "Bottom sample should be critical or high hazard zone",
            bottomReadings.zone == HazardDiffusionSimulator.HazardZone.ZONE_A_CRITICAL ||
            bottomReadings.zone == HazardDiffusionSimulator.HazardZone.ZONE_B_HIGH
        )

        // Far outside perimeter
        val safeReadings = simulator.getSimulatedReadings(3.5f, 0.5f, 1.0f)
        assertEquals(
            "Far perimeter should be in Safe Zone D",
            HazardDiffusionSimulator.HazardZone.ZONE_D_SAFE,
            safeReadings.zone
        )
        assertFalse("Safe zone must not trigger alarm", safeReadings.isAlarm)
    }
}
