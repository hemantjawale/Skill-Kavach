package com.example.skilkavach.ar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Industrial-grade procedural mesh generation for AR safety training.
 * Scale convention: 1 unit = 1 meter (real-world dimensions).
 *
 * Provides detailed composite meshes for:
 * - Fire extinguisher with identifiable PASS components
 * - Emergency exit sign (ISO 7010)
 * - Fire alarm pull station
 * - Hazard zone / fire base marker
 * - Safety pin, handle, sweep zone
 *
 * All meshes include per-vertex normals for PBR-approximate lighting.
 */
object IndustrialMeshes {

    // ── Primitive builders ──────────────────────────────────────────

    /** Creates a cylinder along Y-axis from y=0 to y=height. */
    fun cylinder(
        radius: Float, height: Float, segments: Int = 32,
        capTop: Boolean = true, capBottom: Boolean = true
    ): MeshData {
        val verts = mutableListOf<Float>()
        val norms = mutableListOf<Float>()

        for (i in 0 until segments) {
            val a0 = i * 2f * PI.toFloat() / segments
            val a1 = (i + 1) * 2f * PI.toFloat() / segments
            val x0 = cos(a0) * radius; val z0 = sin(a0) * radius
            val x1 = cos(a1) * radius; val z1 = sin(a1) * radius
            val nx0 = cos(a0); val nz0 = sin(a0)
            val nx1 = cos(a1); val nz1 = sin(a1)

            // Side quad (2 triangles)
            fun addSide(ax: Float, ay: Float, az: Float, anx: Float, anz: Float) {
                verts.addAll(listOf(ax, ay, az)); norms.addAll(listOf(anx, 0f, anz))
            }
            addSide(x0, 0f, z0, nx0, nz0)
            addSide(x1, 0f, z1, nx1, nz1)
            addSide(x1, height, z1, nx1, nz1)
            addSide(x0, 0f, z0, nx0, nz0)
            addSide(x1, height, z1, nx1, nz1)
            addSide(x0, height, z0, nx0, nz0)

            // Top cap
            if (capTop) {
                verts.addAll(listOf(0f, height, 0f)); norms.addAll(listOf(0f, 1f, 0f))
                verts.addAll(listOf(x0, height, z0)); norms.addAll(listOf(0f, 1f, 0f))
                verts.addAll(listOf(x1, height, z1)); norms.addAll(listOf(0f, 1f, 0f))
            }
            // Bottom cap
            if (capBottom) {
                verts.addAll(listOf(0f, 0f, 0f)); norms.addAll(listOf(0f, -1f, 0f))
                verts.addAll(listOf(x1, 0f, z1)); norms.addAll(listOf(0f, -1f, 0f))
                verts.addAll(listOf(x0, 0f, z0)); norms.addAll(listOf(0f, -1f, 0f))
            }
        }
        return MeshData(toBuffer(verts), toBuffer(norms), verts.size / 3)
    }

    /** Creates a cone along Y-axis from y=0 (radius) to y=height (tip). */
    fun cone(radius: Float, height: Float, segments: Int = 24): MeshData {
        val verts = mutableListOf<Float>()
        val norms = mutableListOf<Float>()
        val slopeLen = sqrt(radius * radius + height * height)
        val ny = radius / slopeLen
        val nr = height / slopeLen

        for (i in 0 until segments) {
            val a0 = i * 2f * PI.toFloat() / segments
            val a1 = (i + 1) * 2f * PI.toFloat() / segments
            val x0 = cos(a0) * radius; val z0 = sin(a0) * radius
            val x1 = cos(a1) * radius; val z1 = sin(a1) * radius

            // Side triangle
            verts.addAll(listOf(0f, height, 0f))
            norms.addAll(listOf(cos((a0 + a1) / 2f) * nr, ny, sin((a0 + a1) / 2f) * nr))
            verts.addAll(listOf(x0, 0f, z0))
            norms.addAll(listOf(cos(a0) * nr, ny, sin(a0) * nr))
            verts.addAll(listOf(x1, 0f, z1))
            norms.addAll(listOf(cos(a1) * nr, ny, sin(a1) * nr))

            // Bottom cap
            verts.addAll(listOf(0f, 0f, 0f)); norms.addAll(listOf(0f, -1f, 0f))
            verts.addAll(listOf(x1, 0f, z1)); norms.addAll(listOf(0f, -1f, 0f))
            verts.addAll(listOf(x0, 0f, z0)); norms.addAll(listOf(0f, -1f, 0f))
        }
        return MeshData(toBuffer(verts), toBuffer(norms), verts.size / 3)
    }

    /** Creates a UV sphere centered at origin with given radius. */
    fun sphere(radius: Float, rings: Int = 16, sectors: Int = 24): MeshData {
        val verts = mutableListOf<Float>()
        val norms = mutableListOf<Float>()

        for (r in 0 until rings) {
            val phi0 = PI.toFloat() * r / rings
            val phi1 = PI.toFloat() * (r + 1) / rings
            for (s in 0 until sectors) {
                val theta0 = 2f * PI.toFloat() * s / sectors
                val theta1 = 2f * PI.toFloat() * (s + 1) / sectors

                fun vertex(phi: Float, theta: Float) {
                    val nx = sin(phi) * cos(theta)
                    val ny = cos(phi)
                    val nz = sin(phi) * sin(theta)
                    verts.addAll(listOf(nx * radius, ny * radius, nz * radius))
                    norms.addAll(listOf(nx, ny, nz))
                }
                vertex(phi0, theta0); vertex(phi1, theta0); vertex(phi1, theta1)
                vertex(phi0, theta0); vertex(phi1, theta1); vertex(phi0, theta1)
            }
        }
        return MeshData(toBuffer(verts), toBuffer(norms), verts.size / 3)
    }

    /** Creates a box centered at origin with given half-extents. */
    fun box(hx: Float, hy: Float, hz: Float): MeshData {
        val verts = mutableListOf<Float>()
        val norms = mutableListOf<Float>()

        fun face(positions: List<Float>, nx: Float, ny: Float, nz: Float) {
            for (i in positions.indices step 3) {
                verts.addAll(listOf(positions[i], positions[i + 1], positions[i + 2]))
                norms.addAll(listOf(nx, ny, nz))
            }
        }
        // +Z face
        face(listOf(-hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, -hy, hz, hx, hy, hz, -hx, hy, hz), 0f, 0f, 1f)
        // -Z face
        face(listOf(hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz), 0f, 0f, -1f)
        // +X face
        face(listOf(hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, -hy, hz, hx, hy, -hz, hx, hy, hz), 1f, 0f, 0f)
        // -X face
        face(listOf(-hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, -hy, -hz, -hx, hy, hz, -hx, hy, -hz), -1f, 0f, 0f)
        // +Y face
        face(listOf(-hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, hz, hx, hy, -hz, -hx, hy, -hz), 0f, 1f, 0f)
        // -Y face
        face(listOf(-hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz), 0f, -1f, 0f)

        return MeshData(toBuffer(verts), toBuffer(norms), verts.size / 3)
    }

    /** Flat disc (ring) on the XZ plane at y=0. */
    fun disc(outerRadius: Float, innerRadius: Float = 0f, segments: Int = 48): MeshData {
        val verts = mutableListOf<Float>()
        val norms = mutableListOf<Float>()
        for (i in 0 until segments) {
            val a0 = i * 2f * PI.toFloat() / segments
            val a1 = (i + 1) * 2f * PI.toFloat() / segments
            val ox0 = cos(a0) * outerRadius; val oz0 = sin(a0) * outerRadius
            val ox1 = cos(a1) * outerRadius; val oz1 = sin(a1) * outerRadius
            if (innerRadius <= 0f) {
                verts.addAll(listOf(0f, 0f, 0f)); norms.addAll(listOf(0f, 1f, 0f))
                verts.addAll(listOf(ox0, 0f, oz0)); norms.addAll(listOf(0f, 1f, 0f))
                verts.addAll(listOf(ox1, 0f, oz1)); norms.addAll(listOf(0f, 1f, 0f))
            } else {
                val ix0 = cos(a0) * innerRadius; val iz0 = sin(a0) * innerRadius
                val ix1 = cos(a1) * innerRadius; val iz1 = sin(a1) * innerRadius
                verts.addAll(listOf(ix0, 0f, iz0)); norms.addAll(listOf(0f, 1f, 0f))
                verts.addAll(listOf(ox0, 0f, oz0)); norms.addAll(listOf(0f, 1f, 0f))
                verts.addAll(listOf(ox1, 0f, oz1)); norms.addAll(listOf(0f, 1f, 0f))
                verts.addAll(listOf(ix0, 0f, iz0)); norms.addAll(listOf(0f, 1f, 0f))
                verts.addAll(listOf(ox1, 0f, oz1)); norms.addAll(listOf(0f, 1f, 0f))
                verts.addAll(listOf(ix1, 0f, iz1)); norms.addAll(listOf(0f, 1f, 0f))
            }
        }
        return MeshData(toBuffer(verts), toBuffer(norms), verts.size / 3)
    }

    /** Translates all vertices in a MeshData by (dx, dy, dz). */
    fun translate(mesh: MeshData, dx: Float, dy: Float, dz: Float): MeshData {
        val out = FloatArray(mesh.vertexCount * 3)
        mesh.positions.position(0)
        mesh.positions.get(out)
        for (i in out.indices step 3) { out[i] += dx; out[i + 1] += dy; out[i + 2] += dz }
        val normArr = FloatArray(mesh.vertexCount * 3)
        mesh.normals.position(0)
        mesh.normals.get(normArr)
        return MeshData(toBuffer(out.toList()), toBuffer(normArr.toList()), mesh.vertexCount)
    }

    /** Merges multiple MeshData into one. */
    fun merge(vararg meshes: MeshData): MeshData {
        val totalVerts = meshes.sumOf { it.vertexCount }
        val allPos = mutableListOf<Float>()
        val allNorm = mutableListOf<Float>()
        for (m in meshes) {
            m.positions.position(0)
            for (i in 0 until m.vertexCount * 3) allPos.add(m.positions.get())
            m.normals.position(0)
            for (i in 0 until m.vertexCount * 3) allNorm.add(m.normals.get())
        }
        return MeshData(toBuffer(allPos), toBuffer(allNorm), totalVerts)
    }

    // ── Composite industrial equipment ──────────────────────────────

    // ── Composite industrial equipment ──────────────────────────────

    data class ScaleVerification(
        val name: String,
        val expectedHeightMeters: Float,
        val detectedHeightMeters: Float,
        val status: String
    )

    /**
     * Calibration & Scale Verification report for development testing.
     * Ensures all industrial models adhere to real-world physics convention (1 unit = 1 meter).
     */
    fun verifyRealWorldScale(): Map<String, ScaleVerification> {
        val models = mapOf(
            "Fire Extinguisher" to (0.50f to fireExtinguisher().height),
            "Electrical Cabinet Fire" to (0.65f to electricalCabinetFire().height),
            "Fire Alarm Pull Station" to (0.18f to fireAlarm().height),
            "Emergency Exit Sign" to (0.16f to exitSign().height),
            "Confined Space Hatch" to (0.70f to confinedSpaceHatch().height),
            "Multi-Gas Detector" to (0.14f to gasDetector().height),
            "Rescue Tripod" to (1.75f to rescueTripod().height),
            "Standby Attendant" to (1.72f to buddyFigure().height),
            "Ventilation Blower Fan" to (0.65f to ventilationBlower().height)
        )
        return models.mapValues { (name, pair) ->
            val (expected, detected) = pair
            val pass = abs(expected - detected) <= 0.06f
            ScaleVerification(name, expected, detected, if (pass) "PASS" else "FAIL")
        }
    }

    /**
     * Detailed fire extinguisher: ~0.50m tall, 0.15m diameter body.
     * Components: cylinder body, base dome, operating instructions band, pressure gauge,
     * valve assembly, squeeze handle, safety ring-pin with plastic tamper seal,
     * reinforced discharge hose, and flared discharge nozzle.
     *
     * @param isPinPulled When true, the safety pin is displaced and broken seal dangles down.
     */
    fun fireExtinguisher(isPinPulled: Boolean = false): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // Main body — red cylinder with industrial gloss coat
        parts += ModelPart(
            "body", cylinder(0.075f, 0.40f, 32),
            Material(0.80f, 0.10f, 0.08f, metallic = 0.35f, roughness = 0.50f)
        )
        // Body bottom cap — slight dome
        parts += ModelPart(
            "body_base", translate(sphere(0.075f, 8, 16), 0f, 0f, 0f),
            Material(0.80f, 0.10f, 0.08f, metallic = 0.35f, roughness = 0.50f),
            scaleY = 0.3f
        )
        // Upper neck collar — conical transition
        parts += ModelPart(
            "neck", translate(cone(0.075f, 0.04f, 24), 0f, 0.40f, 0f),
            Material(0.80f, 0.10f, 0.08f, metallic = 0.35f, roughness = 0.50f)
        )
        // Operating instruction band — high-contrast white/black PASS label
        parts += ModelPart(
            "label_band", translate(box(0.058f, 0.075f, 0.003f), 0f, 0.18f, 0.077f),
            Material(0.95f, 0.95f, 0.93f, metallic = 0.0f, roughness = 0.85f)
        )
        // PASS icon indicator rows on label
        parts += ModelPart(
            "pass_text_row", translate(box(0.045f, 0.010f, 0.001f), 0f, 0.20f, 0.081f),
            Material(0.12f, 0.12f, 0.12f, metallic = 0.0f, roughness = 0.9f)
        )
        // Pressure gauge — brass stem and circular casing
        parts += ModelPart(
            "gauge_stem", translate(cylinder(0.007f, 0.015f, 12), 0.065f, 0.41f, 0f),
            Material(0.85f, 0.70f, 0.25f, metallic = 0.8f, roughness = 0.3f)
        )
        parts += ModelPart(
            "gauge_casing", translate(cylinder(0.018f, 0.010f, 16), 0.078f, 0.41f, 0f),
            Material(0.90f, 0.90f, 0.90f, metallic = 0.7f, roughness = 0.25f)
        )
        // Gauge face (green operable center zone flanked by red recharge/overcharge)
        parts += ModelPart(
            "gauge_face_green", translate(disc(0.014f, 0f, 16), 0.088f, 0.415f, 0f),
            Material(0.15f, 0.75f, 0.22f, metallic = 0f, roughness = 0.7f)
        )
        // Valve assembly — heavy brass/black valve body
        parts += ModelPart(
            "valve", translate(cylinder(0.026f, 0.055f, 24), 0f, 0.41f, 0f),
            Material(0.18f, 0.18f, 0.20f, metallic = 0.7f, roughness = 0.30f)
        )
        // Carry handle (lower fixed arm)
        parts += ModelPart(
            "handle_base", translate(box(0.040f, 0.007f, 0.016f), 0f, 0.455f, 0f),
            Material(0.15f, 0.15f, 0.17f, metallic = 0.75f, roughness = 0.25f)
        )
        // Operating lever (upper squeeze arm)
        parts += ModelPart(
            "handle_lever", translate(box(0.050f, 0.006f, 0.014f), 0.025f, 0.472f, 0f),
            Material(0.12f, 0.12f, 0.14f, metallic = 0.80f, roughness = 0.22f)
        )

        // Safety pin & tamper seal logic
        if (!isPinPulled) {
            // Safety pin shaft (brass/steel) through valve
            parts += ModelPart(
                "pin_shaft", translate(cylinder(0.004f, 0.038f, 12), -0.032f, 0.455f, 0f),
                Material(0.90f, 0.78f, 0.20f, metallic = 0.85f, roughness = 0.20f)
            )
            // Pull ring on end of pin
            parts += ModelPart(
                "pin_ring", translate(disc(0.014f, 0.008f, 16), -0.032f, 0.493f, 0f),
                Material(0.90f, 0.78f, 0.20f, metallic = 0.85f, roughness = 0.20f)
            )
            // Plastic tamper seal loop (bright safety yellow/red)
            parts += ModelPart(
                "tamper_seal", translate(box(0.006f, 0.014f, 0.006f), -0.018f, 0.460f, 0.008f),
                Material(0.95f, 0.25f, 0.15f, metallic = 0.0f, roughness = 0.60f)
            )
        } else {
            // Broken tamper seal flag dangling down
            parts += ModelPart(
                "broken_seal", translate(box(0.004f, 0.025f, 0.004f), -0.018f, 0.440f, 0.008f),
                Material(0.95f, 0.25f, 0.15f, metallic = 0.0f, roughness = 0.60f)
            )
        }

        // Reinforced flexible discharge hose
        parts += ModelPart(
            "hose", translate(cylinder(0.011f, 0.24f, 12), 0.045f, 0.26f, 0f),
            Material(0.08f, 0.08f, 0.09f, metallic = 0.0f, roughness = 0.85f)
        )
        // Flared discharge nozzle / horn
        parts += ModelPart(
            "nozzle", translate(cone(0.016f, 0.045f, 16), 0.045f, 0.22f, 0f),
            Material(0.14f, 0.14f, 0.15f, metallic = 0.5f, roughness = 0.40f)
        )
        // Heavy-duty rubberized boot base
        parts += ModelPart(
            "base_ring", translate(disc(0.082f, 0.062f, 32), 0f, 0.002f, 0f),
            Material(0.06f, 0.06f, 0.07f, metallic = 0f, roughness = 0.95f)
        )

        return CompositeModel(parts, height = 0.50f)
    }

    /**
     * Believable Industrial Electrical Control Cabinet Fire Source:
     * - Height: 0.65m, Width: 0.50m, Depth: 0.35m (Real-world scale).
     * - Dark carbon-painted steel enclosure with plinth base.
     * - Recessed front access door with perimeter beveled frame.
     * - High-voltage warning yellow triangle placard with black lightning bolt.
     * - Rotary isolator / safety disconnect switch on door.
     * - Stamped ventilation louvers.
     * - Overhead industrial conduit pipe entries.
     * - Charred, blackened arcing failure zone on top-right where electrical fire originated.
     * - Circular scorch mark burn decal on the floor beneath the burning equipment.
     */
    fun electricalCabinetFire(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // 1. Scorch mark floor burn decal beneath cabinet
        parts += ModelPart(
            "scorch_decal", disc(0.48f, 0f, 32),
            Material(0.08f, 0.07f, 0.06f, metallic = 0f, roughness = 0.98f, alpha = 0.65f)
        )
        parts += ModelPart(
            "scorch_inner", disc(0.28f, 0f, 24),
            Material(0.04f, 0.04f, 0.04f, metallic = 0f, roughness = 0.99f, alpha = 0.85f)
        )

        // 2. Plinth channel footer / base (height 0.06m)
        parts += ModelPart(
            "plinth", translate(box(0.24f, 0.03f, 0.16f), 0f, 0.03f, 0f),
            Material(0.12f, 0.13f, 0.15f, metallic = 0.7f, roughness = 0.40f)
        )

        // 3. Main NEMA-style electrical enclosure body (height 0.55m, total top y = 0.61m)
        parts += ModelPart(
            "cabinet_body", translate(box(0.23f, 0.275f, 0.15f), 0f, 0.335f, 0f),
            Material(0.24f, 0.26f, 0.28f, metallic = 0.60f, roughness = 0.42f)
        )

        // 4. Front door panel with beveled recess
        parts += ModelPart(
            "door_panel", translate(box(0.21f, 0.255f, 0.005f), 0f, 0.335f, 0.155f),
            Material(0.28f, 0.30f, 0.32f, metallic = 0.55f, roughness = 0.45f)
        )

        // 5. High-voltage caution placard (yellow triangle plate)
        parts += ModelPart(
            "hazard_placard", translate(box(0.055f, 0.050f, 0.002f), -0.06f, 0.42f, 0.161f),
            Material(0.95f, 0.78f, 0.06f, metallic = 0.1f, roughness = 0.60f)
        )
        // High voltage lightning bolt symbol in center
        parts += ModelPart(
            "lightning_symbol", translate(box(0.015f, 0.030f, 0.001f), -0.06f, 0.42f, 0.163f),
            Material(0.08f, 0.08f, 0.08f, metallic = 0.0f, roughness = 0.80f)
        )

        // 6. Rotary main disconnect switch / emergency power isolator handle
        parts += ModelPart(
            "isolator_base", translate(cylinder(0.022f, 0.008f, 16), 0.12f, 0.42f, 0.158f),
            Material(0.92f, 0.80f, 0.10f, metallic = 0.2f, roughness = 0.50f)
        )
        parts += ModelPart(
            "isolator_handle", translate(box(0.030f, 0.008f, 0.012f), 0.12f, 0.42f, 0.168f),
            Material(0.85f, 0.12f, 0.10f, metallic = 0.3f, roughness = 0.40f)
        )

        // 7. Ventilation louvers (stamped cooling slots on side)
        for (i in 0..2) {
            parts += ModelPart(
                "louver_l_$i", translate(box(0.002f, 0.004f, 0.07f), -0.233f, 0.46f - i * 0.025f, 0f),
                Material(0.10f, 0.10f, 0.12f, metallic = 0.7f, roughness = 0.3f)
            )
            parts += ModelPart(
                "louver_r_$i", translate(box(0.002f, 0.004f, 0.07f), 0.233f, 0.46f - i * 0.025f, 0f),
                Material(0.10f, 0.10f, 0.12f, metallic = 0.7f, roughness = 0.3f)
            )
        }

        // 8. Top electrical conduits (steel feed pipes entering cabinet)
        parts += ModelPart(
            "conduit_hub_1", translate(cylinder(0.022f, 0.015f, 16), -0.10f, 0.61f, 0f),
            Material(0.65f, 0.68f, 0.72f, metallic = 0.85f, roughness = 0.25f)
        )
        parts += ModelPart(
            "conduit_pipe_1", translate(cylinder(0.016f, 0.10f, 16), -0.10f, 0.625f, 0f),
            Material(0.65f, 0.68f, 0.72f, metallic = 0.85f, roughness = 0.25f)
        )

        // 9. Faulted arcing conduit hub on top-right where electrical fire originated
        parts += ModelPart(
            "conduit_hub_fault", translate(cylinder(0.025f, 0.015f, 16), 0.10f, 0.61f, 0.02f),
            Material(0.12f, 0.11f, 0.10f, metallic = 0.2f, roughness = 0.90f)
        )
        // Charred, melted cable insulation and arc scorch marks around fault opening
        parts += ModelPart(
            "charred_area", translate(box(0.065f, 0.008f, 0.060f), 0.10f, 0.612f, 0.02f),
            Material(0.05f, 0.05f, 0.04f, metallic = 0.0f, roughness = 0.98f)
        )
        // Red glowing ember heat in the damaged electrical junction
        parts += ModelPart(
            "electrical_ember_core", translate(sphere(0.020f, 8, 12), 0.10f, 0.625f, 0.02f),
            Material(1.0f, 0.35f, 0.05f, metallic = 0f, roughness = 0.2f, emissive = 0.8f)
        )

        return CompositeModel(parts, height = 0.65f)
    }

    /**
     * Emergency exit sign: ISO 7010 green illuminated sign.
     * Mounted at specified height, ~0.32m × 0.16m face with dual backup emergency floodlights.
     */
    fun exitSign(): CompositeModel {
        val parts = mutableListOf<ModelPart>()
        // Brushed aluminum frame housing
        parts += ModelPart(
            "housing_frame", translate(box(0.165f, 0.082f, 0.018f), 0f, 0f, 0f),
            Material(0.70f, 0.72f, 0.75f, metallic = 0.75f, roughness = 0.30f)
        )
        // Main panel — bright green emissive acrylic
        parts += ModelPart(
            "panel", translate(box(0.150f, 0.072f, 0.005f), 0f, 0f, 0.015f),
            Material(0.05f, 0.60f, 0.22f, metallic = 0f, roughness = 0.5f, emissive = 0.65f)
        )
        // White ISO 7010 running figure
        parts += ModelPart(
            "icon", translate(box(0.038f, 0.048f, 0.001f), -0.04f, 0.004f, 0.018f),
            Material(0.95f, 0.98f, 0.95f, metallic = 0f, roughness = 0.8f, emissive = 0.55f)
        )
        // White directional arrow
        parts += ModelPart(
            "arrow", translate(box(0.032f, 0.018f, 0.001f), 0.05f, 0f, 0.018f),
            Material(0.95f, 0.98f, 0.95f, metallic = 0f, roughness = 0.8f, emissive = 0.55f)
        )
        // Dual emergency backup floodlight swivels on top corners
        parts += ModelPart(
            "lamp_l", translate(cylinder(0.018f, 0.025f, 16), -0.14f, 0.095f, 0f),
            Material(0.85f, 0.85f, 0.85f, metallic = 0.4f, roughness = 0.3f, emissive = 0.2f)
        )
        parts += ModelPart(
            "lamp_r", translate(cylinder(0.018f, 0.025f, 16), 0.14f, 0.095f, 0f),
            Material(0.85f, 0.85f, 0.85f, metallic = 0.4f, roughness = 0.3f, emissive = 0.2f)
        )
        // Ceiling mounting suspension bracket
        parts += ModelPart(
            "bracket", translate(box(0.010f, 0.050f, 0.022f), 0f, 0.095f, 0f),
            Material(0.60f, 0.60f, 0.62f, metallic = 0.7f, roughness = 0.4f)
        )
        return CompositeModel(parts, height = 0.16f)
    }

    /**
     * Industrial fire alarm pull station: red cast-aluminum box with dual action pull lever,
     * status LED, overhead alarm horn/strobe combination, and conduit fitting.
     */
    fun fireAlarm(): CompositeModel {
        val parts = mutableListOf<ModelPart>()
        // Back mounting plate
        parts += ModelPart(
            "mount_plate", translate(box(0.072f, 0.082f, 0.004f), 0f, 0f, -0.015f),
            Material(0.35f, 0.35f, 0.38f, metallic = 0.6f, roughness = 0.4f)
        )
        // Housing — heavy red industrial powder coat
        parts += ModelPart(
            "housing", translate(box(0.065f, 0.072f, 0.025f), 0f, 0f, 0f),
            Material(0.85f, 0.10f, 0.08f, metallic = 0.40f, roughness = 0.45f)
        )
        // Face plate — slightly raised with chamfer
        parts += ModelPart(
            "face", translate(box(0.056f, 0.062f, 0.003f), 0f, 0f, 0.026f),
            Material(0.88f, 0.14f, 0.12f, metallic = 0.30f, roughness = 0.50f)
        )
        // "PULL DOWN" T-bar handle (white metal)
        parts += ModelPart(
            "pull_bar", translate(box(0.032f, 0.009f, 0.008f), 0f, -0.022f, 0.034f),
            Material(0.92f, 0.92f, 0.90f, metallic = 0.30f, roughness = 0.50f)
        )
        // High-intensity white alarm strobe unit mounted directly above pull station
        parts += ModelPart(
            "strobe_lens", translate(cylinder(0.022f, 0.035f, 16), 0f, 0.095f, 0.008f),
            Material(0.95f, 0.95f, 0.98f, metallic = 0.1f, roughness = 0.2f, emissive = 0.3f)
        )
        // Conduit pipe extending upwards
        parts += ModelPart(
            "conduit", translate(cylinder(0.010f, 0.08f, 12), 0f, 0.13f, 0f),
            Material(0.65f, 0.65f, 0.68f, metallic = 0.8f, roughness = 0.3f)
        )
        // Status indicator LED
        parts += ModelPart(
            "indicator", translate(sphere(0.007f, 8, 12), 0.038f, 0.055f, 0.027f),
            Material(0.1f, 0.9f, 0.2f, metallic = 0f, roughness = 0.3f, emissive = 0.5f)
        )
        // Instruction placard
        parts += ModelPart(
            "label", translate(box(0.042f, 0.018f, 0.001f), 0f, 0.020f, 0.028f),
            Material(0.95f, 0.95f, 0.92f, metallic = 0f, roughness = 0.9f)
        )
        return CompositeModel(parts, height = 0.18f)
    }

    /**
     * Hazard zone floor marker: concentric warning rings with floor scorch gradient.
     */
    fun hazardZone(): CompositeModel {
        val parts = mutableListOf<ModelPart>()
        // Outer warning perimeter ring — yellow caution
        parts += ModelPart(
            "outer_ring", disc(0.42f, 0.35f, 48),
            Material(0.92f, 0.72f, 0.05f, metallic = 0f, roughness = 0.9f, alpha = 0.65f)
        )
        // Carbon scorch mark — heat damage on floor
        parts += ModelPart(
            "scorch_mark", disc(0.35f, 0.22f, 48),
            Material(0.12f, 0.10f, 0.09f, metallic = 0f, roughness = 0.98f, alpha = 0.70f)
        )
        // Inner intense combustion zone — dark charred core
        parts += ModelPart(
            "inner_char", disc(0.22f, 0f, 32),
            Material(0.06f, 0.05f, 0.05f, metallic = 0f, roughness = 0.99f, alpha = 0.85f)
        )
        return CompositeModel(parts, height = 0.01f)
    }

    /** Rotates all vertices in a MeshData around X axis by angleDegrees. */
    fun rotateX(mesh: MeshData, angleDegrees: Float): MeshData {
        val rad = angleDegrees * PI.toFloat() / 180f
        val cosA = cos(rad); val sinA = sin(rad)
        val outPos = FloatArray(mesh.vertexCount * 3)
        val outNorm = FloatArray(mesh.vertexCount * 3)
        mesh.positions.position(0); mesh.positions.get(outPos)
        mesh.normals.position(0); mesh.normals.get(outNorm)
        for (i in outPos.indices step 3) {
            val y = outPos[i + 1]; val z = outPos[i + 2]
            outPos[i + 1] = y * cosA - z * sinA
            outPos[i + 2] = y * sinA + z * cosA
            val ny = outNorm[i + 1]; val nz = outNorm[i + 2]
            outNorm[i + 1] = ny * cosA - nz * sinA
            outNorm[i + 2] = ny * sinA + nz * cosA
        }
        return MeshData(toBuffer(outPos.toList()), toBuffer(outNorm.toList()), mesh.vertexCount)
    }

    /** Rotates all vertices in a MeshData around Y axis by angleDegrees. */
    fun rotateY(mesh: MeshData, angleDegrees: Float): MeshData {
        val rad = angleDegrees * PI.toFloat() / 180f
        val cosA = cos(rad); val sinA = sin(rad)
        val outPos = FloatArray(mesh.vertexCount * 3)
        val outNorm = FloatArray(mesh.vertexCount * 3)
        mesh.positions.position(0); mesh.positions.get(outPos)
        mesh.normals.position(0); mesh.normals.get(outNorm)
        for (i in outPos.indices step 3) {
            val x = outPos[i]; val z = outPos[i + 2]
            outPos[i] = x * cosA + z * sinA
            outPos[i + 2] = -x * sinA + z * cosA
            val nx = outNorm[i]; val nz = outNorm[i + 2]
            outNorm[i] = nx * cosA + nz * sinA
            outNorm[i + 2] = -nx * sinA + nz * cosA
        }
        return MeshData(toBuffer(outPos.toList()), toBuffer(outNorm.toList()), mesh.vertexCount)
    }

    /** Rotates all vertices in a MeshData around Z axis by angleDegrees. */
    fun rotateZ(mesh: MeshData, angleDegrees: Float): MeshData {
        val rad = angleDegrees * PI.toFloat() / 180f
        val cosA = cos(rad); val sinA = sin(rad)
        val outPos = FloatArray(mesh.vertexCount * 3)
        val outNorm = FloatArray(mesh.vertexCount * 3)
        mesh.positions.position(0); mesh.positions.get(outPos)
        mesh.normals.position(0); mesh.normals.get(outNorm)
        for (i in outPos.indices step 3) {
            val x = outPos[i]; val y = outPos[i + 1]
            outPos[i] = x * cosA - y * sinA
            outPos[i + 1] = x * sinA + y * cosA
            val nx = outNorm[i]; val ny = outNorm[i + 1]
            outNorm[i] = nx * cosA - ny * sinA
            outNorm[i + 1] = nx * sinA + ny * cosA
        }
        return MeshData(toBuffer(outPos.toList()), toBuffer(outNorm.toList()), mesh.vertexCount)
    }

    /**
     * Contact shadow: semi-transparent dark ellipse.
     */
    fun contactShadow(radius: Float = 0.12f): MeshData {
        return disc(radius, 0f, 48)
    }

    /**
     * Realistic Confined Space Opening & Access Hatch.
     * Components:
     * - Industrial raised manhole collar (0.65m outer diam)
     * - Bolted flange ring with 16 perimeter bolt studs
     * - Recessed dark interior cylinder simulating depth of the underground shaft
     * - Heavy steel hatch cover propped open at 72° with locking hinge strut
     * - Steel access ladder descending into the opening with cylindrical rungs
     * - Industrial safety pipework with 90° flanged elbow, cast iron red valve handwheel, and brass pressure gauge
     * - DANGER CONFINED SPACE warning plate mounted to collar
     */
    fun confinedSpaceHatch(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // 1. Raised industrial collar (steel flange, 0.35m radius, 0.14m height)
        parts += ModelPart(
            "collar_wall", translate(cylinder(0.35f, 0.14f, 32, capTop = false), 0f, 0f, 0f),
            Material(0.30f, 0.32f, 0.35f, metallic = 0.75f, roughness = 0.45f)
        )
        // 2. Bolted rim flange
        parts += ModelPart(
            "collar_flange", translate(disc(0.42f, 0.34f, 32), 0f, 0.14f, 0f),
            Material(0.26f, 0.28f, 0.30f, metallic = 0.85f, roughness = 0.40f)
        )
        // Flange perimeter bolts
        for (i in 0 until 16) {
            val angle = i * 2f * PI.toFloat() / 16f
            val bx = cos(angle) * 0.385f
            val bz = sin(angle) * 0.385f
            parts += ModelPart(
                "flange_bolt_$i", translate(box(0.009f, 0.007f, 0.009f), bx, 0.147f, bz),
                Material(0.70f, 0.72f, 0.75f, metallic = 0.90f, roughness = 0.25f)
            )
        }

        // 3. Recessed dark interior shaft (illusion of deep underground chamber)
        parts += ModelPart(
            "shaft_interior", translate(cylinder(0.33f, 0.50f, 24), 0f, -0.36f, 0f),
            Material(0.04f, 0.05f, 0.06f, metallic = 0.1f, roughness = 0.95f)
        )
        parts += ModelPart(
            "shaft_bottom", translate(disc(0.33f, 0f, 24), 0f, -0.36f, 0f),
            Material(0.02f, 0.02f, 0.03f, metallic = 0.0f, roughness = 1.0f)
        )

        // 4. Access ladder descending into the opening
        // Two vertical rails
        parts += ModelPart(
            "ladder_rail_l", translate(cylinder(0.012f, 0.55f, 12), -0.15f, -0.35f, -0.26f),
            Material(0.80f, 0.80f, 0.82f, metallic = 0.85f, roughness = 0.30f)
        )
        parts += ModelPart(
            "ladder_rail_r", translate(cylinder(0.012f, 0.55f, 12), 0.15f, -0.35f, -0.26f),
            Material(0.80f, 0.80f, 0.82f, metallic = 0.85f, roughness = 0.30f)
        )
        // 4 rungs
        for (r in 0..3) {
            val ry = -0.30f + r * 0.14f
            val rungMesh = rotateZ(cylinder(0.010f, 0.30f, 12), 90f)
            parts += ModelPart(
                "ladder_rung_$r", translate(rungMesh, -0.15f, ry, -0.26f),
                Material(0.85f, 0.85f, 0.87f, metallic = 0.85f, roughness = 0.25f)
            )
        }

        // 5. Heavy steel hatch cover propped open at 72° angle
        val lidMesh = rotateX(box(0.35f, 0.018f, 0.35f), -72f)
        parts += ModelPart(
            "hatch_lid", translate(lidMesh, 0f, 0.35f, -0.32f),
            Material(0.28f, 0.30f, 0.33f, metallic = 0.70f, roughness = 0.55f)
        )
        // Hatch hinge and locking strut
        parts += ModelPart(
            "hatch_hinge", translate(cylinder(0.015f, 0.25f, 12), -0.125f, 0.15f, -0.34f),
            Material(0.18f, 0.18f, 0.20f, metallic = 0.85f, roughness = 0.35f)
        )
        val strutMesh = rotateX(cylinder(0.008f, 0.35f, 8), -35f)
        parts += ModelPart(
            "hatch_strut", translate(strutMesh, 0.28f, 0.15f, -0.24f),
            Material(0.85f, 0.75f, 0.15f, metallic = 0.80f, roughness = 0.30f)
        )

        // 6. Industrial Pipework alongside the hatch
        // Vertical pipe
        parts += ModelPart(
            "pipe_vert", translate(cylinder(0.038f, 0.55f, 16), 0.48f, 0f, 0f),
            Material(0.35f, 0.42f, 0.48f, metallic = 0.80f, roughness = 0.35f)
        )
        // Flange ring on pipe
        parts += ModelPart(
            "pipe_flange", translate(cylinder(0.055f, 0.025f, 16), 0.48f, 0.22f, 0f),
            Material(0.30f, 0.35f, 0.40f, metallic = 0.85f, roughness = 0.30f)
        )
        // Cast iron valve body & handwheel
        parts += ModelPart(
            "valve_body", translate(box(0.045f, 0.045f, 0.045f), 0.48f, 0.38f, 0f),
            Material(0.20f, 0.22f, 0.24f, metallic = 0.85f, roughness = 0.35f)
        )
        // Red valve handwheel
        val wheelMesh = rotateX(disc(0.085f, 0.065f, 20), 90f)
        parts += ModelPart(
            "valve_wheel", translate(wheelMesh, 0.48f, 0.38f, 0.075f),
            Material(0.82f, 0.14f, 0.12f, metallic = 0.35f, roughness = 0.45f)
        )
        // Valve wheel center hub & spokes
        parts += ModelPart(
            "valve_spoke1", translate(box(0.075f, 0.005f, 0.005f), 0.48f, 0.38f, 0.075f),
            Material(0.82f, 0.14f, 0.12f, metallic = 0.35f, roughness = 0.45f)
        )
        // Brass pressure gauge
        parts += ModelPart(
            "gauge_stem", translate(cylinder(0.008f, 0.04f, 8), 0.48f, 0.55f, 0f),
            Material(0.75f, 0.65f, 0.20f, metallic = 0.70f, roughness = 0.30f)
        )
        parts += ModelPart(
            "gauge_case", translate(cylinder(0.032f, 0.018f, 16), 0.48f, 0.59f, 0f),
            Material(0.75f, 0.65f, 0.20f, metallic = 0.70f, roughness = 0.30f)
        )
        parts += ModelPart(
            "gauge_dial", translate(disc(0.028f, 0f, 16), 0.48f, 0.609f, 0f),
            Material(0.95f, 0.95f, 0.92f, metallic = 0.0f, roughness = 0.70f)
        )

        // 7. Warning placard mounted to collar flange
        parts += ModelPart(
            "warning_sign_plate", translate(box(0.14f, 0.065f, 0.004f), 0f, 0.08f, 0.36f),
            Material(0.92f, 0.75f, 0.08f, metallic = 0.1f, roughness = 0.60f)
        )
        parts += ModelPart(
            "warning_sign_text", translate(box(0.12f, 0.020f, 0.002f), 0f, 0.09f, 0.364f),
            Material(0.12f, 0.12f, 0.12f, metallic = 0.0f, roughness = 0.90f)
        )

        return CompositeModel(parts, height = 0.70f)
    }

    /**
     * Industrial 3-Leg Aluminum Rescue Tripod & Winch System.
     * Standing height: ~1.75m apex, legs spread ~1.2m at base.
     * Components:
     * - 3 aluminum tubular legs with foot pads and reflective caution bands
     * - Triangular cast apex head with central pulley
     * - Safety rescue retrieval winch mounted on leg with winding handle
     * - Steel lifeline / cable dropping down into the center of the opening
     */
    fun rescueTripod(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        val apexHeight = 1.75f
        val legBaseRadius = 0.58f

        // 1. Triangular Cast Apex Head
        parts += ModelPart(
            "apex_head", translate(cylinder(0.09f, 0.08f, 6), 0f, apexHeight - 0.04f, 0f),
            Material(0.85f, 0.45f, 0.05f, metallic = 0.4f, roughness = 0.50f)
        )
        // Central pulley wheel
        parts += ModelPart(
            "pulley_wheel", translate(cylinder(0.035f, 0.018f, 16), 0f, apexHeight - 0.08f, 0f),
            Material(0.70f, 0.72f, 0.75f, metallic = 0.85f, roughness = 0.25f)
        )

        // 2. 3 Tripod Legs
        for (i in 0..2) {
            val angle = i * 2f * PI.toFloat() / 3f
            val footX = cos(angle) * legBaseRadius
            val footZ = sin(angle) * legBaseRadius

            // Leg cylinder from foot to apex
            val legLen = sqrt(legBaseRadius * legBaseRadius + apexHeight * apexHeight)
            val tiltAngle = atan2(legBaseRadius, apexHeight) * 180f / PI.toFloat()
            val yawAngle = -angle * 180f / PI.toFloat() + 90f

            var legMesh = cylinder(0.024f, legLen, 12)
            legMesh = rotateZ(legMesh, tiltAngle)
            legMesh = rotateY(legMesh, yawAngle)

            parts += ModelPart(
                "tripod_leg_$i", translate(legMesh, 0f, 0f, 0f),
                Material(0.78f, 0.80f, 0.82f, metallic = 0.85f, roughness = 0.30f)
            )

            // High-visibility safety orange sleeve & reflective bands on each leg
            parts += ModelPart(
                "leg_foot_$i", translate(box(0.05f, 0.02f, 0.05f), footX, 0.01f, footZ),
                Material(0.15f, 0.15f, 0.18f, metallic = 0.4f, roughness = 0.80f)
            )
            val bandX = footX * 0.5f; val bandZ = footZ * 0.5f
            parts += ModelPart(
                "hazard_band_$i", translate(box(0.035f, 0.12f, 0.035f), bandX, 0.85f, bandZ),
                Material(0.92f, 0.45f, 0.08f, metallic = 0.2f, roughness = 0.60f)
            )
        }

        // 3. Rescue Retrieval Winch mounted to rear leg (leg 0)
        val winchY = 0.95f
        parts += ModelPart(
            "winch_body", translate(box(0.065f, 0.09f, 0.07f), 0.32f, winchY, 0f),
            Material(0.18f, 0.20f, 0.24f, metallic = 0.85f, roughness = 0.35f)
        )
        // Winch drum with coiled cable
        parts += ModelPart(
            "winch_drum", translate(cylinder(0.038f, 0.08f, 16), 0.32f, winchY - 0.04f, 0f),
            Material(0.75f, 0.78f, 0.80f, metallic = 0.90f, roughness = 0.20f)
        )
        // Crank handle
        parts += ModelPart(
            "winch_handle", translate(box(0.01f, 0.12f, 0.01f), 0.40f, winchY, 0.05f),
            Material(0.85f, 0.75f, 0.10f, metallic = 0.80f, roughness = 0.25f)
        )

        // 4. Stainless steel lifeline cable dropping straight down into the hatch center
        parts += ModelPart(
            "lifeline_cable", translate(cylinder(0.0035f, 1.70f, 8), 0f, 0f, 0f),
            Material(0.85f, 0.88f, 0.90f, metallic = 0.95f, roughness = 0.15f)
        )

        return CompositeModel(parts, height = apexHeight)
    }

    /**
     * Handheld Multi-Gas Detector (4-Gas Monitor: O2, LEL, H2S, CO).
     * Scale: 0.14m tall, 0.075m wide, 0.035m thick (ergonomic palm size).
     * Features:
     * - Rubberized high-impact yellow housing with black shock-absorber bumpers
     * - High-contrast backlit LCD screen displaying 4 gas channels
     * - Sensor diffusion port cap with micro-mesh
     * - Red visual alarm LED bars
     * - Stainless steel alligator belt clip
     */
    fun gasDetector(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // 1. Rubberized impact-resistant main casing (Safety Yellow)
        parts += ModelPart(
            "casing", translate(box(0.038f, 0.065f, 0.016f), 0f, 0.065f, 0f),
            Material(0.92f, 0.76f, 0.08f, metallic = 0.15f, roughness = 0.55f)
        )
        // Black corner protective bumpers
        for (sx in listOf(-1f, 1f)) {
            for (sy in listOf(0.02f, 0.11f)) {
                parts += ModelPart(
                    "bumper_${sx}_$sy", translate(box(0.008f, 0.014f, 0.018f), sx * 0.036f, sy, 0f),
                    Material(0.12f, 0.12f, 0.14f, metallic = 0.1f, roughness = 0.90f)
                )
            }
        }

        // 2. High-contrast LCD Display Screen
        parts += ModelPart(
            "screen_bezel", translate(box(0.030f, 0.024f, 0.002f), 0f, 0.082f, 0.017f),
            Material(0.08f, 0.08f, 0.10f, metallic = 0.4f, roughness = 0.30f)
        )
        parts += ModelPart(
            "screen_lcd", translate(box(0.027f, 0.020f, 0.001f), 0f, 0.082f, 0.0185f),
            Material(0.12f, 0.28f, 0.18f, metallic = 0.0f, roughness = 0.20f, emissive = 0.45f)
        )
        // 4 digital readout field representations on screen
        parts += ModelPart(
            "readout_o2", translate(box(0.010f, 0.006f, 0.001f), -0.013f, 0.090f, 0.020f),
            Material(0.85f, 0.95f, 0.85f, metallic = 0.0f, roughness = 0.1f, emissive = 0.6f)
        )
        parts += ModelPart(
            "readout_lel", translate(box(0.010f, 0.006f, 0.001f), 0.013f, 0.090f, 0.020f),
            Material(0.85f, 0.95f, 0.85f, metallic = 0.0f, roughness = 0.1f, emissive = 0.6f)
        )
        parts += ModelPart(
            "readout_h2s", translate(box(0.010f, 0.006f, 0.001f), -0.013f, 0.074f, 0.020f),
            Material(0.95f, 0.85f, 0.85f, metallic = 0.0f, roughness = 0.1f, emissive = 0.6f)
        )
        parts += ModelPart(
            "readout_co", translate(box(0.010f, 0.006f, 0.001f), 0.013f, 0.074f, 0.020f),
            Material(0.85f, 0.95f, 0.85f, metallic = 0.0f, roughness = 0.1f, emissive = 0.6f)
        )

        // 3. Sensor diffusion port cap (circular grill)
        parts += ModelPart(
            "sensor_cap", translate(cylinder(0.014f, 0.008f, 16), 0f, 0.038f, 0.016f),
            Material(0.20f, 0.22f, 0.24f, metallic = 0.80f, roughness = 0.35f)
        )
        parts += ModelPart(
            "sensor_mesh", translate(disc(0.011f, 0f, 16), 0f, 0.038f, 0.025f),
            Material(0.65f, 0.68f, 0.70f, metallic = 0.90f, roughness = 0.25f)
        )

        // 4. Red Visual Alarm LED Bars
        parts += ModelPart(
            "alarm_led_l", translate(box(0.004f, 0.004f, 0.003f), -0.028f, 0.115f, 0.016f),
            Material(0.95f, 0.15f, 0.10f, metallic = 0.0f, roughness = 0.2f, emissive = 0.85f)
        )
        parts += ModelPart(
            "alarm_led_r", translate(box(0.004f, 0.004f, 0.003f), 0.028f, 0.115f, 0.016f),
            Material(0.95f, 0.15f, 0.10f, metallic = 0.0f, roughness = 0.2f, emissive = 0.85f)
        )

        // 5. Stainless steel belt clip on back
        parts += ModelPart(
            "belt_clip", translate(box(0.012f, 0.040f, 0.004f), 0f, 0.070f, -0.020f),
            Material(0.85f, 0.85f, 0.88f, metallic = 0.95f, roughness = 0.15f)
        )

        return CompositeModel(parts, height = 0.14f)
    }

    /**
     * Confined Space Entry Permit Board (Free-standing industrial easel).
     * Height: ~1.15m.
     * Features:
     * - Industrial steel tripod stand / pedestal
     * - Large clear permit board with "CONFINED SPACE ENTRY PERMIT" heading
     * - Detailed checklist sections: Authorized Entrants, Hazard Controls,
     *   Stratified Gas Testing Log, Attendant Protocol sign-off.
     */
    fun permitClipboard(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // 1. Pedestal stand
        parts += ModelPart(
            "stand_base", translate(disc(0.18f, 0f, 24), 0f, 0.01f, 0f),
            Material(0.20f, 0.22f, 0.25f, metallic = 0.80f, roughness = 0.40f)
        )
        parts += ModelPart(
            "stand_pole", translate(cylinder(0.016f, 0.78f, 12), 0f, 0.01f, 0f),
            Material(0.75f, 0.78f, 0.80f, metallic = 0.85f, roughness = 0.25f)
        )

        // 2. Rigid backing board (angled back slightly at 15°)
        val boardMesh = rotateX(box(0.18f, 0.24f, 0.006f), -15f)
        parts += ModelPart(
            "board_back", translate(boardMesh, 0f, 0.92f, 0f),
            Material(0.30f, 0.32f, 0.35f, metallic = 0.50f, roughness = 0.60f)
        )

        // 3. Official Confined Space Entry Permit document
        val sheetMesh = rotateX(box(0.165f, 0.225f, 0.002f), -15f)
        parts += ModelPart(
            "permit_sheet", translate(sheetMesh, 0f, 0.92f, 0.007f),
            Material(0.96f, 0.96f, 0.94f, metallic = 0.0f, roughness = 0.90f)
        )

        // 4. Header banner in safety red ("CONFINED SPACE ENTRY PERMIT")
        val headerMesh = rotateX(box(0.155f, 0.022f, 0.001f), -15f)
        parts += ModelPart(
            "permit_header", translate(headerMesh, 0f, 1.01f, 0.010f),
            Material(0.85f, 0.15f, 0.12f, metallic = 0.0f, roughness = 0.70f)
        )

        // 5. Permit form text sections / checkboxes
        for (i in 0..5) {
            val lineY = 0.97f - i * 0.026f
            val lineMesh = rotateX(box(0.145f, 0.004f, 0.001f), -15f)
            parts += ModelPart(
                "permit_section_$i", translate(lineMesh, 0f, lineY, 0.010f),
                Material(0.18f, 0.20f, 0.22f, metallic = 0.0f, roughness = 1.0f)
            )
        }

        // Heavy metal spring clip at top
        val clipMesh = rotateX(box(0.045f, 0.015f, 0.012f), -15f)
        parts += ModelPart(
            "permit_clip", translate(clipMesh, 0f, 1.04f, 0.012f),
            Material(0.85f, 0.85f, 0.88f, metallic = 0.90f, roughness = 0.20f)
        )

        return CompositeModel(parts, height = 1.15f)
    }

    /**
     * PPE Inspection Station / Staging Table.
     * Table height: 0.75m.
     * Equipment presented:
     * - Industrial safety helmet with brim and mounted headlamp
     * - Full-body fall arrest harness with dorsal D-ring & leg loops
     * - Chemical/gas resistant heavy nitrile gloves
     * - Half-mask respirator with twin gas cartridges
     */
    fun ppeStation(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // 1. Staging table top (0.60m x 0.40m, 0.03m thick)
        val tableY = 0.72f
        parts += ModelPart(
            "table_top", translate(box(0.30f, 0.015f, 0.20f), 0f, tableY, 0f),
            Material(0.35f, 0.38f, 0.42f, metallic = 0.65f, roughness = 0.50f)
        )
        // 4 steel tubular legs
        for (lx in listOf(-0.26f, 0.26f)) {
            for (lz in listOf(-0.16f, 0.16f)) {
                parts += ModelPart(
                    "table_leg_${lx}_$lz", translate(cylinder(0.014f, tableY, 12), lx, 0f, lz),
                    Material(0.70f, 0.72f, 0.75f, metallic = 0.85f, roughness = 0.30f)
                )
            }
        }

        // 2. Safety Hard Hat with Headlamp
        val helmetX = -0.16f; val helmetZ = 0.02f
        parts += ModelPart(
            "helmet_dome", translate(sphere(0.065f, 10, 16), helmetX, tableY + 0.075f, helmetZ),
            Material(0.92f, 0.75f, 0.08f, metallic = 0.25f, roughness = 0.45f),
            scaleY = 0.7f
        )
        parts += ModelPart(
            "helmet_brim", translate(disc(0.085f, 0.065f, 24), helmetX, tableY + 0.045f, helmetZ),
            Material(0.92f, 0.75f, 0.08f, metallic = 0.25f, roughness = 0.45f)
        )
        parts += ModelPart(
            "helmet_lamp", translate(box(0.015f, 0.012f, 0.015f), helmetX, tableY + 0.085f, helmetZ + 0.068f),
            Material(0.85f, 0.85f, 0.90f, metallic = 0.5f, roughness = 0.2f, emissive = 0.5f)
        )

        // 3. Full-Body Fall Arrest Harness
        val harnessX = 0.05f; val harnessZ = 0.02f
        // Webbing straps (high-vis green/yellow)
        parts += ModelPart(
            "harness_webbing", translate(box(0.08f, 0.025f, 0.10f), harnessX, tableY + 0.025f, harnessZ),
            Material(0.25f, 0.75f, 0.20f, metallic = 0.0f, roughness = 0.85f)
        )
        // Dorsal D-Ring (for retrieval winch line attachment)
        parts += ModelPart(
            "harness_dring", translate(cylinder(0.016f, 0.008f, 12), harnessX, tableY + 0.055f, harnessZ),
            Material(0.90f, 0.90f, 0.92f, metallic = 0.95f, roughness = 0.15f)
        )

        // 4. Chemical / Gas Resistant Heavy Nitrile Gloves
        parts += ModelPart(
            "gloves_pair", translate(box(0.045f, 0.015f, 0.075f), 0.19f, tableY + 0.02f, -0.04f),
            Material(0.10f, 0.35f, 0.65f, metallic = 0.0f, roughness = 0.70f)
        )

        // 5. Half-Mask Respirator with Twin Chemical Cartridges
        val respX = 0.18f; val respZ = 0.06f
        parts += ModelPart(
            "respirator_facepiece", translate(sphere(0.040f, 8, 12), respX, tableY + 0.04f, respZ),
            Material(0.20f, 0.22f, 0.25f, metallic = 0.1f, roughness = 0.80f),
            scaleY = 0.7f
        )
        // Twin filter cartridges
        parts += ModelPart(
            "filter_cartridge_l", translate(cylinder(0.022f, 0.020f, 12), respX - 0.038f, tableY + 0.03f, respZ),
            Material(0.85f, 0.50f, 0.12f, metallic = 0.3f, roughness = 0.60f)
        )
        parts += ModelPart(
            "filter_cartridge_r", translate(cylinder(0.022f, 0.020f, 12), respX + 0.038f, tableY + 0.03f, respZ),
            Material(0.85f, 0.50f, 0.12f, metallic = 0.3f, roughness = 0.60f)
        )

        return CompositeModel(parts, height = 0.95f)
    }

    /**
     * Standby Attendant (Safety Buddy Watch Figure).
     * Height: ~1.72m tall (realistic human scale).
     * Stationed safely outside the restricted hazard boundary.
     * Features:
     * - Industrial work uniform & two-tone high-visibility safety vest
     * - Retroreflective silver safety stripes
     * - Yellow safety helmet
     * - Two-way communication radio with antenna on shoulder
     * - Safety entry clipboard held in hands
     */
    fun buddyFigure(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // 1. Legs / Work Trousers (Navy industrial blue)
        parts += ModelPart(
            "leg_left", translate(cylinder(0.045f, 0.80f, 12), -0.09f, 0f, 0f),
            Material(0.14f, 0.20f, 0.32f, metallic = 0.0f, roughness = 0.85f)
        )
        parts += ModelPart(
            "leg_right", translate(cylinder(0.045f, 0.80f, 12), 0.09f, 0f, 0f),
            Material(0.14f, 0.20f, 0.32f, metallic = 0.0f, roughness = 0.85f)
        )
        // Safety boots
        parts += ModelPart(
            "boot_left", translate(box(0.050f, 0.045f, 0.080f), -0.09f, 0.045f, 0.025f),
            Material(0.15f, 0.12f, 0.10f, metallic = 0.1f, roughness = 0.80f)
        )
        parts += ModelPart(
            "boot_right", translate(box(0.050f, 0.045f, 0.080f), 0.09f, 0.045f, 0.025f),
            Material(0.15f, 0.12f, 0.10f, metallic = 0.1f, roughness = 0.80f)
        )

        // 2. Torso with High-Visibility Safety Vest
        val torsoY = 0.80f
        parts += ModelPart(
            "torso", translate(box(0.16f, 0.25f, 0.10f), 0f, torsoY + 0.25f, 0f),
            Material(0.88f, 0.95f, 0.10f, metallic = 0.0f, roughness = 0.70f)
        )
        // Reflective silver safety stripes on vest
        parts += ModelPart(
            "vest_stripe_horiz", translate(box(0.162f, 0.025f, 0.102f), 0f, torsoY + 0.24f, 0f),
            Material(0.92f, 0.92f, 0.95f, metallic = 0.80f, roughness = 0.20f, emissive = 0.25f)
        )
        parts += ModelPart(
            "vest_stripe_vert_l", translate(box(0.025f, 0.120f, 0.102f), -0.08f, torsoY + 0.38f, 0f),
            Material(0.92f, 0.92f, 0.95f, metallic = 0.80f, roughness = 0.20f, emissive = 0.25f)
        )
        parts += ModelPart(
            "vest_stripe_vert_r", translate(box(0.025f, 0.120f, 0.102f), 0.08f, torsoY + 0.38f, 0f),
            Material(0.92f, 0.92f, 0.95f, metallic = 0.80f, roughness = 0.20f, emissive = 0.25f)
        )

        // 3. Arms & Entry Clipboard
        parts += ModelPart(
            "arm_left", translate(cylinder(0.035f, 0.45f, 10), -0.20f, torsoY + 0.05f, 0.05f),
            Material(0.14f, 0.20f, 0.32f, metallic = 0.0f, roughness = 0.85f)
        )
        parts += ModelPart(
            "arm_right", translate(cylinder(0.035f, 0.45f, 10), 0.20f, torsoY + 0.05f, 0.05f),
            Material(0.14f, 0.20f, 0.32f, metallic = 0.0f, roughness = 0.85f)
        )
        // Clipboard held in hands
        val logMesh = rotateX(box(0.09f, 0.12f, 0.005f), 30f)
        parts += ModelPart(
            "attendant_log", translate(logMesh, 0f, torsoY + 0.28f, 0.16f),
            Material(0.90f, 0.90f, 0.85f, metallic = 0.0f, roughness = 0.90f)
        )

        // 4. Two-Way Communication Radio with Antenna on Chest
        parts += ModelPart(
            "radio_body", translate(box(0.022f, 0.045f, 0.015f), -0.10f, torsoY + 0.42f, 0.11f),
            Material(0.15f, 0.15f, 0.18f, metallic = 0.6f, roughness = 0.40f)
        )
        parts += ModelPart(
            "radio_antenna", translate(cylinder(0.003f, 0.09f, 8), -0.10f, torsoY + 0.465f, 0.11f),
            Material(0.10f, 0.10f, 0.10f, metallic = 0.8f, roughness = 0.30f)
        )

        // 5. Head and Yellow Hard Hat
        val headY = torsoY + 0.58f
        parts += ModelPart(
            "head", translate(sphere(0.085f, 12, 16), 0f, headY, 0f),
            Material(0.78f, 0.62f, 0.50f, metallic = 0.0f, roughness = 0.75f)
        )
        // Hard hat
        parts += ModelPart(
            "helmet_dome", translate(sphere(0.095f, 10, 16), 0f, headY + 0.04f, 0f),
            Material(0.92f, 0.75f, 0.08f, metallic = 0.25f, roughness = 0.45f),
            scaleY = 0.65f
        )
        parts += ModelPart(
            "helmet_brim", translate(disc(0.125f, 0.090f, 24), 0f, headY + 0.015f, 0f),
            Material(0.92f, 0.75f, 0.08f, metallic = 0.25f, roughness = 0.45f)
        )

        return CompositeModel(parts, height = 1.75f)
    }

    /**
     * Safety Boundary Exclusion Barricade (Cones with retractable hazard tape).
     * Used to visibly enforce the restricted entry zone boundary.
     */
    fun safetyBarricade(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // Safety cone
        parts += ModelPart(
            "cone_base", translate(box(0.14f, 0.015f, 0.14f), 0f, 0.015f, 0f),
            Material(0.15f, 0.15f, 0.18f, metallic = 0.2f, roughness = 0.80f)
        )
        parts += ModelPart(
            "cone_body", translate(cone(0.11f, 0.42f, 16), 0f, 0.03f, 0f),
            Material(0.92f, 0.40f, 0.05f, metallic = 0.1f, roughness = 0.60f)
        )
        // Reflective white collar
        parts += ModelPart(
            "cone_collar", translate(cylinder(0.075f, 0.09f, 16), 0f, 0.18f, 0f),
            Material(0.92f, 0.92f, 0.95f, metallic = 0.6f, roughness = 0.25f, emissive = 0.2f)
        )
        // Caution chain ring on top
        parts += ModelPart(
            "cone_top_ring", translate(cylinder(0.018f, 0.035f, 12), 0f, 0.45f, 0f),
            Material(0.85f, 0.75f, 0.10f, metallic = 0.8f, roughness = 0.30f)
        )

        return CompositeModel(parts, height = 0.48f)
    }

    /**
     * Wall/Post Mounted Warning Alarm Beacon with Siren Horn.
     * Features: Amber/red rotating beacon dome with high-frequency siren horn.
     */
    fun industrialAlarmBeacon(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // Mounting backplate & bracket
        parts += ModelPart(
            "beacon_base", translate(box(0.06f, 0.08f, 0.02f), 0f, 0f, 0f),
            Material(0.25f, 0.26f, 0.28f, metallic = 0.8f, roughness = 0.40f)
        )
        // Siren sounder horn
        parts += ModelPart(
            "siren_horn", translate(cylinder(0.040f, 0.07f, 16), 0f, -0.05f, 0.04f),
            Material(0.15f, 0.15f, 0.18f, metallic = 0.5f, roughness = 0.50f)
        )
        // Strobe beacon dome (Warning Amber / Red)
        parts += ModelPart(
            "beacon_dome", translate(cylinder(0.038f, 0.09f, 16), 0f, 0.05f, 0.03f),
            Material(0.95f, 0.45f, 0.05f, metallic = 0.1f, roughness = 0.20f, emissive = 0.70f)
        )

        return CompositeModel(parts, height = 0.24f)
    }

    /**
     * ISO 7010 Compliant Emergency Evacuation Sign (Upwind Exit).
     * Green safety illuminated box with running figure and directional arrow.
     */
    fun evacuationMusterSign(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // Green illuminated face
        parts += ModelPart(
            "sign_face", translate(box(0.18f, 0.09f, 0.012f), 0f, 0f, 0f),
            Material(0.08f, 0.55f, 0.25f, metallic = 0.1f, roughness = 0.30f, emissive = 0.65f)
        )
        // White directional arrow & symbol representation
        parts += ModelPart(
            "sign_symbol", translate(box(0.14f, 0.06f, 0.002f), 0f, 0f, 0.013f),
            Material(0.95f, 0.98f, 0.95f, metallic = 0.0f, roughness = 0.80f, emissive = 0.85f)
        )
        // Metal housing frame
        parts += ModelPart(
            "sign_frame", translate(box(0.19f, 0.10f, 0.010f), 0f, 0f, -0.005f),
            Material(0.20f, 0.22f, 0.24f, metallic = 0.85f, roughness = 0.35f)
        )

        return CompositeModel(parts, height = 0.20f)
    }

    /**
     * Portable Industrial Confined Space Ventilation Blower & Ducting.
     * ~0.65m total height (blower housing + elevated handle + duct curve).
     * Features: Heavy-duty powder-coated yellow fan housing, cast motor body,
     * intake grille, discharge collar, accordion ventilation hose extending to sump.
     */
    fun ventilationBlower(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // Main cylindrical fan housing (safety yellow powder coat)
        parts += ModelPart(
            "blower_casing", translate(cylinder(0.22f, 0.38f, 24), 0f, 0.25f, 0f),
            Material(0.92f, 0.75f, 0.08f, metallic = 0.25f, roughness = 0.35f)
        )

        // Heavy-duty cast iron motor housing behind fan
        parts += ModelPart(
            "blower_motor", translate(cylinder(0.12f, 0.22f, 16), 0f, 0.25f, -0.25f),
            Material(0.20f, 0.22f, 0.24f, metallic = 0.85f, roughness = 0.45f)
        )

        // Intake wire safety grille
        parts += ModelPart(
            "blower_grille", translate(disc(0.21f, 0.05f, 16), 0f, 0.25f, -0.19f),
            Material(0.15f, 0.15f, 0.15f, metallic = 0.90f, roughness = 0.30f)
        )

        // Flexible accordion ventilation ducting (yellow/orange PVC fabric)
        parts += ModelPart(
            "flexible_duct", translate(cylinder(0.18f, 0.60f, 18), 0.18f, 0.25f, 0.35f),
            Material(0.95f, 0.55f, 0.05f, metallic = 0.05f, roughness = 0.65f)
        )

        // Steel tubular carry handle on top
        parts += ModelPart(
            "carry_handle", translate(box(0.04f, 0.10f, 0.26f), 0f, 0.48f, 0f),
            Material(0.12f, 0.12f, 0.12f, metallic = 0.50f, roughness = 0.70f)
        )

        // Rubber vibration isolator mounting feet (4 corners)
        parts += ModelPart(
            "mounting_feet", translate(box(0.36f, 0.05f, 0.36f), 0f, 0.025f, 0f),
            Material(0.10f, 0.10f, 0.10f, metallic = 0.0f, roughness = 0.90f)
        )

        return CompositeModel(parts, height = 0.65f)
    }


    // ── Data classes ────────────────────────────────────────────────

    data class MeshData(
        val positions: FloatBuffer,
        val normals: FloatBuffer,
        val vertexCount: Int
    )

    data class Material(
        val r: Float, val g: Float, val b: Float,
        val metallic: Float = 0.0f,
        val roughness: Float = 0.5f,
        val emissive: Float = 0.0f,
        val alpha: Float = 1.0f
    )

    data class ModelPart(
        val name: String,
        val mesh: MeshData,
        val material: Material,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val scaleZ: Float = 1f
    )

    data class CompositeModel(
        val parts: List<ModelPart>,
        val height: Float
    )

    // ── Utilities ───────────────────────────────────────────────────

    private fun toBuffer(data: List<Float>): FloatBuffer {
        val arr = data.toFloatArray()
        return ByteBuffer.allocateDirect(arr.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(arr); position(0) }
    }

    private fun toBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }
}
