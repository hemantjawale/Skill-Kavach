package com.example.skilkavach.ar

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Native glTF 2.0 Binary (GLB) asset loader for ARCore OpenGL rendering.
 * Converts GLB assets from app assets into [IndustrialMeshes.CompositeModel].
 *
 * Implements seamless fallback to procedural [IndustrialMeshes] if a GLB model
 * cannot be loaded or parsed.
 */
object GlbLoader {

    private const val TAG = "GlbLoader"
    private const val GLB_MAGIC = 0x46546C67 // "glTF"
    private const val CHUNK_JSON = 0x4E4F534A // "JSON"
    private const val CHUNK_BIN  = 0x00414E49 // "BIN\0"

    /**
     * Loads a GLB asset by asset path (e.g., "models/fire_extinguisher.glb").
     * Returns procedural [fallbackModel] if GLB loading fails for any reason.
     */
    fun loadModelWithFallback(
        context: Context,
        assetPath: String,
        fallbackModel: () -> IndustrialMeshes.CompositeModel
    ): IndustrialMeshes.CompositeModel {
        return try {
            val model = loadGlbFromAssets(context, assetPath)
            if (model != null && model.parts.isNotEmpty()) {
                Log.i(TAG, "Successfully loaded GLB model: $assetPath (${model.parts.size} parts)")
                model
            } else {
                Log.w(TAG, "GLB model $assetPath returned empty parts, using procedural fallback.")
                fallbackModel()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load GLB asset $assetPath: ${e.message}. Using procedural fallback.")
            fallbackModel()
        }
    }

    private fun loadGlbFromAssets(context: Context, assetPath: String): IndustrialMeshes.CompositeModel? {
        val inputStream: InputStream = context.assets.open(assetPath)
        val bytes = inputStream.use { it.readBytes() }
        if (bytes.size < 20) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = buffer.int
        val version = buffer.int
        val length = buffer.int

        if (magic != GLB_MAGIC || version != 2 || length > bytes.size) {
            Log.e(TAG, "Invalid GLB header in $assetPath: magic=0x${Integer.toHexString(magic)}, ver=$version")
            return null
        }

        // Chunk 0: JSON
        val chunk0Len = buffer.int
        val chunk0Type = buffer.int
        if (chunk0Type != CHUNK_JSON) {
            Log.e(TAG, "Chunk 0 is not JSON in $assetPath")
            return null
        }
        val jsonBytes = ByteArray(chunk0Len)
        buffer.get(jsonBytes)

        val jsonStr = String(jsonBytes, Charsets.UTF_8).trim()
        val jsonObj = JSONObject(jsonStr)

        // Chunk 1: BIN
        var binBytes = ByteArray(0)
        if (buffer.remaining() >= 8) {
            val chunk1Len = buffer.int
            val chunk1Type = buffer.int
            if (chunk1Type == CHUNK_BIN && buffer.remaining() >= chunk1Len) {
                binBytes = ByteArray(chunk1Len)
                buffer.get(binBytes)
            }
        }

        val binBuffer = ByteBuffer.wrap(binBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Parse JSON structures
        val jsonMaterials = jsonObj.optJSONArray("materials")
        val jsonAccessors = jsonObj.optJSONArray("accessors") ?: return null
        val jsonBufferViews = jsonObj.optJSONArray("bufferViews") ?: return null
        val jsonMeshes = jsonObj.optJSONArray("meshes") ?: return null

        // Parse Materials
        val materialsList = mutableListOf<IndustrialMeshes.Material>()
        if (jsonMaterials != null) {
            for (i in 0 until jsonMaterials.length()) {
                val matObj = jsonMaterials.getJSONObject(i)
                val pbr = matObj.optJSONObject("pbrMetallicRoughness")
                var r = 0.8f; var g = 0.8f; var b = 0.8f; var a = 1.0f
                var metallic = 0.5f; var roughness = 0.5f
                var er = 0f; var eg = 0f; var eb = 0f

                if (pbr != null) {
                    val colorArr = pbr.optJSONArray("baseColorFactor")
                    if (colorArr != null && colorArr.length() >= 4) {
                        r = colorArr.getDouble(0).toFloat()
                        g = colorArr.getDouble(1).toFloat()
                        b = colorArr.getDouble(2).toFloat()
                        a = colorArr.getDouble(3).toFloat()
                    }
                    metallic = pbr.optDouble("metallicFactor", 0.5).toFloat()
                    roughness = pbr.optDouble("roughnessFactor", 0.5).toFloat()
                }

                val emissiveArr = matObj.optJSONArray("emissiveFactor")
                if (emissiveArr != null && emissiveArr.length() >= 3) {
                    er = emissiveArr.getDouble(0).toFloat()
                    eg = emissiveArr.getDouble(1).toFloat()
                    eb = emissiveArr.getDouble(2).toFloat()
                }

                val maxEmissive = maxOf(er, maxOf(eg, eb))

                materialsList.add(
                    IndustrialMeshes.Material(
                        r = r, g = g, b = b,
                        metallic = metallic, roughness = roughness,
                        emissive = maxEmissive, alpha = a
                    )
                )
            }
        }

        val modelParts = mutableListOf<IndustrialMeshes.ModelPart>()
        var maxHeight = 0.2f

        for (m in 0 until jsonMeshes.length()) {
            val meshObj = jsonMeshes.getJSONObject(m)
            val meshName = meshObj.optString("name", "Part_$m")
            val primitives = meshObj.optJSONArray("primitives") ?: continue

            for (p in 0 until primitives.length()) {
                val prim = primitives.getJSONObject(p)
                val matIdx = prim.optInt("material", 0)
                val material = materialsList.getOrNull(matIdx) ?: IndustrialMeshes.Material(0.8f, 0.8f, 0.8f)

                val attrs = prim.getJSONObject("attributes")
                val posAccIdx = attrs.getInt("POSITION")
                val normAccIdx = attrs.optInt("NORMAL", -1)
                val indAccIdx = prim.getInt("indices")

                // Extract Positions
                val posAcc = jsonAccessors.getJSONObject(posAccIdx)
                val posBv = jsonBufferViews.getJSONObject(posAcc.getInt("bufferView"))
                val posOffset = posBv.optInt("byteOffset", 0) + posAcc.optInt("byteOffset", 0)
                val posCount = posAcc.getInt("count")

                val posFloatBuffer = FloatBuffer.allocate(posCount * 3)
                binBuffer.position(posOffset)
                for (i in 0 until posCount * 3) {
                    val valF = binBuffer.float
                    posFloatBuffer.put(valF)
                    if (i % 3 == 1 && valF > maxHeight) {
                        maxHeight = valF
                    }
                }
                posFloatBuffer.position(0)

                // Extract Normals
                val normFloatBuffer = FloatBuffer.allocate(posCount * 3)
                if (normAccIdx >= 0) {
                    val normAcc = jsonAccessors.getJSONObject(normAccIdx)
                    val normBv = jsonBufferViews.getJSONObject(normAcc.getInt("bufferView"))
                    val normOffset = normBv.optInt("byteOffset", 0) + normAcc.optInt("byteOffset", 0)
                    binBuffer.position(normOffset)
                    for (i in 0 until posCount * 3) {
                        normFloatBuffer.put(binBuffer.float)
                    }
                } else {
                    for (i in 0 until posCount) {
                        normFloatBuffer.put(0f).put(1f).put(0f)
                    }
                }
                normFloatBuffer.position(0)

                // Extract Indices
                val indAcc = jsonAccessors.getJSONObject(indAccIdx)
                val indBv = jsonBufferViews.getJSONObject(indAcc.getInt("bufferView"))
                val indOffset = indBv.optInt("byteOffset", 0) + indAcc.optInt("byteOffset", 0)
                val indCount = indAcc.getInt("count")
                val compType = indAcc.getInt("componentType")

                val indexList = mutableListOf<Int>()
                binBuffer.position(indOffset)
                for (i in 0 until indCount) {
                    val idxVal = if (compType == 5123) { // UNSIGNED_SHORT
                        binBuffer.short.toInt() and 0xFFFF
                    } else {
                        binBuffer.int
                    }
                    indexList.add(idxVal)
                }

                // Reindex vertices according to indices for non-indexed GLES draw
                val reindexedVerts = FloatBuffer.allocate(indCount * 3)
                val reindexedNorms = FloatBuffer.allocate(indCount * 3)

                for (idx in indexList) {
                    reindexedVerts.put(posFloatBuffer.get(idx * 3))
                    reindexedVerts.put(posFloatBuffer.get(idx * 3 + 1))
                    reindexedVerts.put(posFloatBuffer.get(idx * 3 + 2))

                    reindexedNorms.put(normFloatBuffer.get(idx * 3))
                    reindexedNorms.put(normFloatBuffer.get(idx * 3 + 1))
                    reindexedNorms.put(normFloatBuffer.get(idx * 3 + 2))
                }

                reindexedVerts.position(0)
                reindexedNorms.position(0)

                val meshData = IndustrialMeshes.MeshData(
                    positions = reindexedVerts,
                    normals = reindexedNorms,
                    vertexCount = indCount
                )

                modelParts.add(
                    IndustrialMeshes.ModelPart(
                        name = meshName,
                        mesh = meshData,
                        material = material
                    )
                )
            }
        }

        return IndustrialMeshes.CompositeModel(modelParts, height = maxHeight)
    }
}
