package com.example.skilkavach

import com.example.skilkavach.ar.FireSimulationAction
import com.example.skilkavach.ar.FireSimulationState
import com.example.skilkavach.ar.FireStateMachine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Fire Safety AR Simulation State Machine & PASS Protocol Execution.
 *
 * Verifies:
 * - State machine transitions (IDLE -> IGNITED -> IDENTIFY -> INSPECT -> PIN_REMOVED -> AIMING -> DISCHARGING -> SWEEPING -> SUPPRESSED -> EXTINGUISHED).
 * - Out-of-order action prevention (e.g., squeezing handle before pulling pin fails closed).
 * - Incorrect aiming penalty (aiming at flame top instead of base).
 * - Fire intensity decrease during sweeping.
 * - Score calculation and simulation reset capability.
 */
class FireStateMachineTest {

    private lateinit var stateMachine: FireStateMachine

    @Before
    fun setUp() {
        stateMachine = FireStateMachine()
    }

    @Test
    fun testInitialState() {
        assertEquals(FireSimulationState.IDLE, stateMachine.currentState)
        assertEquals(1.0f, stateMachine.fireIntensity, 0.001f)
        assertFalse(stateMachine.isPinPulled)
        assertFalse(stateMachine.isAimingAtBase)
        assertFalse(stateMachine.isDischarging)
        assertEquals(100, stateMachine.score)
    }

    @Test
    fun testCorrectPassSequenceExecution() {
        // 1. Ignite Fire
        assertTrue(stateMachine.processAction(FireSimulationAction.IGNITE_FIRE))
        assertEquals(FireSimulationState.IGNITED, stateMachine.currentState)

        // 2. Select Extinguisher
        assertTrue(stateMachine.processAction(FireSimulationAction.SELECT_EXTINGUISHER))
        assertEquals(FireSimulationState.IDENTIFY, stateMachine.currentState)

        // 3. Inspect Extinguisher
        assertTrue(stateMachine.processAction(FireSimulationAction.INSPECT_EXTINGUISHER))
        assertEquals(FireSimulationState.INSPECT, stateMachine.currentState)

        // 4. Pull Safety Pin
        assertTrue(stateMachine.processAction(FireSimulationAction.PULL_SAFETY_PIN))
        assertEquals(FireSimulationState.PIN_REMOVED, stateMachine.currentState)
        assertTrue(stateMachine.isPinPulled)

        // 5. Aim at Base of Fire
        assertTrue(stateMachine.processAction(FireSimulationAction.AIM_NOZZLE_BASE))
        assertEquals(FireSimulationState.AIMING, stateMachine.currentState)
        assertTrue(stateMachine.isAimingAtBase)

        // 6. Squeeze Handle
        assertTrue(stateMachine.processAction(FireSimulationAction.SQUEEZE_HANDLE))
        assertEquals(FireSimulationState.DISCHARGING, stateMachine.currentState)
        assertTrue(stateMachine.isDischarging)

        // 7. Sweep Left-Right
        assertTrue(stateMachine.processAction(FireSimulationAction.SWEEP_LEFT_RIGHT))
        assertEquals(FireSimulationState.SWEEPING, stateMachine.currentState)
        assertTrue(stateMachine.fireIntensity < 1.0f)

        // Sweep until suppressed
        stateMachine.processAction(FireSimulationAction.SWEEP_LEFT_RIGHT)
        stateMachine.processAction(FireSimulationAction.SWEEP_LEFT_RIGHT)
        stateMachine.processAction(FireSimulationAction.SWEEP_LEFT_RIGHT)
        assertEquals(FireSimulationState.SUPPRESSED, stateMachine.currentState)

        // 8. Confirm Extinguished State
        assertTrue(stateMachine.processAction(FireSimulationAction.COMPLETE_VERIFICATION))
        assertEquals(FireSimulationState.EXTINGUISHED, stateMachine.currentState)
        assertEquals(0.0f, stateMachine.fireIntensity, 0.001f)
        assertEquals(100, stateMachine.score)
    }

    @Test
    fun testPrematureHandleSqueezeFails() {
        stateMachine.processAction(FireSimulationAction.IGNITE_FIRE)
        stateMachine.processAction(FireSimulationAction.SELECT_EXTINGUISHER)

        // Try squeezing handle without pulling pin
        assertFalse(stateMachine.processAction(FireSimulationAction.SQUEEZE_HANDLE))
        assertEquals(FireSimulationState.FAILED, stateMachine.currentState)
        assertNotNull(stateMachine.lastErrorMessage)
        assertTrue(stateMachine.score < 100)
    }

    @Test
    fun testIncorrectAimingAtFlameTopPenalizesScore() {
        stateMachine.processAction(FireSimulationAction.IGNITE_FIRE)
        stateMachine.processAction(FireSimulationAction.PULL_SAFETY_PIN)

        // Aim at flame top instead of base
        assertFalse(stateMachine.processAction(FireSimulationAction.AIM_NOZZLE_FLAME_TOP))
        assertFalse(stateMachine.isAimingAtBase)
        assertTrue(stateMachine.score < 100)
        assertEquals("Aim at the BASE of the fire, not the top of the flames!", stateMachine.lastErrorMessage)
    }

    @Test
    fun testResetSimulationRestoresInitialState() {
        stateMachine.processAction(FireSimulationAction.IGNITE_FIRE)
        stateMachine.processAction(FireSimulationAction.PULL_SAFETY_PIN)
        stateMachine.processAction(FireSimulationAction.AIM_NOZZLE_BASE)
        stateMachine.processAction(FireSimulationAction.SQUEEZE_HANDLE)

        assertTrue(stateMachine.processAction(FireSimulationAction.RESET_SIMULATION))
        assertEquals(FireSimulationState.IDLE, stateMachine.currentState)
        assertEquals(1.0f, stateMachine.fireIntensity, 0.001f)
        assertFalse(stateMachine.isPinPulled)
        assertFalse(stateMachine.isDischarging)
        assertEquals(100, stateMachine.score)
    }
}
