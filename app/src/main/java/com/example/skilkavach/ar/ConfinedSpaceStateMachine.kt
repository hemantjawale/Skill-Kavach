package com.example.skilkavach.ar

/**
 * Industrial Confined Space Entry & Gas Safety Protocol State Machine.
 *
 * Teaches the 13-step OSHA / DGMS industrial entry procedure:
 * 1. Identify confined space (manhole/tank opening)
 * 2. Check entry permit on permit board
 * 3. Barricade area with exclusion barriers
 * 4. Wear required PPE (respirator / SCBA)
 * 5. Inspect safety harness
 * 6. Set rescue tripod & winch
 * 7. Start forced-air ventilation blower
 * 8. Test atmosphere at TOP (collar rim)
 * 9. Test atmosphere at MIDDLE depth
 * 10. Test atmosphere at BOTTOM (sump level for heavy H2S pooling)
 * 11. Confirm all gas readings are within safe limits
 * 12. Station standby attendant outside opening
 * 13. Enter confined space safely
 */
enum class ConfinedSpaceState {
    IDLE,
    SPACE_IDENTIFIED,
    PERMIT_CHECKED,
    BARRICADED,
    PPE_EQUIPPED,
    HARNESS_INSPECTED,
    TRIPOD_SET,
    VENTILATING,
    ATMOSPHERE_TESTED_TOP,
    ATMOSPHERE_TESTED_MID,
    ATMOSPHERE_TESTED_BOTTOM,
    READINGS_CONFIRMED,
    ATTENDANT_STATIONED,
    ENTRY_PERMITTED,
    DANGER_WARNING,
    FAILED
}

enum class ConfinedSpaceAction {
    IDENTIFY_SPACE,
    CHECK_PERMIT,
    SET_BARRICADE,
    EQUIP_PPE,
    INSPECT_HARNESS,
    SET_TRIPOD,
    START_VENTILATION,
    TEST_ATMOSPHERE_TOP,
    TEST_ATMOSPHERE_MID,
    TEST_ATMOSPHERE_BOTTOM,
    CONFIRM_SAFE_ATMOSPHERE,
    STATION_ATTENDANT,
    ENTER_SPACE,
    RESET_SIMULATION
}

class ConfinedSpaceStateMachine {

    var currentState: ConfinedSpaceState = ConfinedSpaceState.IDLE
        private set

    var isPermitChecked: Boolean = false
        private set

    var isBarricaded: Boolean = false
        private set

    var isPpeEquipped: Boolean = false
        private set

    var isHarnessInspected: Boolean = false
        private set

    var isTripodSet: Boolean = false
        private set

    var isVentilated: Boolean = false
        private set

    var testedTop: Boolean = false
        private set

    var testedMid: Boolean = false
        private set

    var testedBottom: Boolean = false
        private set

    var isAttendantStationed: Boolean = false
        private set

    var score: Int = 100
        private set

    var lastFeedbackMessage: String? = null
        private set

    fun processAction(action: ConfinedSpaceAction): Boolean {
        when (action) {
            ConfinedSpaceAction.IDENTIFY_SPACE -> {
                if (currentState == ConfinedSpaceState.IDLE || currentState == ConfinedSpaceState.FAILED) {
                    currentState = ConfinedSpaceState.SPACE_IDENTIFIED
                    lastFeedbackMessage = "Confined space entry hazard identified."
                    return true
                }
            }

            ConfinedSpaceAction.CHECK_PERMIT -> {
                if (currentState == ConfinedSpaceState.SPACE_IDENTIFIED || currentState == ConfinedSpaceState.IDLE) {
                    isPermitChecked = true
                    currentState = ConfinedSpaceState.PERMIT_CHECKED
                    lastFeedbackMessage = "Work permit checked and verified."
                    return true
                }
            }

            ConfinedSpaceAction.SET_BARRICADE -> {
                if (currentState == ConfinedSpaceState.PERMIT_CHECKED || currentState == ConfinedSpaceState.SPACE_IDENTIFIED) {
                    isBarricaded = true
                    currentState = ConfinedSpaceState.BARRICADED
                    lastFeedbackMessage = "Perimeter barricaded. Unauthorized entry prohibited."
                    return true
                }
            }

            ConfinedSpaceAction.EQUIP_PPE -> {
                if (currentState == ConfinedSpaceState.BARRICADED || currentState == ConfinedSpaceState.PERMIT_CHECKED) {
                    isPpeEquipped = true
                    currentState = ConfinedSpaceState.PPE_EQUIPPED
                    lastFeedbackMessage = "PPE and gas monitor equipped."
                    return true
                }
            }

            ConfinedSpaceAction.INSPECT_HARNESS -> {
                if (currentState == ConfinedSpaceState.PPE_EQUIPPED || currentState == ConfinedSpaceState.BARRICADED) {
                    isHarnessInspected = true
                    currentState = ConfinedSpaceState.HARNESS_INSPECTED
                    lastFeedbackMessage = "Full body harness and lifeline inspected."
                    return true
                }
            }

            ConfinedSpaceAction.SET_TRIPOD -> {
                if (currentState == ConfinedSpaceState.HARNESS_INSPECTED || currentState == ConfinedSpaceState.PPE_EQUIPPED) {
                    isTripodSet = true
                    currentState = ConfinedSpaceState.TRIPOD_SET
                    lastFeedbackMessage = "Rescue tripod and mechanical retrieval winch positioned."
                    return true
                }
            }

            ConfinedSpaceAction.START_VENTILATION -> {
                if (currentState == ConfinedSpaceState.TRIPOD_SET || currentState == ConfinedSpaceState.HARNESS_INSPECTED) {
                    isVentilated = true
                    currentState = ConfinedSpaceState.VENTILATING
                    lastFeedbackMessage = "Forced-air ventilation blower activated."
                    return true
                }
            }

            ConfinedSpaceAction.TEST_ATMOSPHERE_TOP -> {
                if (currentState == ConfinedSpaceState.VENTILATING || currentState == ConfinedSpaceState.TRIPOD_SET) {
                    testedTop = true
                    currentState = ConfinedSpaceState.ATMOSPHERE_TESTED_TOP
                    lastFeedbackMessage = "Sampled TOP atmosphere at rim level."
                    return true
                }
            }

            ConfinedSpaceAction.TEST_ATMOSPHERE_MID -> {
                if (testedTop || currentState == ConfinedSpaceState.ATMOSPHERE_TESTED_TOP) {
                    testedMid = true
                    currentState = ConfinedSpaceState.ATMOSPHERE_TESTED_MID
                    lastFeedbackMessage = "Sampled MIDDLE atmosphere at mid-depth."
                    return true
                } else if (!testedTop) {
                    score = (score - 10).coerceAtLeast(0)
                    lastFeedbackMessage = "WARNING: Must sample TOP level before lower levels!"
                    return false
                }
            }

            ConfinedSpaceAction.TEST_ATMOSPHERE_BOTTOM -> {
                if (testedMid || currentState == ConfinedSpaceState.ATMOSPHERE_TESTED_MID) {
                    testedBottom = true
                    currentState = ConfinedSpaceState.ATMOSPHERE_TESTED_BOTTOM
                    lastFeedbackMessage = "Sampled BOTTOM atmosphere in sump pit (H2S pooling zone)."
                    return true
                } else if (!testedMid) {
                    score = (score - 15).coerceAtLeast(0)
                    lastFeedbackMessage = "CRITICAL WARNING: Stratified sampling requires testing TOP, MID, then BOTTOM!"
                    return false
                }
            }

            ConfinedSpaceAction.CONFIRM_SAFE_ATMOSPHERE -> {
                if (testedTop && testedMid && testedBottom) {
                    currentState = ConfinedSpaceState.READINGS_CONFIRMED
                    lastFeedbackMessage = "All 3-level atmospheric readings verified within safe limits."
                    return true
                } else {
                    score = (score - 25).coerceAtLeast(0)
                    currentState = ConfinedSpaceState.DANGER_WARNING
                    lastFeedbackMessage = "DANGER: Cannot confirm atmosphere without testing all 3 depths (Top, Mid, Bottom)!"
                    return false
                }
            }

            ConfinedSpaceAction.STATION_ATTENDANT -> {
                if (currentState == ConfinedSpaceState.READINGS_CONFIRMED) {
                    isAttendantStationed = true
                    currentState = ConfinedSpaceState.ATTENDANT_STATIONED
                    lastFeedbackMessage = "Standby attendant stationed outside entry opening."
                    return true
                }
            }

            ConfinedSpaceAction.ENTER_SPACE -> {
                if (currentState == ConfinedSpaceState.ATTENDANT_STATIONED && isVentilated && testedBottom) {
                    currentState = ConfinedSpaceState.ENTRY_PERMITTED
                    lastFeedbackMessage = "Safe confined space entry completed."
                    return true
                } else {
                    currentState = ConfinedSpaceState.FAILED
                    score = (score - 35).coerceAtLeast(0)
                    lastFeedbackMessage = "FATAL ERROR: Attempted entry without completing mandatory safety & gas testing steps!"
                    return false
                }
            }

            ConfinedSpaceAction.RESET_SIMULATION -> {
                currentState = ConfinedSpaceState.IDLE
                isPermitChecked = false
                isBarricaded = false
                isPpeEquipped = false
                isHarnessInspected = false
                isTripodSet = false
                isVentilated = false
                testedTop = false
                testedMid = false
                testedBottom = false
                isAttendantStationed = false
                score = 100
                lastFeedbackMessage = null
                return true
            }
        }
        return false
    }
}
