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

    /**
     * Detailed fire extinguisher: ~0.50m tall, 0.15m diameter body.
     * Components: body, valve, handle, lever, hose, nozzle, gauge, label area, base ring.
     */
    fun fireExtinguisher(): CompositeModel {
        val parts = mutableListOf<ModelPart>()

        // Main body — red cylinder
        parts += ModelPart(
            "body", cylinder(0.075f, 0.40f, 32),
            Material(0.78f, 0.12f, 0.10f, metallic = 0.3f, roughness = 0.55f)
        )
        // Body bottom cap — slight dome
        parts += ModelPart(
            "body_base", translate(sphere(0.075f, 8, 16), 0f, 0f, 0f),
            Material(0.78f, 0.12f, 0.10f, metallic = 0.3f, roughness = 0.55f),
            scaleY = 0.3f
        )
        // Label band — white rectangle area on body
        parts += ModelPart(
            "label", translate(box(0.055f, 0.06f, 0.003f), 0f, 0.18f, 0.077f),
            Material(0.92f, 0.92f, 0.90f, metallic = 0.0f, roughness = 0.9f)
        )
        // Pressure gauge — small disc on side
        parts += ModelPart(
            "gauge", translate(cylinder(0.018f, 0.008f, 16), 0.077f, 0.32f, 0f),
            Material(0.90f, 0.90f, 0.88f, metallic = 0.5f, roughness = 0.3f)
        )
        // Gauge face (green)
        parts += ModelPart(
            "gauge_face", translate(disc(0.015f, 0f, 16), 0.085f, 0.324f, 0f),
            Material(0.2f, 0.7f, 0.2f, metallic = 0f, roughness = 0.8f)
        )
        // Valve assembly — black cylinder on top
        parts += ModelPart(
            "valve", translate(cylinder(0.030f, 0.06f, 24), 0f, 0.40f, 0f),
            Material(0.15f, 0.15f, 0.15f, metallic = 0.6f, roughness = 0.35f)
        )
        // Handle — lever on top (interactive target for SQUEEZE)
        parts += ModelPart(
            "handle_base", translate(box(0.035f, 0.008f, 0.015f), 0f, 0.46f, 0f),
            Material(0.12f, 0.12f, 0.12f, metallic = 0.7f, roughness = 0.3f)
        )
        parts += ModelPart(
            "handle_lever", translate(box(0.045f, 0.006f, 0.012f), 0.02f, 0.475f, 0f),
            Material(0.10f, 0.10f, 0.10f, metallic = 0.8f, roughness = 0.25f)
        )
        // Safety pin — yellow ring + shaft (interactive target for PULL)
        parts += ModelPart(
            "pin_shaft", translate(cylinder(0.004f, 0.035f, 12), -0.035f, 0.455f, 0f),
            Material(0.85f, 0.75f, 0.15f, metallic = 0.7f, roughness = 0.3f)
        )
        parts += ModelPart(
            "pin_ring", translate(disc(0.012f, 0.007f, 16), -0.035f, 0.49f, 0f),
            Material(0.85f, 0.75f, 0.15f, metallic = 0.7f, roughness = 0.3f)
        )
        // Hose — thin dark cylinder curving from valve
        parts += ModelPart(
            "hose", translate(cylinder(0.010f, 0.20f, 12), 0.04f, 0.30f, 0f),
            Material(0.08f, 0.08f, 0.08f, metallic = 0.0f, roughness = 0.85f)
        )
        // Nozzle — cone at end of hose (interactive target for AIM)
        parts += ModelPart(
            "nozzle", translate(cone(0.014f, 0.04f, 12), 0.04f, 0.26f, 0f),
            Material(0.12f, 0.12f, 0.12f, metallic = 0.5f, roughness = 0.4f)
        )
        // Base ring — black rubber foot
        parts += ModelPart(
            "base_ring", translate(disc(0.08f, 0.065f, 32), 0f, 0.001f, 0f),
            Material(0.06f, 0.06f, 0.06f, metallic = 0f, roughness = 0.95f)
        )

        return CompositeModel(parts, height = 0.50f)
    }

    /**
     * Emergency exit sign: ISO 7010 green illuminated sign.
     * Mounted at specified height, ~0.30m × 0.15m face.
     */
    fun exitSign(): CompositeModel {
        val parts = mutableListOf<ModelPart>()
        // Main panel — green emissive
        parts += ModelPart(
            "panel", translate(box(0.15f, 0.075f, 0.012f), 0f, 0f, 0f),
            Material(0.05f, 0.55f, 0.20f, metallic = 0f, roughness = 0.6f, emissive = 0.5f)
        )
        // White running figure area
        parts += ModelPart(
            "icon", translate(box(0.04f, 0.05f, 0.001f), -0.03f, 0.005f, 0.013f),
            Material(0.92f, 0.95f, 0.92f, metallic = 0f, roughness = 0.8f, emissive = 0.4f)
        )
        // Arrow indicator
        parts += ModelPart(
            "arrow", translate(box(0.035f, 0.018f, 0.001f), 0.06f, 0f, 0.013f),
            Material(0.92f, 0.95f, 0.92f, metallic = 0f, roughness = 0.8f, emissive = 0.4f)
        )
        // Mounting bracket top
        parts += ModelPart(
            "bracket", translate(box(0.008f, 0.04f, 0.025f), 0f, 0.075f, -0.013f),
            Material(0.7f, 0.7f, 0.7f, metallic = 0.6f, roughness = 0.4f)
        )
        return CompositeModel(parts, height = 0.15f)
    }

    /**
     * Industrial fire alarm pull station: red box with pull handle.
     * ~0.14m × 0.14m × 0.06m.
     */
    fun fireAlarm(): CompositeModel {
        val parts = mutableListOf<ModelPart>()
        // Housing — red metal box
        parts += ModelPart(
            "housing", translate(box(0.065f, 0.070f, 0.030f), 0f, 0f, 0f),
            Material(0.82f, 0.10f, 0.08f, metallic = 0.4f, roughness = 0.5f)
        )
        // Face plate — slightly lighter
        parts += ModelPart(
            "face", translate(box(0.055f, 0.060f, 0.002f), 0f, 0f, 0.031f),
            Material(0.88f, 0.15f, 0.12f, metallic = 0.3f, roughness = 0.55f)
        )
        // Pull handle — white T-bar
        parts += ModelPart(
            "pull_bar", translate(box(0.030f, 0.008f, 0.008f), 0f, -0.025f, 0.039f),
            Material(0.90f, 0.90f, 0.88f, metallic = 0.2f, roughness = 0.6f)
        )
        // Status indicator light — small dome on top
        parts += ModelPart(
            "indicator", translate(sphere(0.010f, 8, 12), 0f, 0.072f, 0.020f),
            Material(0.9f, 0.1f, 0.1f, metallic = 0f, roughness = 0.3f, emissive = 0.0f)
        )
        // Label area — white rectangle
        parts += ModelPart(
            "label", translate(box(0.040f, 0.020f, 0.001f), 0f, 0.020f, 0.033f),
            Material(0.92f, 0.92f, 0.90f, metallic = 0f, roughness = 0.9f)
        )
        return CompositeModel(parts, height = 0.14f)
    }

    /**
     * Hazard zone floor marker: concentric warning rings.
     */
    fun hazardZone(): CompositeModel {
        val parts = mutableListOf<ModelPart>()
        // Outer warning ring — yellow
        parts += ModelPart(
            "outer_ring", disc(0.35f, 0.30f, 48),
            Material(0.90f, 0.70f, 0.05f, metallic = 0f, roughness = 0.9f, alpha = 0.6f)
        )
        // Inner danger zone — red
        parts += ModelPart(
            "inner_zone", disc(0.30f, 0f, 48),
            Material(0.85f, 0.15f, 0.10f, metallic = 0f, roughness = 0.9f, alpha = 0.4f)
        )
        return CompositeModel(parts, height = 0.01f)
    }

    /**
     * Contact shadow: semi-transparent dark ellipse.
     */
    fun contactShadow(radius: Float = 0.12f): MeshData {
        return disc(radius, 0f, 48)
    }

    /**
     * Gas detector: yellow box with display and sensors.
     */
    fun gasDetector(): CompositeModel {
        val parts = mutableListOf<ModelPart>()
        parts += ModelPart(
            "body", translate(box(0.06f, 0.08f, 0.025f), 0f, 0f, 0f),
            Material(0.85f, 0.70f, 0.05f, metallic = 0.2f, roughness = 0.5f)
        )
        parts += ModelPart(
            "display", translate(box(0.04f, 0.03f, 0.002f), 0f, 0.015f, 0.026f),
            Material(0.1f, 0.2f, 0.1f, metallic = 0f, roughness = 0.4f, emissive = 0.3f)
        )
        parts += ModelPart(
            "sensor", translate(cylinder(0.008f, 0.015f, 12), 0f, -0.05f, 0.025f),
            Material(0.7f, 0.7f, 0.7f, metallic = 0.8f, roughness = 0.2f)
        )
        return CompositeModel(parts, height = 0.16f)
    }

    /**
     * Permit clipboard: white rectangle with text lines.
     */
    fun permitClipboard(): CompositeModel {
        val parts = mutableListOf<ModelPart>()
        parts += ModelPart(
            "board", translate(box(0.08f, 0.11f, 0.005f), 0f, 0f, 0f),
            Material(0.92f, 0.90f, 0.85f, metallic = 0f, roughness = 0.9f)
        )
        // Clip at top
        parts += ModelPart(
            "clip", translate(box(0.03f, 0.012f, 0.008f), 0f, 0.105f, 0f),
            Material(0.6f, 0.6f, 0.6f, metallic = 0.7f, roughness = 0.3f)
        )
        // Text lines
        for (i in 0..4) {
            parts += ModelPart(
                "line_$i", translate(box(0.06f, 0.003f, 0.001f), 0f, 0.06f - i * 0.025f, 0.006f),
                Material(0.15f, 0.15f, 0.15f, metallic = 0f, roughness = 1f)
            )
        }
        return CompositeModel(parts, height = 0.22f)
    }

    /**
     * Buddy/attendant figure: simplified human form.
     */
    fun buddyFigure(): CompositeModel {
        val parts = mutableListOf<ModelPart>()
        // Body
        parts += ModelPart(
            "torso", translate(cylinder(0.04f, 0.12f, 16), 0f, 0.06f, 0f),
            Material(0.15f, 0.35f, 0.60f, metallic = 0f, roughness = 0.8f)
        )
        // Head
        parts += ModelPart(
            "head", translate(sphere(0.03f, 10, 14), 0f, 0.21f, 0f),
            Material(0.75f, 0.60f, 0.45f, metallic = 0f, roughness = 0.9f)
        )
        // Hard hat
        parts += ModelPart(
            "helmet", translate(sphere(0.034f, 6, 14), 0f, 0.225f, 0f),
            Material(0.90f, 0.70f, 0.05f, metallic = 0.2f, roughness = 0.5f),
            scaleY = 0.6f
        )
        return CompositeModel(parts, height = 0.26f)
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
