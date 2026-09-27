package com.example.skilkavach

import com.example.skilkavach.ar.HazardDiffusionSimulator
import com.example.skilkavach.ar.IndustrialMeshes
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Gas Leak & Confined Space AR Module physics, scale calibration,
 * 3D composite meshes, stratification, and diffusion simulation.
 */
class GasSimulationScaleTest {

    @Test
    fun testRealWorldScaleCalibration() {
        val verificationMap = IndustrialMeshes.verifyRealWorldScale()
        println("=== Gas & Confined Space Real-World Scale Calibration Report ===")
        verificationMap.forEach { (name, report) ->
            println("Model: ${report.name} | Expected: ${report.expectedHeightMeters}m | Detected: ${report.detectedHeightMeters}m | Status: ${report.status}")
            assertEquals("Model '$name' scale verification failed!", "PASS", report.status)
        }
    }

    @Test
    fun testConfinedSpaceHatchMeshIntegrity() {
        val hatch = IndustrialMeshes.confinedSpaceHatch()
        assertEquals(0.70f, hatch.height, 0.05f)

        val partNames = hatch.parts.map { it.name }.toSet()
        assertTrue("Hatch collar missing", partNames.contains("collar_wall"))
        assertTrue("Hatch interior shaft missing", partNames.contains("shaft_interior"))
        assertTrue("Ladder rail missing", partNames.contains("ladder_rail_l"))
        assertTrue("Hatch lid missing", partNames.contains("hatch_lid"))
        assertTrue("Flange bolt missing", partNames.contains("flange_bolt_0"))
    }

    @Test
    fun testGasDetectorMeshIntegrity() {
        val detector = IndustrialMeshes.gasDetector()
        assertEquals(0.14f, detector.height, 0.03f)

        val partNames = detector.parts.map { it.name }.toSet()
        assertTrue("Detector casing missing", partNames.contains("casing"))
        assertTrue("LCD screen missing", partNames.contains("screen_lcd"))
        assertTrue("Sensor cap missing", partNames.contains("sensor_cap"))
    }

    @Test
    fun testRescueTripodMeshIntegrity() {
        val tripod = IndustrialMeshes.rescueTripod()
        assertEquals(1.75f, tripod.height, 0.05f)

        val partNames = tripod.parts.map { it.name }.toSet()
        assertTrue("Tripod leg missing", partNames.contains("tripod_leg_0"))
        assertTrue("Apex head missing", partNames.contains("apex_head"))
        assertTrue("Winch body missing", partNames.contains("winch_body"))
    }

    @Test
    fun testVentilationBlowerMeshIntegrity() {
        val blower = IndustrialMeshes.ventilationBlower()
        assertEquals(0.65f, blower.height, 0.05f)

        val partNames = blower.parts.map { it.name }.toSet()
        assertTrue("Blower casing missing", partNames.contains("blower_casing"))
        assertTrue("Blower motor missing", partNames.contains("blower_motor"))
        assertTrue("Intake grille missing", partNames.contains("blower_grille"))
        assertTrue("Flexible duct missing", partNames.contains("flexible_duct"))
        assertTrue("Carry handle missing", partNames.contains("carry_handle"))
        assertTrue("Mounting feet missing", partNames.contains("mounting_feet"))
    }

    @Test
    fun testVerticalStratificationAndDiffusion() {
        val sim = HazardDiffusionSimulator()

        val topSample = sim.getStratifiedSample(HazardDiffusionSimulator.SamplingLevel.TOP)
        val midSample = sim.getStratifiedSample(HazardDiffusionSimulator.SamplingLevel.MIDDLE)
        val botSample = sim.getStratifiedSample(HazardDiffusionSimulator.SamplingLevel.BOTTOM)

        // H2S is heavier than air (vapor density 1.19), so BOTTOM concentration should be highest
        assertTrue("H2S at BOTTOM (${botSample.h2sPpm}) should exceed TOP (${topSample.h2sPpm})", botSample.h2sPpm >= topSample.h2sPpm)
        assertTrue("O2 at BOTTOM (${botSample.oxygenPercent}) should be more displaced than TOP (${topSample.oxygenPercent})", botSample.oxygenPercent <= topSample.oxygenPercent)

        // Test mechanical ventilation dissipation
        sim.isVentilated = true
        val ventilatedSample = sim.getStratifiedSample(HazardDiffusionSimulator.SamplingLevel.BOTTOM)
        assertTrue("Ventilation should reduce H2S concentration below unventilated bottom sample", ventilatedSample.h2sPpm < botSample.h2sPpm)
    }

    @Test
    fun testHazardZoneClassification() {
        val sim = HazardDiffusionSimulator()

        // Distant point should be safe zone D
        val safeReadings = sim.getSimulatedReadings(5.0f, 0.0f, 5.0f)
        assertEquals(HazardDiffusionSimulator.HazardZone.ZONE_D_SAFE, safeReadings.zone)
        assertFalse(safeReadings.isAlarm)

        // Point near bottom of leak source should be Critical Zone A
        val criticalReadings = sim.getSimulatedReadings(sim.sourceX, -0.85f, sim.sourceZ)
        assertEquals(HazardDiffusionSimulator.HazardZone.ZONE_A_CRITICAL, criticalReadings.zone)
        assertTrue(criticalReadings.isAlarm)
    }
}
