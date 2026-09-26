package com.example.skilkavach.ar

import kotlin.math.*

/**
 * Physically inspired Hazard Diffusion Simulator for industrial AR training.
 *
 * Simulates atmospheric gas dispersion and pooling in and around confined spaces.
 * Models multi-gas behavior:
 * - H2S (Hydrogen Sulfide): Heavier than air (vapor density 1.19), accumulates at bottom of pits/manholes, highly toxic.
 * - O2 (Oxygen): Displaced by heavier or combustible gases, leading to asphyxiation hazard.
 * - LEL (Combustible Gases, e.g. Methane): Flammable hazard, stratification depends on vapor density.
 * - CO (Carbon Monoxide): Near air density (0.97), mixes uniformly.
 *
 * NOTE: This is an educational training simulation designed to teach confined space
 * hazards, stratification, and atmospheric testing protocols. It does not replace
 * certified gas dispersion models or real atmospheric testing.
 */
class HazardDiffusionSimulator(
    val width: Int = 20,
    val height: Int = 20,
    val diffusionRate: Float = 0.25f,
    val dissipationRate: Float = 0.02f
) {
    // ── 2D Cellular Automaton Grid (Backwards compatible) ────────────
    private var currentGrid = Array(width) { FloatArray(height) }
    private var nextGrid = Array(width) { FloatArray(height) }
    private var obstacles = Array(width) { BooleanArray(height) }

    // ── 3D Confined Space Hazard Model ──────────────────────────────
    var sourceX: Float = 0.0f
    var sourceY: Float = -0.5f  // Below ground level inside hatch
    var sourceZ: Float = -1.2f
    var leakRate: Float = 0.35f
    var airflowVelocityX: Float = 0.15f  // Ambient drift (wind)
    var airflowVelocityZ: Float = 0.05f
    var elapsedTimeSeconds: Float = 0f
    var isVentilated: Boolean = false

    // Stratified sampling locations relative to confined space opening
    enum class SamplingLevel {
        TOP,      // At collar rim (z=0, y=0.0m) — lighter gases / initial egress
        MIDDLE,   // Inside collar (y=-0.35m) — transitional zone
        BOTTOM    // Bottom of sump/pit (y=-0.90m) — heavier-than-air toxic pooling (H2S)
    }

    enum class HazardZone(val displayName: String, val description: String) {
        ZONE_A_CRITICAL("Zone A — Critical", "Immediate Danger to Life & Health (IDLH). Entry strictly prohibited."),
        ZONE_B_HIGH("Zone B — High Hazard", "Exceeds Permissible Exposure Limit. Mandatory evacuation."),
        ZONE_C_MODERATE("Zone C — Moderate Warning", "Approaching Action Level. Restricted perimeter."),
        ZONE_D_SAFE("Zone D — Safe Perimeter", "Atmosphere within normal baseline. Safe watch area.")
    }

    data class GasReadings(
        val oxygenPercent: Float,     // Normal: 20.9%, Deficient: <19.5%, Enriched: >23.5%
        val lelPercent: Float,        // Lower Explosive Limit %: Safe <10%, Alarm >=10%
        val h2sPpm: Float,            // Hydrogen Sulfide: TWA 10 ppm, IDLH 100 ppm
        val coPpm: Float,             // Carbon Monoxide: TWA 35 ppm, IDLH 1200 ppm
        val zone: HazardZone,
        val isAlarm: Boolean,
        val isWarning: Boolean,
        val statusMessage: String
    )

    fun setObstacle(x: Int, y: Int, isObstacle: Boolean) {
        if (x in 0 until width && y in 0 until height) {
            obstacles[x][y] = isObstacle
        }
    }

    fun injectHazard(x: Int, y: Int, amount: Float) {
        if (x in 0 until width && y in 0 until height && !obstacles[x][y]) {
            currentGrid[x][y] = (currentGrid[x][y] + amount).coerceAtMost(1.0f)
        }
    }

    /**
     * Executes one cellular automaton timestep:
     * C_next(x,y) = C(x,y) + D * sum_neighbors(C_neighbor - C(x,y)) - Dissipation
     */
    fun step(windX: Float = 0.1f, windY: Float = 0f) {
        elapsedTimeSeconds += 0.05f

        for (x in 0 until width) {
            for (y in 0 until height) {
                if (obstacles[x][y]) {
                    nextGrid[x][y] = 0f
                    continue
                }

                var neighborSum = 0f
                var validNeighbors = 0

                val dxs = intArrayOf(-1, 1, 0, 0, -1, -1, 1, 1)
                val dys = intArrayOf(0, 0, -1, 1, -1, 1, -1, 1)

                for (i in 0..7) {
                    val nx = x + dxs[i]
                    val ny = y + dys[i]

                    if (nx in 0 until width && ny in 0 until height && !obstacles[nx][ny]) {
                        neighborSum += currentGrid[nx][ny]
                        validNeighbors++
                    }
                }

                val avgNeighbor = if (validNeighbors > 0) neighborSum / validNeighbors else 0f
                val delta = (avgNeighbor - currentGrid[x][y]) * diffusionRate

                // Apply wind drift bias
                val windDrift = if (windX > 0 && x > 0 && !obstacles[x - 1][y]) {
                    currentGrid[x - 1][y] * windX * 0.1f
                } else 0f

                val updatedConcentration = currentGrid[x][y] + delta + windDrift - dissipationRate
                nextGrid[x][y] = updatedConcentration.coerceIn(0f, 1.0f)
            }
        }

        // Swap buffers
        val temp = currentGrid
        currentGrid = nextGrid
        nextGrid = temp
    }

    // ── 3D Physically-Inspired Concentration Calculation ─────────────

    /**
     * Calculates simulated gas concentration (0.0 to 1.0) at any 3D spatial position (x, y, z).
     * Takes into account:
     * - Distance from confined space source
     * - Vertical stratification: H2S pooling in negative Y (inside pit)
     * - Ambient wind drift along X/Z
     * - Active mechanical ventilation dissipation
     */
    fun getConcentrationAt(x: Float, y: Float, z: Float): Float {
        val dx = x - sourceX
        val dy = y - sourceY
        val dz = z - sourceZ

        // Apply wind offset to virtual center
        val windDriftX = airflowVelocityX * (elapsedTimeSeconds * 0.05f).coerceAtMost(0.4f)
        val windDriftZ = airflowVelocityZ * (elapsedTimeSeconds * 0.05f).coerceAtMost(0.2f)

        val horizontalDist = sqrt((dx - windDriftX).pow(2) + (dz - windDriftZ).pow(2))

        // Vertical density gradient: heavier-than-air gas accumulates at low heights (y <= 0)
        val depthFactor = if (y < 0f) {
            1.0f + abs(y) * 1.5f  // Higher concentration deeper in the sump
        } else {
            exp(-y * 2.5f)        // Rapid attenuation above ground
        }

        // Gaussian dispersion profile
        val sigma = 0.45f + (if (isVentilated) 0.6f else 0.2f)
        val dispersion = exp(-(horizontalDist * horizontalDist) / (2f * sigma * sigma))

        val ventilationAttenuation = if (isVentilated) 0.25f else 1.0f
        val rawConcentration = dispersion * depthFactor * leakRate * ventilationAttenuation

        return rawConcentration.coerceIn(0f, 1.0f)
    }

    /**
     * Returns the simulated multi-gas readings at a given 3D position or sampling level.
     * Generates realistic, physically consistent values for:
     * - Oxygen (% Vol)
     * - Combustible gas (% LEL)
     * - Hydrogen Sulfide (ppm)
     * - Carbon Monoxide (ppm)
     */
    fun getSimulatedReadings(x: Float, y: Float, z: Float): GasReadings {
        val concentration = getConcentrationAt(x, y, z)

        // Oxygen is displaced by the expanding gas plume
        val o2 = (20.9f - concentration * 3.8f).coerceIn(16.0f, 20.9f)

        // LEL reaches alarming levels in high-concentration pocket
        val lel = (concentration * 32.0f).coerceIn(0f, 100f)

        // H2S (Hydrogen Sulfide): 0 to ~35 ppm in simulated leak
        val h2s = (concentration * 36.0f).coerceIn(0f, 100f)

        // Carbon monoxide: 0 to ~30 ppm
        val co = (concentration * 28.0f).coerceIn(0f, 150f)

        // Classify zone
        val zone = when {
            concentration >= 0.60f || h2s >= 15f || o2 < 19.5f -> HazardZone.ZONE_A_CRITICAL
            concentration >= 0.35f || h2s >= 10f || lel >= 10f -> HazardZone.ZONE_B_HIGH
            concentration >= 0.15f || h2s >= 5f                 -> HazardZone.ZONE_C_MODERATE
            else                                                -> HazardZone.ZONE_D_SAFE
        }

        val isAlarm = zone == HazardZone.ZONE_A_CRITICAL || zone == HazardZone.ZONE_B_HIGH
        val isWarning = zone == HazardZone.ZONE_C_MODERATE

        val status = when (zone) {
            HazardZone.ZONE_A_CRITICAL -> "CRITICAL ALARM: H2S EXCEEDED / O2 DEFICIENT — DO NOT ENTER!"
            HazardZone.ZONE_B_HIGH -> "HIGH ALARM: HAZARDOUS CONCENTRATION — WITHDRAW IMMEDIATELY"
            HazardZone.ZONE_C_MODERATE -> "WARNING: GAS DETECTED ABOVE BASELINE — MONITOR CLOSELY"
            HazardZone.ZONE_D_SAFE -> "ATMOSPHERE NORMAL — SAFE TESTING BASELINE"
        }

        return GasReadings(
            oxygenPercent = (round(o2 * 10f) / 10f),
            lelPercent = (round(lel * 10f) / 10f),
            h2sPpm = (round(h2s * 10f) / 10f),
            coPpm = (round(co * 10f) / 10f),
            zone = zone,
            isAlarm = isAlarm,
            isWarning = isWarning,
            statusMessage = status
        )
    }

    /**
     * Stratified atmospheric testing at specific standardized sample depths:
     * - TOP: Rim level (y = 0.0m)
     * - MIDDLE: Mid-depth (y = -0.40m)
     * - BOTTOM: Sump base (y = -0.85m) — critical for heavier-than-air H2S
     */
    fun getStratifiedSample(level: SamplingLevel): GasReadings {
        val sampleY = when (level) {
            SamplingLevel.TOP -> 0.05f
            SamplingLevel.MIDDLE -> -0.35f
            SamplingLevel.BOTTOM -> -0.85f
        }
        return getSimulatedReadings(sourceX, sampleY, sourceZ)
    }

    fun getConcentration(x: Int, y: Int): Float {
        return if (x in 0 until width && y in 0 until height) currentGrid[x][y] else 0f
    }

    fun getGrid(): Array<FloatArray> = currentGrid

    fun clear() {
        elapsedTimeSeconds = 0f
        for (x in 0 until width) {
            for (y in 0 until height) {
                currentGrid[x][y] = 0f
                nextGrid[x][y] = 0f
            }
        }
    }
}

