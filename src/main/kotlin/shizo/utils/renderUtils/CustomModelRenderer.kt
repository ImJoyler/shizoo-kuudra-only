package shizo.utils.renderUtils

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object CustomModelRenderer {

    data class ResolvedEntry(
        val path: String,
        val scale: Float = 1.0f,
        val rotationY: Float = 0f,
        val isSelf: Boolean = false
    )

    val ALL_MODEL_PATHS = mapOf(
        "Tung Tung Sahur" to "/assets/shizo/tts.glb",
    )

    private val MODEL_DEFAULTS = mapOf(
        "Tung Tung Sahur" to ResolvedEntry("/assets/shizo/tts.glb", 1.0f, 0f),
    )

    fun getModelDefaultScale(modelName: String): Float = MODEL_DEFAULTS[modelName]?.scale ?: 1.0f
    fun getModelDefaultRotationY(modelName: String): Float = MODEL_DEFAULTS[modelName]?.rotationY ?: 0f

    data class PlayerModelEntry(
        val model: String,
        val scale: Float = 1.0f,
        val rotationY: Float = 0f
    )

    private val playerModelsFile = File("config/shizo/player_models.json")
    private var playerModels = mutableMapOf<String, PlayerModelEntry>()
    private val gson = GsonBuilder().setPrettyPrinting().create()


    fun init() {
        loadPlayerModels()
    }

    fun loadPlayerModels() {
        if (!playerModelsFile.exists()) {
            playerModels = mutableMapOf()
            return
        }
        try {
            val json = JsonParser.parseString(playerModelsFile.readText()).asJsonObject
            playerModels.clear()
            for ((key, value) in json.entrySet()) {
                val obj = value.asJsonObject
                val model = obj.get("model")?.asString ?: continue
                playerModels[key.lowercase()] = PlayerModelEntry(
                    model = model,
                    scale = obj.get("scale")?.asFloat ?: 1.0f,
                    rotationY = obj.get("rotationY")?.asFloat ?: 0f
                )
            }
        } catch (e: Exception) {
            System.err.println("[Shizo] Failed to load player_models.json: ${e.message}")
            playerModels = mutableMapOf()
        }
    }

    fun savePlayerModels() {
        try {
            playerModelsFile.parentFile?.mkdirs()
            val json = JsonObject()
            for ((name, entry) in playerModels) {
                val obj = JsonObject()
                obj.addProperty("model", entry.model)
                obj.addProperty("scale", entry.scale)
                obj.addProperty("rotationY", entry.rotationY)
                json.add(name, obj)
            }
            playerModelsFile.writeText(gson.toJson(json))
        } catch (e: Exception) {
            System.err.println("[Shizo] Failed to save player_models.json: ${e.message}")
        }
    }

    fun setPlayerModel(playerName: String, model: String, scale: Float = 1.0f, rotationY: Float = 0f) {
        playerModels[playerName.lowercase()] = PlayerModelEntry(model, scale, rotationY)
        savePlayerModels()
    }

    fun removePlayerModel(playerName: String): Boolean {
        val removed = playerModels.remove(playerName.lowercase()) != null
        if (removed) savePlayerModels()
        return removed
    }

    fun getPlayerModels(): Map<String, PlayerModelEntry> = playerModels.toMap()

    private val loadedModels = HashMap<String, ModelData>()

    private class MeshData(
        val positions: FloatArray,
        val normals: FloatArray?,
        val uvs: FloatArray?,
        val indices: IntArray,
        val materialIndex: Int
    )

    private class NodeInfo(
        val meshIndex: Int?,
        val children: IntArray,
        val translation: FloatArray,
        val rotation: FloatArray,
        val scale: FloatArray,
        val matrix: FloatArray?
    )

    private class AnimChannel(
        val nodeIndex: Int,
        val path: String,
        val timestamps: FloatArray,
        val values: FloatArray
    )

    private class GlbAnimation(
        val name: String,
        val channels: List<AnimChannel>
    )

    private class AnimatedTRS {
        var translation: FloatArray? = null
        var rotation: Quaternionf? = null
        var scale: FloatArray? = null
    }

    private class ModelData(
        val meshes: List<List<MeshData>>,
        val nodes: List<NodeInfo>,
        val sceneRoots: IntArray,
        val idleAnimation: GlbAnimation?,
        val walkAnimation: GlbAnimation?,
        val hitAnimation: GlbAnimation?, //peak ily Flufflygamer3342
        val allAnimations: List<GlbAnimation>,
        val yFloorOffset: Float,
        val facingOffset: Float,
        val textures: List<ResourceLocation>
    )

// FUCK ME IN THE ASS
    fun submit(
        modelPath: String,
        poseStack: PoseStack,
        collector: MultiBufferSource,
        bodyRot: Float,
        lightCoords: Int,
        walkAnimSpeed: Float,
        walkAnimPos: Float,
        hitAnimProgress: Float = 0f,
        scale: Float = 1.0f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        offsetZ: Float = 0f,
        rotationX: Float = 0f,
        rotationY: Float = 0f,
        rotationZ: Float = 0f
    ) {
        if (!loadedModels.containsKey(modelPath)) loadGlb(modelPath)
        val data = loadedModels[modelPath] ?: return
        if (data.textures.isEmpty()) return

        poseStack.pushPose()

        val totalYaw = 180f - bodyRot - data.facingOffset + rotationY
        poseStack.mulPose(Axis.YP.rotationDegrees(totalYaw))

        poseStack.scale(scale, scale, scale)
        poseStack.translate(
            offsetX.toDouble(),
            (data.yFloorOffset + offsetY).toDouble(),
            offsetZ.toDouble()
        )

        poseStack.mulPose(Axis.XP.rotationDegrees(rotationX))
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotationZ))

        val animOverrides = HashMap<Int, AnimatedTRS>()
        val currentTime = (System.nanoTime() / 1_000_000_000.0).toFloat()

        data.idleAnimation?.let { anim ->
            for (channel in anim.channels) {
                val duration = channel.timestamps.last()
                if (duration > 0f) {
                    val time = currentTime % duration
                    val override = animOverrides.getOrPut(channel.nodeIndex) { AnimatedTRS() }
                    applyChannel(override, channel, time)
                }
            }
        }

    data.walkAnimation?.let { anim ->
        val blend = (walkAnimSpeed * 4f).coerceIn(0f, 1f)

        if (blend > 0.001f) {
            for (channel in anim.channels) {
                val duration = channel.timestamps.last()
                if (duration > 0f) {

                    // reminder to change this for speed
                    val time = (currentTime * 1.0f) % duration

                    val node = data.nodes[channel.nodeIndex]
                    val override = animOverrides.getOrPut(channel.nodeIndex) { AnimatedTRS() }
                    applyChannelBlended(override, channel, node, time, blend)
                }
            }
        }
    }
        data.hitAnimation?.let { anim ->
            if (hitAnimProgress > 0f) {
                val blend = if (hitAnimProgress < 0.1f) hitAnimProgress / 0.1f
                else if (hitAnimProgress > 0.9f) (1f - hitAnimProgress) / 0.1f
                else 1f

                for (channel in anim.channels) {
                    val duration = channel.timestamps.last()
                    if (duration > 0f) {
                        val time = hitAnimProgress * duration
                        val node = data.nodes[channel.nodeIndex]
                        val override = animOverrides.getOrPut(channel.nodeIndex) { AnimatedTRS() }
                        applyChannelBlended(override, channel, node, time, blend)
                    }
                }
            }
        }

        for (rootIdx in data.sceneRoots) {
            renderNodeTree(poseStack, collector, data, rootIdx, animOverrides, lightCoords)
        }

        poseStack.popPose()
    }

    private fun renderNodeTree(
        poseStack: PoseStack, collector: net.minecraft.client.renderer.MultiBufferSource,
        data: ModelData,
        nodeIdx: Int, animOverrides: Map<Int, AnimatedTRS>, light: Int
    ) {
        val node = data.nodes[nodeIdx]
        poseStack.pushPose()

        val anim = animOverrides[nodeIdx]
        if (anim != null) {
            val t = anim.translation ?: node.translation
            val r = anim.rotation ?: Quaternionf(node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3])
            val sc = anim.scale ?: node.scale
            if (t[0] != 0f || t[1] != 0f || t[2] != 0f)
                poseStack.translate(t[0].toDouble(), t[1].toDouble(), t[2].toDouble())
            poseStack.mulPose(r)
            if (sc[0] != 1f || sc[1] != 1f || sc[2] != 1f)
                poseStack.scale(sc[0], sc[1], sc[2])
        } else if (node.matrix != null) {
            applyMatrix(poseStack, node.matrix)
        } else {
            val t = node.translation
            if (t[0] != 0f || t[1] != 0f || t[2] != 0f)
                poseStack.translate(t[0].toDouble(), t[1].toDouble(), t[2].toDouble())
            if (node.rotation[0] != 0f || node.rotation[1] != 0f || node.rotation[2] != 0f || node.rotation[3] != 1f)
                poseStack.mulPose(Quaternionf(node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3]))
            val sc = node.scale
            if (sc[0] != 1f || sc[1] != 1f || sc[2] != 1f)
                poseStack.scale(sc[0], sc[1], sc[2])
        }

        node.meshIndex?.let { meshIdx ->
            if (meshIdx < data.meshes.size) {
                for (prim in data.meshes[meshIdx]) {
                    val texLoc = data.textures.getOrElse(prim.materialIndex) { data.textures[0] }
                    val renderType = RenderType.entityCutoutNoCullZOffset(texLoc)

                    val buffer = collector.getBuffer(renderType)

                    emitMesh(buffer, poseStack.last(), prim, light)
                }
            }
        }

        for (childIdx in node.children) {
            if (childIdx < data.nodes.size) {
                renderNodeTree(poseStack, collector, data, childIdx, animOverrides, light)
            }
        }

        poseStack.popPose()
    }

    private fun emitMesh(buffer: VertexConsumer, pose: PoseStack.Pose, mesh: MeshData, light: Int) {
        val indices = mesh.indices
        var i = 0
        while (i + 2 < indices.size) {
            emitVertex(buffer, pose, mesh, indices[i], light)
            emitVertex(buffer, pose, mesh, indices[i + 1], light)
            emitVertex(buffer, pose, mesh, indices[i + 2], light)
            emitVertex(buffer, pose, mesh, indices[i + 2], light)
            i += 3
        }
    }

    private fun emitVertex(
        buffer: VertexConsumer, pose: PoseStack.Pose,
        mesh: MeshData, idx: Int, light: Int
    ) {
        val px = mesh.positions[idx * 3]
        val py = mesh.positions[idx * 3 + 1]
        val pz = mesh.positions[idx * 3 + 2]

        val u = mesh.uvs?.get(idx * 2) ?: 0f
        val v = mesh.uvs?.get(idx * 2 + 1) ?: 0f

        val nx = mesh.normals?.get(idx * 3) ?: 0f
        val ny = mesh.normals?.get(idx * 3 + 1) ?: 1f
        val nz = mesh.normals?.get(idx * 3 + 2) ?: 0f

        buffer.addVertex(pose, px, py, pz)
            .setColor(1f, 1f, 1f, 1f)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz)
    }

    private fun applyMatrix(poseStack: PoseStack, m: FloatArray) {
        poseStack.last().pose().mul(
            Matrix4f(
                m[0], m[1], m[2], m[3],
                m[4], m[5], m[6], m[7],
                m[8], m[9], m[10], m[11],
                m[12], m[13], m[14], m[15]
            )
        )
        poseStack.last().normal().mul(
            Matrix3f(
                m[0], m[1], m[2],
                m[4], m[5], m[6],
                m[8], m[9], m[10]
            )
        )
    }

    private val IDENTITY_MAT = floatArrayOf(
        1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f
    )

    private fun computeModelMinY(data: ModelData): Float {
        var minY = Float.MAX_VALUE
        for (rootIdx in data.sceneRoots) {
            minY = minOf(minY, computeNodeMinY(data, rootIdx, IDENTITY_MAT))
        }
        return if (minY == Float.MAX_VALUE) 0f else minY
    }

    private fun computeNodeMinY(data: ModelData, nodeIdx: Int, parentTransform: FloatArray): Float {
        if (nodeIdx >= data.nodes.size) return Float.MAX_VALUE
        val node = data.nodes[nodeIdx]
        val local = nodeToMatrix(node)
        val accumulated = mat4Multiply(parentTransform, local)

        var minY = Float.MAX_VALUE

        node.meshIndex?.let { meshIdx ->
            if (meshIdx < data.meshes.size) {
                for (prim in data.meshes[meshIdx]) {
                    for (i in 0 until prim.positions.size / 3) {
                        val x = prim.positions[i * 3]
                        val y = prim.positions[i * 3 + 1]
                        val z = prim.positions[i * 3 + 2]
                        val worldY = accumulated[1] * x + accumulated[5] * y + accumulated[9] * z + accumulated[13]
                        minY = minOf(minY, worldY)
                    }
                }
            }
        }

        for (childIdx in node.children) {
            minY = minOf(minY, computeNodeMinY(data, childIdx, accumulated))
        }
        return minY
    }

    private fun nodeToMatrix(node: NodeInfo): FloatArray {
        if (node.matrix != null) return node.matrix

        val t = node.translation
        val r = node.rotation
        val s = node.scale

        if (t === DEFAULT_TRANSLATION && r === DEFAULT_ROTATION && s === DEFAULT_SCALE) return IDENTITY_MAT

        val x = r[0]; val y = r[1]; val z = r[2]; val w = r[3]
        val x2 = x * 2; val y2 = y * 2; val z2 = z * 2
        val xx = x * x2; val yy = y * y2; val zz = z * z2
        val xy = x * y2; val xz = x * z2; val yz = y * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2

        return floatArrayOf(
            (1f - yy - zz) * s[0], (xy + wz) * s[0], (xz - wy) * s[0], 0f,
            (xy - wz) * s[1], (1f - xx - zz) * s[1], (yz + wx) * s[1], 0f,
            (xz + wy) * s[2], (yz - wx) * s[2], (1f - xx - yy) * s[2], 0f,
            t[0], t[1], t[2], 1f
        )
    }

    private fun mat4Multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += a[k * 4 + row] * b[col * 4 + k]
                }
                r[col * 4 + row] = sum
            }
        }
        return r
    }

    private fun computeFacingOffset(data: ModelData): Float {
        var sumX = 0f
        var sumZ = 0f
        var meshCount = 0

        fun walk(nodeIdx: Int, parentMat: FloatArray) {
            if (nodeIdx >= data.nodes.size) return
            val node = data.nodes[nodeIdx]
            val localMat = nodeToMatrix(node)
            val worldMat = mat4Multiply(parentMat, localMat)

            if (node.meshIndex != null) {
                sumX += worldMat[8]
                sumZ += worldMat[10]
                meshCount++
            }

            for (childIdx in node.children) {
                walk(childIdx, worldMat)
            }
        }

        for (rootIdx in data.sceneRoots) {
            walk(rootIdx, IDENTITY_MAT)
        }

        if (meshCount == 0) return 0f
        return Math.toDegrees(Math.atan2((sumX / meshCount).toDouble(), (sumZ / meshCount).toDouble())).toFloat()
    }

    private fun applyChannel(override: AnimatedTRS, channel: AnimChannel, time: Float) {
        when (channel.path) {
            "rotation" -> override.rotation = interpolateQuat(channel, time)
            "translation" -> override.translation = interpolateVec3(channel, time)
            "scale" -> override.scale = interpolateVec3(channel, time)
        }
    }

    private fun applyChannelBlended(
        override: AnimatedTRS, channel: AnimChannel, node: NodeInfo,
        time: Float, blend: Float
    ) {
        when (channel.path) {
            "rotation" -> {
                val animQ = interpolateQuat(channel, time)
                val restQ = Quaternionf(node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3])
                restQ.slerp(animQ, blend)
                override.rotation = restQ
            }
            "translation" -> {
                val animT = interpolateVec3(channel, time)
                val restT = node.translation
                override.translation = floatArrayOf(
                    restT[0] + (animT[0] - restT[0]) * blend,
                    restT[1] + (animT[1] - restT[1]) * blend,
                    restT[2] + (animT[2] - restT[2]) * blend
                )
            }
            "scale" -> {
                val animS = interpolateVec3(channel, time)
                val restS = node.scale
                override.scale = floatArrayOf(
                    restS[0] + (animS[0] - restS[0]) * blend,
                    restS[1] + (animS[1] - restS[1]) * blend,
                    restS[2] + (animS[2] - restS[2]) * blend
                )
            }
        }
    }

    private fun interpolateQuat(channel: AnimChannel, time: Float): Quaternionf {
        val ts = channel.timestamps
        val vals = channel.values

        if (ts.size <= 1 || time <= ts[0]) {
            return Quaternionf(vals[0], vals[1], vals[2], vals[3])
        }

        val last = ts.size - 1
        if (time >= ts[last]) {
            return Quaternionf(vals[last * 4], vals[last * 4 + 1], vals[last * 4 + 2], vals[last * 4 + 3])
        }

        var k = 0
        while (k < last && ts[k + 1] < time) k++
        val t = (time - ts[k]) / (ts[k + 1] - ts[k])

        val q0 = Quaternionf(vals[k * 4], vals[k * 4 + 1], vals[k * 4 + 2], vals[k * 4 + 3])
        val q1 = Quaternionf(vals[(k + 1) * 4], vals[(k + 1) * 4 + 1], vals[(k + 1) * 4 + 2], vals[(k + 1) * 4 + 3])
        q0.slerp(q1, t)
        return q0
    }

    private fun interpolateVec3(channel: AnimChannel, time: Float): FloatArray {
        val ts = channel.timestamps
        val vals = channel.values

        if (ts.size <= 1 || time <= ts[0]) {
            return floatArrayOf(vals[0], vals[1], vals[2])
        }

        val last = ts.size - 1
        if (time >= ts[last]) {
            return floatArrayOf(vals[last * 3], vals[last * 3 + 1], vals[last * 3 + 2])
        }

        var k = 0
        while (k < last && ts[k + 1] < time) k++
        val t = (time - ts[k]) / (ts[k + 1] - ts[k])

        return floatArrayOf(
            vals[k * 3] + (vals[(k + 1) * 3] - vals[k * 3]) * t,
            vals[k * 3 + 1] + (vals[(k + 1) * 3 + 1] - vals[k * 3 + 1]) * t,
            vals[k * 3 + 2] + (vals[(k + 1) * 3 + 2] - vals[k * 3 + 2]) * t
        )
    }

    private fun loadGlb(path: String) {
        try {
            val stream = javaClass.getResourceAsStream(path) ?: run {
                System.err.println("[Shizo] Could not find $path")
                return
            }
            val bytes = stream.readAllBytes()
            stream.close()
            parseGlb(bytes, path)
        } catch (e: Exception) {
            System.err.println("[Shizo] Failed to load GLB model ($path):")
            e.printStackTrace()
        }
    }

    private val DEFAULT_TRANSLATION = floatArrayOf(0f, 0f, 0f)
    private val DEFAULT_ROTATION = floatArrayOf(0f, 0f, 0f, 1f)
    private val DEFAULT_SCALE = floatArrayOf(1f, 1f, 1f)

    private fun parseGlb(bytes: ByteArray, modelPath: String) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = buf.int
        if (magic != 0x46546C67) {
            System.err.println("[Shizo] Invalid GLB magic: $magic")
            return
        }
        val version = buf.int
        val totalLength = buf.int

        val jsonLength = buf.int
        val jsonType = buf.int
        val jsonBytes = ByteArray(jsonLength)
        buf.get(jsonBytes)
        val jsonStr = String(jsonBytes, Charsets.UTF_8)

        if (!buf.hasRemaining() || buf.remaining() < 8) {
            System.err.println("[Shizo] GLB has no binary chunk")
            return
        }
        val binLength = buf.int
        val binType = buf.int
        val binData = ByteArray(binLength)
        buf.get(binData)

        val root = JsonParser.parseString(jsonStr).asJsonObject
        val meshesJson = root.getAsJsonArray("meshes") ?: return
        val accessors = root.getAsJsonArray("accessors") ?: return
        val bufferViews = root.getAsJsonArray("bufferViews") ?: return

        val nodesJson = root.getAsJsonArray("nodes")
        val nodeList = mutableListOf<NodeInfo>()
        if (nodesJson != null) {
            for (nodeEl in nodesJson) {
                val node = nodeEl.asJsonObject
                val meshIdx = if (node.has("mesh")) node.get("mesh").asInt else null
                val children = if (node.has("children")) {
                    val arr = node.getAsJsonArray("children")
                    IntArray(arr.size()) { arr[it].asInt }
                } else IntArray(0)

                val hasMatrix = node.has("matrix")
                val matrix = if (hasMatrix) {
                    val arr = node.getAsJsonArray("matrix")
                    FloatArray(16) { arr[it].asFloat }
                } else null

                val translation = if (node.has("translation")) {
                    val arr = node.getAsJsonArray("translation")
                    floatArrayOf(arr[0].asFloat, arr[1].asFloat, arr[2].asFloat)
                } else DEFAULT_TRANSLATION

                val rotation = if (node.has("rotation")) {
                    val arr = node.getAsJsonArray("rotation")
                    floatArrayOf(arr[0].asFloat, arr[1].asFloat, arr[2].asFloat, arr[3].asFloat)
                } else DEFAULT_ROTATION

                val scale = if (node.has("scale")) {
                    val arr = node.getAsJsonArray("scale")
                    floatArrayOf(arr[0].asFloat, arr[1].asFloat, arr[2].asFloat)
                } else DEFAULT_SCALE

                nodeList.add(NodeInfo(meshIdx, children, translation, rotation, scale, matrix))
            }
        }

        val scenesJson = root.getAsJsonArray("scenes")
        val sceneRoots = if (scenesJson != null && scenesJson.size() > 0) {
            val sceneNodes = scenesJson[0].asJsonObject.getAsJsonArray("nodes")
            if (sceneNodes != null) IntArray(sceneNodes.size()) { sceneNodes[it].asInt }
            else IntArray(0)
        } else IntArray(0)

        val meshList = mutableListOf<List<MeshData>>()
        for (meshEl in meshesJson) {
            val mesh = meshEl.asJsonObject
            val primitives = mesh.getAsJsonArray("primitives") ?: continue

            val primList = mutableListOf<MeshData>()
            for (primEl in primitives) {
                val prim = primEl.asJsonObject
                val attrs = prim.getAsJsonObject("attributes") ?: continue
                if (!attrs.has("POSITION")) continue

                val matIdx = prim.get("material")?.asInt ?: 0

                val posAccessor = accessors[attrs.get("POSITION").asInt].asJsonObject
                val positions = readFloatAccessor(posAccessor, bufferViews, binData)

                val normals = if (attrs.has("NORMAL")) {
                    val normAccessor = accessors[attrs.get("NORMAL").asInt].asJsonObject
                    readFloatAccessor(normAccessor, bufferViews, binData)
                } else {
                    FloatArray(positions.size / 3 * 3) { i -> if (i % 3 == 1) 1f else 0f }
                }

                val uvs = if (attrs.has("TEXCOORD_0")) {
                    val uvAccessor = accessors[attrs.get("TEXCOORD_0").asInt].asJsonObject
                    readFloatAccessor(uvAccessor, bufferViews, binData)
                } else {
                    FloatArray(positions.size / 3 * 2)
                }

                val indices = if (prim.has("indices")) {
                    val idxAccessor = accessors[prim.get("indices").asInt].asJsonObject
                    readIndexAccessor(idxAccessor, bufferViews, binData)
                } else {
                    val count = posAccessor.get("count").asInt
                    IntArray(count) { it }
                }

                primList.add(MeshData(positions, normals, uvs, indices, matIdx))
            }
            meshList.add(primList)
        }

        val animsJson = root.getAsJsonArray("animations")
        var idleAnim: GlbAnimation? = null
        var walkAnim: GlbAnimation? = null
        var hitAnim: GlbAnimation? = null
        val allAnims = mutableListOf<GlbAnimation>()

        if (animsJson != null) {
            for (animEl in animsJson) {
                val anim = animEl.asJsonObject
                val name = anim.get("name")?.asString ?: ""
                val channelsJson = anim.getAsJsonArray("channels") ?: continue
                val samplersJson = anim.getAsJsonArray("samplers") ?: continue

                val channels = mutableListOf<AnimChannel>()

                for (channelEl in channelsJson) {
                    val ch = channelEl.asJsonObject
                    val target = ch.getAsJsonObject("target") ?: continue
                    val path = target.get("path")?.asString ?: continue
                    if (path != "rotation" && path != "translation" && path != "scale") continue
                    val nodeIndex = target.get("node")?.asInt ?: continue
                    val samplerIdx = ch.get("sampler").asInt
                    val sampler = samplersJson[samplerIdx].asJsonObject

                    val inputAccessor = accessors[sampler.get("input").asInt].asJsonObject
                    val outputAccessor = accessors[sampler.get("output").asInt].asJsonObject

                    val timestamps = readFloatAccessor(inputAccessor, bufferViews, binData)
                    val values = readFloatAccessor(outputAccessor, bufferViews, binData)

                    channels.add(AnimChannel(nodeIndex, path, timestamps, values))
                }

                val glbAnim = GlbAnimation(name, channels)
                allAnims.add(glbAnim)
                val lower = name.lowercase()
                when {
                    "idle" in lower || "slow" in lower || "default" in lower -> idleAnim = glbAnim
                    "walk" in lower || "run" in lower -> walkAnim = glbAnim
                    "hit" in lower || "attack" in lower || "swing" in lower -> hitAnim = glbAnim
                }
            }
        }
        // we just stand still idfc
//        if (idleAnim == null && allAnims.isNotEmpty()) {
//            idleAnim = allAnims[0]
//        }

        val texId = modelPath.substringAfterLast("/").substringBeforeLast(".").lowercase()
        val textureLocations = loadAllTextures(root, bufferViews, binData, texId)

        val tempData = ModelData(meshList, nodeList, sceneRoots, idleAnim, walkAnim, hitAnim, allAnims, 0f, 0f, textureLocations)
        val minY = computeModelMinY(tempData)
        val facingOffset = computeFacingOffset(tempData)
        loadedModels[modelPath] = ModelData(meshList, nodeList, sceneRoots, idleAnim, walkAnim, hitAnim, allAnims, -minY, facingOffset, textureLocations)

        val vertCount = meshList.sumOf { prims -> prims.sumOf { it.positions.size / 3 } }
        val triCount = meshList.sumOf { prims -> prims.sumOf { it.indices.size / 3 } }
        println("[Shizo] Loaded GLB '$modelPath': $vertCount verts, $triCount tris, ${nodeList.size} nodes, ${allAnims.size} animations (${allAnims.map { it.name }}), ${textureLocations.size} textures")
    }

    private fun loadAllTextures(root: JsonObject, bufferViews: JsonArray, binData: ByteArray, texId: String): List<ResourceLocation> {
        try {
            val imagesJson = root.getAsJsonArray("images")
            if (imagesJson == null || imagesJson.size() == 0) {
                return listOf(createFallbackTexture(texId, 0))
            }

            val imageLocations = mutableListOf<ResourceLocation>()
            for (i in 0 until imagesJson.size()) {
                val image = imagesJson[i].asJsonObject
                if (image.has("bufferView")) {
                    val bvIdx = image.get("bufferView").asInt
                    val bv = bufferViews[bvIdx].asJsonObject
                    val offset = bv.get("byteOffset")?.asInt ?: 0
                    val length = bv.get("byteLength").asInt
                    val imageData = binData.sliceArray(offset until offset + length)

                    val nativeImage = NativeImage.read(ByteArrayInputStream(imageData))
                    val dynamicTexture = DynamicTexture({ "shizo_glb_${texId}_img$i" }, nativeImage)
                    val loc = ResourceLocation.fromNamespaceAndPath("shizo", "glb_tex_${texId}_img$i")
                    Minecraft.getInstance().textureManager.register(loc, dynamicTexture)
                    imageLocations.add(loc)
                } else {
                    imageLocations.add(createFallbackTexture(texId, i))
                }
            }

            val materialsJson = root.getAsJsonArray("materials")
            val texturesJson = root.getAsJsonArray("textures")
            if (materialsJson == null || texturesJson == null) {
                return if (imageLocations.isNotEmpty()) imageLocations else listOf(createFallbackTexture(texId, 0))
            }

            val materialTextures = mutableListOf<ResourceLocation>()
            for (i in 0 until materialsJson.size()) {
                val mat = materialsJson[i].asJsonObject
                val pbr = mat.getAsJsonObject("pbrMetallicRoughness")
                if (pbr != null && pbr.has("baseColorTexture")) {
                    val texIdx = pbr.getAsJsonObject("baseColorTexture").get("index").asInt
                    if (texIdx < texturesJson.size()) {
                        val source = texturesJson[texIdx].asJsonObject.get("source").asInt
                        if (source < imageLocations.size) {
                            materialTextures.add(imageLocations[source])
                            continue
                        }
                    }
                }
                materialTextures.add(if (imageLocations.isNotEmpty()) imageLocations[0] else createFallbackTexture(texId, i))
            }

            return if (materialTextures.isNotEmpty()) materialTextures else imageLocations.ifEmpty { listOf(createFallbackTexture(texId, 0)) }
        } catch (e: Exception) {
            System.err.println("[Shizo] Failed to load GLB textures: ${e.message}")
            return listOf(createFallbackTexture(texId, 0))
        }
    }

    private fun createFallbackTexture(texId: String, index: Int): ResourceLocation {
        val img = NativeImage(2, 2, true)
        img.setPixel(0, 0, -1); img.setPixel(1, 0, -1)
        img.setPixel(0, 1, -1); img.setPixel(1, 1, -1)
        val tex = DynamicTexture({ "shizo_glb_fallback_${texId}_$index" }, img)
        val loc = ResourceLocation.fromNamespaceAndPath("shizo", "glb_tex_fallback_${texId}_$index")
        Minecraft.getInstance().textureManager.register(loc, tex)
        return loc
    }

    private fun readFloatAccessor(
        accessor: JsonObject, bufferViews: JsonArray, bin: ByteArray
    ): FloatArray {
        val bvIdx = accessor.get("bufferView").asInt
        val bv = bufferViews[bvIdx].asJsonObject
        val count = accessor.get("count").asInt
        val components = when (accessor.get("type").asString) {
            "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; else -> 1
        }
        val stride = bv.get("byteStride")?.asInt ?: (components * 4)
        val accessorOffset = accessor.get("byteOffset")?.asInt ?: 0
        val bvOffset = bv.get("byteOffset")?.asInt ?: 0

        val result = FloatArray(count * components)
        for (i in 0 until count) {
            val elemStart = bvOffset + accessorOffset + i * stride
            val fb = ByteBuffer.wrap(bin, elemStart, components * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
            for (c in 0 until components) {
                result[i * components + c] = fb.float
            }
        }
        return result
    }

    private fun readIndexAccessor(
        accessor: JsonObject, bufferViews: JsonArray, bin: ByteArray
    ): IntArray {
        val bvIdx = accessor.get("bufferView").asInt
        val bv = bufferViews[bvIdx].asJsonObject
        val byteOffset = (bv.get("byteOffset")?.asInt ?: 0) +
                (accessor.get("byteOffset")?.asInt ?: 0)
        val count = accessor.get("count").asInt
        val componentType = accessor.get("componentType").asInt

        val result = IntArray(count)
        val b = ByteBuffer.wrap(bin, byteOffset, bin.size - byteOffset)
            .order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until count) {
            result[i] = when (componentType) {
                5121 -> b.get().toInt() and 0xFF
                5123 -> b.short.toInt() and 0xFFFF
                5125 -> b.int
                else -> b.short.toInt() and 0xFFFF
            }
        }
        return result
    }
}