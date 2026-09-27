package com.example.skilkavach.ar

/**
 * Industrial Fire Safety Simulation State Machine (PASS Protocol).
 *
 * States:
 * IDLE → IGNITED → IDENTIFY → INSPECT → PIN_REMOVED → AIMING → DISCHARGING → SWEEPING → SUPPRESSED → EXTINGUISHED
 * FAILED (on invalid sequence / timeout / dangerous action)
 */
enum class FireSimulationState {
    IDLE,
    IGNITED,
    IDENTIFY,
    INSPECT,
    PIN_REMOVED,
    AIMING,
    DISCHARGING,
    SWEEPING,
    SUPPRESSED,
    EXTINGUISHED,
    FAILED
}

/**
 * User actions during the simulation.
 */
enum class FireSimulationAction {
    IGNITE_FIRE,
    SELECT_EXTINGUISHER,
    INSPECT_EXTINGUISHER,
    PULL_SAFETY_PIN,
    AIM_NOZZLE_BASE,
    AIM_NOZZLE_FLAME_TOP, // Incorrect action!
    SQUEEZE_HANDLE,
    SWEEP_LEFT_RIGHT,
    COMPLETE_VERIFICATION,
    RESET_SIMULATION
}

class FireStateMachine {

    var currentState: FireSimulationState = FireSimulationState.IDLE
        private set

    var fireIntensity: Float = 1.0f
        private set

    var isPinPulled: Boolean = false
        private set

    var isAimingAtBase: Boolean = false
        private set

    var isDischarging: Boolean = false
        private set

    var sweepProgress: Float = 0.0f
        private set

    var lastErrorMessage: String? = null
        private set

    var score: Int = 100
        private set

    fun processAction(action: FireSimulationAction): Boolean {
        when (action) {
            FireSimulationAction.IGNITE_FIRE -> {
                if (currentState == FireSimulationState.IDLE || currentState == FireSimulationState.FAILED) {
                    currentState = FireSimulationState.IGNITED
                    fireIntensity = 1.0f
                    isPinPulled = false
                    isAimingAtBase = false
                    isDischarging = false
                    sweepProgress = 0.0f
                    lastErrorMessage = null
                    score = 100
                    return true
                }
            }

            FireSimulationAction.SELECT_EXTINGUISHER -> {
                if (currentState == FireSimulationState.IGNITED || currentState == FireSimulationState.IDLE) {
                    currentState = FireSimulationState.IDENTIFY
                    return true
                }
            }

            FireSimulationAction.INSPECT_EXTINGUISHER -> {
                if (currentState == FireSimulationState.IDENTIFY || currentState == FireSimulationState.IGNITED) {
                    currentState = FireSimulationState.INSPECT
                    return true
                }
            }

            FireSimulationAction.PULL_SAFETY_PIN -> {
                if (currentState == FireSimulationState.INSPECT || currentState == FireSimulationState.IDENTIFY || currentState == FireSimulationState.IGNITED) {
                    isPinPulled = true
                    currentState = FireSimulationState.PIN_REMOVED
                    return true
                } else if (isPinPulled) {
                    return true
                }
            }

            FireSimulationAction.AIM_NOZZLE_BASE -> {
                if (currentState == FireSimulationState.PIN_REMOVED || currentState == FireSimulationState.AIMING) {
                    isAimingAtBase = true
                    currentState = FireSimulationState.AIMING
                    return true
                } else if (!isPinPulled) {
                    currentState = FireSimulationState.FAILED
                    lastErrorMessage = "Safety pin must be pulled before aiming and discharging!"
                    score = (score - 20).coerceAtLeast(0)
                    return false
                }
            }

            FireSimulationAction.AIM_NOZZLE_FLAME_TOP -> {
                score = (score - 15).coerceAtLeast(0)
                lastErrorMessage = "Aim at the BASE of the fire, not the top of the flames!"
                isAimingAtBase = false
                return false
            }

            FireSimulationAction.SQUEEZE_HANDLE -> {
                if (isPinPulled && (currentState == FireSimulationState.AIMING || currentState == FireSimulationState.DISCHARGING || currentState == FireSimulationState.PIN_REMOVED)) {
                    isDischarging = true
                    currentState = FireSimulationState.DISCHARGING
                    return true
                } else if (!isPinPulled) {
                    currentState = FireSimulationState.FAILED
                    lastErrorMessage = "Cannot squeeze handle: Safety pin is still locked!"
                    score = (score - 25).coerceAtLeast(0)
                    return false
                }
            }

            FireSimulationAction.SWEEP_LEFT_RIGHT -> {
                if (currentState == FireSimulationState.DISCHARGING || currentState == FireSimulationState.SWEEPING) {
                    if (isAimingAtBase && isDischarging) {
                        currentState = FireSimulationState.SWEEPING
                        sweepProgress = (sweepProgress + 0.25f).coerceAtMost(1.0f)
                        fireIntensity = (1.0f - sweepProgress).coerceAtLeast(0.0f)

                        if (sweepProgress >= 1.0f) {
                            currentState = FireSimulationState.SUPPRESSED
                        }
                        return true
                    } else if (!isAimingAtBase) {
                        score = (score - 10).coerceAtLeast(0)
                        lastErrorMessage = "Must aim at the base of the fire while sweeping!"
                        return false
                    }
                }
            }

            FireSimulationAction.COMPLETE_VERIFICATION -> {
                if (currentState == FireSimulationState.SUPPRESSED || sweepProgress >= 1.0f) {
                    currentState = FireSimulationState.EXTINGUISHED
                    fireIntensity = 0.0f
                    isDischarging = false
                    return true
                }
            }

            FireSimulationAction.RESET_SIMULATION -> {
                currentState = FireSimulationState.IDLE
                fireIntensity = 1.0f
                isPinPulled = false
                isAimingAtBase = false
                isDischarging = false
                sweepProgress = 0.0f
                lastErrorMessage = null
                score = 100
                return true
            }
        }
        return false
    }
}
