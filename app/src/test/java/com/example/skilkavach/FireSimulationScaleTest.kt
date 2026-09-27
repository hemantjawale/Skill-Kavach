package com.example.skilkavach

import com.example.skilkavach.ar.IndustrialMeshes
import org.junit.Assert.*
import org.junit.Test

/**
 * Real-World Scale and Physical Fidelity Unit Tests for Fire AR Industrial Simulation.
 *
 * Verifies:
 * - 1 world unit = 1 meter convention across all hero 3D assets.
 * - Extinguisher physical dimensions (0.50m height, human-scale).
 * - Electrical cabinet dimensions (0.65m height, 0.50m width, NEMA industrial scale).
 * - Wall-mounted fire alarm and illuminated exit sign proportions.
 * - Extinguisher safety pin pulling state machine (tamper seal breaks and pin removes on action).
 * - Automated scale calibration report validation.
 */
class FireSimulationScaleTest {

    @Test
    fun testRealWorldScaleCalibration() {
        val calibration = IndustrialMeshes.verifyRealWorldScale()
        assertTrue("Calibration must verify at least 4 hero objects", calibration.size >= 4)

        for ((objectName, result) in calibration) {
            assertEquals(
                "Object '$objectName' must pass real-world scale calibration. Result: ${result.status}, detected: ${result.detectedHeightMeters}m, expected: ${result.expectedHeightMeters}m",
                "PASS",
                result.status
            )
        }
    }

    @Test
    fun testFireExtinguisherDimensionsAndPinState() {
        // Unpulled extinguisher
        val intactExtinguisher = IndustrialMeshes.fireExtinguisher(isPinPulled = false)
        assertEquals("Extinguisher height must match real-world spec (~0.50m)", 0.50f, intactExtinguisher.height, 0.02f)

        val intactPartNames = intactExtinguisher.parts.map { it.name }
        assertTrue("Intact extinguisher must have body cylinder", intactPartNames.contains("body"))
        assertTrue("Intact extinguisher must have safety pin shaft", intactPartNames.contains("pin_shaft"))
        assertTrue("Intact extinguisher must have safety pin ring", intactPartNames.contains("pin_ring"))
        assertTrue("Intact extinguisher must have tamper seal", intactPartNames.contains("tamper_seal"))
        assertTrue("Intact extinguisher must have discharge nozzle", intactPartNames.contains("nozzle"))

        // Pulled extinguisher (user pulled pin)
        val armedExtinguisher = IndustrialMeshes.fireExtinguisher(isPinPulled = true)
        val armedPartNames = armedExtinguisher.parts.map { it.name }
        assertFalse("Armed extinguisher must NOT have safety pin shaft", armedPartNames.contains("pin_shaft"))
        assertFalse("Armed extinguisher must NOT have safety pin ring", armedPartNames.contains("pin_ring"))
        assertFalse("Armed extinguisher must NOT have intact tamper seal", armedPartNames.contains("tamper_seal"))
        assertTrue("Armed extinguisher maintains body", armedPartNames.contains("body"))
    }

    @Test
    fun testElectricalCabinetIndustrialFidelity() {
        val cabinet = IndustrialMeshes.electricalCabinetFire()
        assertEquals("Electrical cabinet height must match industrial NEMA spec (~0.65m)", 0.65f, cabinet.height, 0.02f)

        val partNames = cabinet.parts.map { it.name }
        assertTrue("Must include cabinet body", partNames.contains("cabinet_body"))
        assertTrue("Must include plinth base", partNames.contains("plinth"))
        assertTrue("Must include electrical enclosure door panel", partNames.contains("door_panel"))
        assertTrue("Must include high voltage hazard placard", partNames.contains("hazard_placard"))
        assertTrue("Must include rotary isolator switch handle", partNames.contains("isolator_handle"))
        assertTrue("Must include overhead conduit pipes", partNames.contains("conduit_pipe_1"))
        assertTrue("Must include electrical arc damage origin", partNames.contains("charred_area"))
        assertTrue("Must include ground scorch burn decal", partNames.contains("scorch_decal"))
    }

    @Test
    fun testFireAlarmStationAndExitSignScale() {
        val alarm = IndustrialMeshes.fireAlarm()
        assertEquals("Wall fire alarm station height must be ~0.18m", 0.18f, alarm.height, 0.02f)

        val exitSign = IndustrialMeshes.exitSign()
        assertEquals("Industrial emergency exit sign height must be ~0.16m", 0.16f, exitSign.height, 0.02f)
    }
}
