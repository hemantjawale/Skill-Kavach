package com.example.skilkavach

import com.example.skilkavach.ar.ConfinedSpaceAction
import com.example.skilkavach.ar.ConfinedSpaceState
import com.example.skilkavach.ar.ConfinedSpaceStateMachine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Confined Space & Gas Safety Protocol State Machine.
 *
 * Verifies:
 * - 13-step OSHA/DGMS entry procedure sequence.
 * - Out-of-order testing prevention (sampling bottom before top/mid fails closed).
 * - Premature entry prevention (attempting entry without testing atmosphere or stationing attendant fails closed).
 * - Score calculation and simulation reset capability.
 */
class ConfinedSpaceStateMachineTest {

    private lateinit var stateMachine: ConfinedSpaceStateMachine

    @Before
    fun setUp() {
        stateMachine = ConfinedSpaceStateMachine()
    }

    @Test
    fun testInitialState() {
        assertEquals(ConfinedSpaceState.IDLE, stateMachine.currentState)
        assertFalse(stateMachine.isPermitChecked)
        assertFalse(stateMachine.isBarricaded)
        assertFalse(stateMachine.isPpeEquipped)
        assertFalse(stateMachine.isVentilated)
        assertFalse(stateMachine.testedTop)
        assertFalse(stateMachine.testedMid)
        assertFalse(stateMachine.testedBottom)
        assertEquals(100, stateMachine.score)
    }

    @Test
    fun testCompleteValidEntrySequence() {
        // 1. Identify space
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.IDENTIFY_SPACE))
        assertEquals(ConfinedSpaceState.SPACE_IDENTIFIED, stateMachine.currentState)

        // 2. Check permit
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.CHECK_PERMIT))
        assertEquals(ConfinedSpaceState.PERMIT_CHECKED, stateMachine.currentState)

        // 3. Barricade area
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.SET_BARRICADE))
        assertEquals(ConfinedSpaceState.BARRICADED, stateMachine.currentState)

        // 4. Wear PPE
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.EQUIP_PPE))
        assertEquals(ConfinedSpaceState.PPE_EQUIPPED, stateMachine.currentState)

        // 5. Inspect harness
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.INSPECT_HARNESS))
        assertEquals(ConfinedSpaceState.HARNESS_INSPECTED, stateMachine.currentState)

        // 6. Set tripod
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.SET_TRIPOD))
        assertEquals(ConfinedSpaceState.TRIPOD_SET, stateMachine.currentState)

        // 7. Start ventilation
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.START_VENTILATION))
        assertEquals(ConfinedSpaceState.VENTILATING, stateMachine.currentState)

        // 8. Test atmosphere TOP
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.TEST_ATMOSPHERE_TOP))
        assertEquals(ConfinedSpaceState.ATMOSPHERE_TESTED_TOP, stateMachine.currentState)

        // 9. Test atmosphere MID
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.TEST_ATMOSPHERE_MID))
        assertEquals(ConfinedSpaceState.ATMOSPHERE_TESTED_MID, stateMachine.currentState)

        // 10. Test atmosphere BOTTOM
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.TEST_ATMOSPHERE_BOTTOM))
        assertEquals(ConfinedSpaceState.ATMOSPHERE_TESTED_BOTTOM, stateMachine.currentState)

        // 11. Confirm safe readings
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.CONFIRM_SAFE_ATMOSPHERE))
        assertEquals(ConfinedSpaceState.READINGS_CONFIRMED, stateMachine.currentState)

        // 12. Station attendant
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.STATION_ATTENDANT))
        assertEquals(ConfinedSpaceState.ATTENDANT_STATIONED, stateMachine.currentState)

        // 13. Enter space safely
        assertTrue(stateMachine.processAction(ConfinedSpaceAction.ENTER_SPACE))
        assertEquals(ConfinedSpaceState.ENTRY_PERMITTED, stateMachine.currentState)
        assertEquals(100, stateMachine.score)
    }

    @Test
    fun testPrematureEntryFailsWithPenalty() {
        stateMachine.processAction(ConfinedSpaceAction.IDENTIFY_SPACE)

        // Attempting to enter space immediately without testing atmosphere or ventilation
        assertFalse(stateMachine.processAction(ConfinedSpaceAction.ENTER_SPACE))
        assertEquals(ConfinedSpaceState.FAILED, stateMachine.currentState)
        assertNotNull(stateMachine.lastFeedbackMessage)
        assertTrue(stateMachine.score < 100)
    }

    @Test
    fun testUnstratifiedSamplingFails() {
        stateMachine.processAction(ConfinedSpaceAction.IDENTIFY_SPACE)
        stateMachine.processAction(ConfinedSpaceAction.CHECK_PERMIT)
        stateMachine.processAction(ConfinedSpaceAction.SET_BARRICADE)
        stateMachine.processAction(ConfinedSpaceAction.EQUIP_PPE)
        stateMachine.processAction(ConfinedSpaceAction.INSPECT_HARNESS)
        stateMachine.processAction(ConfinedSpaceAction.SET_TRIPOD)
        stateMachine.processAction(ConfinedSpaceAction.START_VENTILATION)

        // Try sampling bottom level without sampling top level first
        assertFalse(stateMachine.processAction(ConfinedSpaceAction.TEST_ATMOSPHERE_BOTTOM))
        assertTrue(stateMachine.score < 100)
    }

    @Test
    fun testResetSimulationRestoresInitialState() {
        stateMachine.processAction(ConfinedSpaceAction.IDENTIFY_SPACE)
        stateMachine.processAction(ConfinedSpaceAction.CHECK_PERMIT)
        stateMachine.processAction(ConfinedSpaceAction.SET_BARRICADE)

        assertTrue(stateMachine.processAction(ConfinedSpaceAction.RESET_SIMULATION))
        assertEquals(ConfinedSpaceState.IDLE, stateMachine.currentState)
        assertFalse(stateMachine.isPermitChecked)
        assertFalse(stateMachine.isBarricaded)
        assertEquals(100, stateMachine.score)
    }
}
