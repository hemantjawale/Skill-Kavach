package com.example.skilkavach.ar

/**
 * Grid-based Cellular Automaton Hazard Diffusion Model.
 * Simulates fire, smoke, and gas propagation across scanned 3D room coordinates
 * accounting for solid obstacles (equipment/walls) and ventilation direction.
 */
class HazardDiffusionSimulator(
    val width: Int = 20,
    val height: Int = 20,
    val diffusionRate: Float = 0.25f,
    val dissipationRate: Float = 0.02f
) {
    private var currentGrid = Array(width) { FloatArray(height) }
    private var nextGrid = Array(width) { FloatArray(height) }
    private var obstacles = Array(width) { BooleanArray(height) }

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

    fun getConcentration(x: Int, y: Int): Float {
        return if (x in 0 until width && y in 0 until height) currentGrid[x][y] else 0f
    }

    fun getGrid(): Array<FloatArray> = currentGrid

    fun clear() {
        for (x in 0 until width) {
            for (y in 0 until height) {
                currentGrid[x][y] = 0f
                nextGrid[x][y] = 0f
            }
        }
    }
}
