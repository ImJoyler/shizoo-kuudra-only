package shizo.utils.renderUtils.renderUtils

import shizo.Shizo.mc
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import shizo.mixin.accessors.BeaconBeamAccessor
import shizo.events.RenderEvent
import shizo.events.core.on

import shizo.utils.Color
import shizo.utils.Color.Companion.multiplyAlpha
import shizo.utils.addVec
import shizo.utils.renderX
import shizo.utils.renderY
import shizo.utils.renderZ
import kotlin.math.sqrt
import shizo.utils.unaryMinus
import kotlin.collections.iterator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val BEAM_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png")
private const val DEPTH = 0
private const val NO_DEPTH = 1

internal data class LineData(val from: Vec3, val to: Vec3, val color1: Int, val color2: Int, val thickness: Float)
internal data class BoxData(val aabb: AABB, val r: Float, val g: Float, val b: Float, val a: Float, val thickness: Float)
internal data class BeaconData(val pos: BlockPos, val color: Color, val isScoping: Boolean, val gameTime: Long)
internal data class TextData(val text: String, val pos: Vec3, val scale: Float, val depth: Boolean, val cameraRotation: Quaternionf, val font: Font, val textWidth: Float)
internal data class TextureData(val texture: ResourceLocation, val aabb: AABB, val r: Float, val g: Float, val b: Float, val a: Float, val depth: Boolean)
inline val Entity.renderPos: Vec3
    get() = Vec3(renderX, renderY, renderZ)

class RenderConsumer {
    internal val lines = listOf(ObjectArrayList<LineData>(), ObjectArrayList())
    internal val filledBoxes = listOf(ObjectArrayList<BoxData>(), ObjectArrayList())
    internal val wireBoxes = listOf(ObjectArrayList<BoxData>(), ObjectArrayList())

    // surely this wont' break render event
    internal val textures = listOf(ObjectArrayList<TextureData>(), ObjectArrayList())

    internal val beaconBeams = ObjectArrayList<BeaconData>()
    internal val texts = ObjectArrayList<TextData>()

    fun clear() {
        lines.forEach { it.clear() }
        filledBoxes.forEach { it.clear() }
        wireBoxes.forEach { it.clear() }
        beaconBeams.clear()
        texts.clear()
        textures.forEach { it.clear() }
    }
}

object RenderBatchManager {
    val renderConsumer = RenderConsumer()

    init {
        on<RenderEvent.Last> {
            val matrix = context.matrices() ?: return@on
            val bufferSource = context.consumers() as? MultiBufferSource.BufferSource ?: return@on
            val camera = context.gameRenderer().mainCamera?.position ?: return@on

            matrix.pushPose()
            matrix.translate(-camera.x, -camera.y, -camera.z)

            matrix.renderBatchedLinesAndWireBoxes(renderConsumer.lines, renderConsumer.wireBoxes, bufferSource)
            matrix.renderBatchedFilledBoxes(renderConsumer.filledBoxes, bufferSource)

            matrix.popPose()

            matrix.renderBatchedBeaconBeams(renderConsumer.beaconBeams, camera)
            matrix.renderBatchedTexts(renderConsumer.texts, bufferSource, camera)
            renderConsumer.clear()
        }
    }
}


private fun PoseStack.renderBatchedLinesAndWireBoxes(
    lines: List<List<LineData>>,
    wireBoxes: List<List<BoxData>>,
    bufferSource: MultiBufferSource.BufferSource
) {
    val lineRenderLayers = listOf(CustomRenderLayer.LINE_LIST, CustomRenderLayer.LINE_LIST_ESP)
    val last = this.last()
    for (depthState in 0..1) {
        if (lines[depthState].isEmpty() && wireBoxes[depthState].isEmpty()) continue
        val buffer = bufferSource.getBuffer(lineRenderLayers[depthState])

        for (line in lines[depthState]) {
            val dirX = line.to.x - line.from.x
            val dirY = line.to.y - line.from.y
            val dirZ = line.to.z - line.from.z

            PrimitiveRenderer.renderVector(
                last, buffer,
                Vector3f(line.from.x.toFloat(), line.from.y.toFloat(), line.from.z.toFloat()),
                Vec3(dirX, dirY, dirZ),
                line.color1, line.color2
            )
        }

        for (box in wireBoxes[depthState]) {
            PrimitiveRenderer.renderLineBox(
                last, buffer, box.aabb,
                box.r, box.g, box.b, box.a
            )
        }

        bufferSource.endBatch(lineRenderLayers[depthState])
    }
}

private fun PoseStack.renderBatchedFilledBoxes(consumer: List<List<BoxData>>, bufferSource: MultiBufferSource.BufferSource) {
    val filledBoxRenderLayers = listOf(CustomRenderLayer.TRIANGLE_STRIP, CustomRenderLayer.TRIANGLE_STRIP_ESP)
    val last = this.last()
    for ((depthState, boxes) in consumer.withIndex()) {
        if (boxes.isEmpty()) continue
        val buffer = bufferSource.getBuffer(filledBoxRenderLayers[depthState])

        for (box in boxes) {
            PrimitiveRenderer.addChainedFilledBoxVertices(
                last, buffer,
                box.aabb.minX.toFloat(), box.aabb.minY.toFloat(), box.aabb.minZ.toFloat(),
                box.aabb.maxX.toFloat(), box.aabb.maxY.toFloat(), box.aabb.maxZ.toFloat(),
                box.r, box.g, box.b, box.a
            )
        }

        bufferSource.endBatch(filledBoxRenderLayers[depthState])
    }
}
private fun PoseStack.renderBatchedTextures(consumer: List<List<TextureData>>, bufferSource: MultiBufferSource.BufferSource) {
    val last = this.last()

    for (depthState in 0..1) {
        if (consumer[depthState].isEmpty()) continue

        val grouped = consumer[depthState].groupBy { it.texture }

        for ((texture, dataList) in grouped) {
            val buffer = bufferSource.getBuffer(RenderType.entityCutout(texture))

            for (data in dataList) {
                val minX = data.aabb.minX.toFloat()
                val minY = data.aabb.minY.toFloat()
                val minZ = data.aabb.minZ.toFloat()
                val maxX = data.aabb.maxX.toFloat()
                val maxZ = data.aabb.maxZ.toFloat()

                buffer.addVertex(last, minX, minY, minZ).setColor(data.r, data.g, data.b, data.a).setUv(0f, 0f).setOverlay(
                    OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, 1f, 0f)
                buffer.addVertex(last, minX, minY, maxZ).setColor(data.r, data.g, data.b, data.a).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, 1f, 0f)
                buffer.addVertex(last, maxX, minY, maxZ).setColor(data.r, data.g, data.b, data.a).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, 1f, 0f)
                buffer.addVertex(last, maxX, minY, minZ).setColor(data.r, data.g, data.b, data.a).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, 1f, 0f)
            }
        }
    }
}

private fun PoseStack.renderBatchedBeaconBeams(consumer: List<BeaconData>, camera: Vec3) {
    for (beacon in consumer) {
        pushPose()
        translate(beacon.pos.x - camera.x, beacon.pos.y - camera.y, beacon.pos.z - camera.z)

        val centerX = beacon.pos.x + 0.5
        val centerZ = beacon.pos.z + 0.5
        val dx = camera.x - centerX
        val dz = camera.z - centerZ
        val length = sqrt(dx * dx + dz * dz).toFloat()

        val scale = if (beacon.isScoping) 1.0f else maxOf(1.0f, length * 0.010416667f)

        BeaconBeamAccessor.invokeRenderBeam(
            this,
            mc.gameRenderer.featureRenderDispatcher.submitNodeStorage,
            BEAM_TEXTURE,
            1f,
            beacon.gameTime.toFloat(),
            0,
            319,
            beacon.color.rgba,
            0.2f * scale,
            0.25f * scale
        )
        popPose()
    }
}

private fun PoseStack.renderBatchedTexts(consumer: List<TextData>, bufferSource: MultiBufferSource.BufferSource, camera: Vec3) {
    val cameraPos = -camera

    for (textData in consumer) {
        pushPose()
        val pose = last().pose()
        val scaleFactor = textData.scale * 0.025f

        pose.translate(textData.pos.toVector3f())
            .translate(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat())
            .rotate(textData.cameraRotation)
            .scale(scaleFactor, -scaleFactor, scaleFactor)

        textData.font.drawInBatch(
            textData.text, -textData.textWidth / 2f, 0f, -1, true, pose, bufferSource,
            if (textData.depth) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH,
            0, LightTexture.FULL_BRIGHT
        )

        popPose()
    }
}




object PrimitiveRenderer {

    private val edges = intArrayOf(
        0, 1,  1, 5,  5, 4,  4, 0,
        3, 2,  2, 6,  6, 7,  7, 3,
        0, 3,  1, 2,  5, 6,  4, 7
    )

    fun renderLineBox(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        aabb: AABB,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val x0 = aabb.minX.toFloat()
        val y0 = aabb.minY.toFloat()
        val z0 = aabb.minZ.toFloat()
        val x1 = aabb.maxX.toFloat()
        val y1 = aabb.maxY.toFloat()
        val z1 = aabb.maxZ.toFloat()

        val corners = floatArrayOf(
            x0, y0, z0,
            x1, y0, z0,
            x1, y1, z0,
            x0, y1, z0,
            x0, y0, z1,
            x1, y0, z1,
            x1, y1, z1,
            x0, y1, z1
        )

        for (i in edges.indices step 2) {
            val i0 = edges[i] * 3
            val i1 = edges[i + 1] * 3

            val x0 = corners[i0]
            val y0 = corners[i0 + 1]
            val z0 = corners[i0 + 2]
            val x1 = corners[i1]
            val y1 = corners[i1 + 1]
            val z1 = corners[i1 + 2]

            val dx = x1 - x0
            val dy = y1 - y0
            val dz = z1 - z0

            buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setNormal(pose, dx, dy, dz)
            buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz)
        }
    }

    fun addChainedFilledBoxVertices(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val matrix = pose.pose()

        fun vertex(x: Float, y: Float, z: Float) {
            buffer.addVertex(matrix, x, y, z).setColor(r, g, b, a)
        }

        vertex(minX, minY, minZ)
        vertex(minX, minY, minZ)
        vertex(minX, minY, minZ)

        vertex(minX, minY, maxZ)
        vertex(minX, maxY, minZ)
        vertex(minX, maxY, maxZ)

        vertex(minX, maxY, maxZ)

        vertex(minX, minY, maxZ)
        vertex(maxX, maxY, maxZ)
        vertex(maxX, minY, maxZ)

        vertex(maxX, minY, maxZ)

        vertex(maxX, minY, minZ)
        vertex(maxX, maxY, maxZ)
        vertex(maxX, maxY, minZ)

        vertex(maxX, maxY, minZ)

        vertex(maxX, minY, minZ)
        vertex(minX, maxY, minZ)
        vertex(minX, minY, minZ)

        vertex(minX, minY, minZ)

        vertex(maxX, minY, minZ)
        vertex(minX, minY, maxZ)
        vertex(maxX, minY, maxZ)

        vertex(maxX, minY, maxZ)

        vertex(minX, maxY, minZ)
        vertex(minX, maxY, minZ)
        vertex(minX, maxY, maxZ)
        vertex(maxX, maxY, minZ)
        vertex(maxX, maxY, maxZ)

        vertex(maxX, maxY, maxZ)
        vertex(maxX, maxY, maxZ)
    }

    fun renderVector(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        start: Vector3f,
        direction: Vec3,
        startColor: Int,
        endColor: Int
    ) {
        val endX = start.x() + direction.x.toFloat()
        val endY = start.y() + direction.y.toFloat()
        val endZ = start.z() + direction.z.toFloat()

        val nx = direction.x.toFloat()
        val ny = direction.y.toFloat()
        val nz = direction.z.toFloat()

        buffer.addVertex(pose, start.x(), start.y(), start.z())
            .setColor(startColor)
            .setNormal(pose, nx, ny, nz)

        buffer.addVertex(pose, endX, endY, endZ)
            .setColor(endColor)
            .setNormal(pose, nx, ny, nz)
    }
}


fun RenderEvent.Extract.drawWireFrameBox(aabb: AABB, color: Color, thickness: Float = 3f, depth: Boolean = false) {
    consumer.wireBoxes[if (depth) DEPTH else NO_DEPTH].add(
        BoxData(aabb, color.redFloat, color.greenFloat, color.blueFloat, color.alphaFloat, thickness)
    )
}

fun RenderEvent.Extract.drawFilledBox(aabb: AABB, color: Color, depth: Boolean = false) {
    consumer.filledBoxes[if (depth) DEPTH else NO_DEPTH].add(
        BoxData(aabb, color.redFloat, color.greenFloat, color.blueFloat, color.alphaFloat, 3f)
    )
}

fun RenderEvent.Extract.drawStyledBox(
    aabb: AABB,
    color: Color,
    style: Int = 0,
    depth: Boolean = true
) {
    when (style) {
        0 -> drawFilledBox(aabb, color, depth = depth)
        1 -> drawWireFrameBox(aabb, color, depth = depth)
        2 -> {
            drawFilledBox(aabb, color.multiplyAlpha(0.5f), depth = depth)
            drawWireFrameBox(aabb, color, depth = depth)
        }
    }
}


fun RenderEvent.Extract.drawTracer(to: Vec3, color: Color, depth: Boolean, thickness: Float = 3f) {
    val from = mc.player?.let { player ->
        player.renderPos.add(player.forward.add(0.0, player.eyeHeight.toDouble(), 0.0))
    } ?: return
    drawLine(listOf(from, to), color, depth, thickness)
}

fun RenderEvent.Extract.drawLine(points: Collection<Vec3>, color: Color, depth: Boolean, thickness: Float = 3f) {
    drawLine(points, color, color, depth, thickness)
}

fun RenderEvent.Extract.drawLine(points: Collection<Vec3>, color1: Color, color2: Color, depth: Boolean, thickness: Float = 3f) {
    if (points.size < 2) return

    val rgba1 = color1.rgba
    val rgba2 = color2.rgba
    val batch = consumer.lines[if (depth) DEPTH else NO_DEPTH]

    val iterator = points.iterator()
    var current = iterator.next()

    while (iterator.hasNext()) {
        val next = iterator.next()
        batch.add(LineData(current, next, rgba1, rgba2, thickness))
        current = next
    }
}
fun RenderEvent.Extract.drawText(text: String, pos: Vec3, scale: Float, depth: Boolean) {
    val cameraRotation = mc.gameRenderer.mainCamera.rotation()
    val font = mc.font ?: return
    val textWidth = font.width(text).toFloat()

    consumer.texts.add(TextData(text, pos, scale, depth, cameraRotation, font, textWidth))
}

fun RenderEvent.Extract.drawCylinder(
    center: Vec3,
    radius: Float,
    height: Float,
    color: Color,
    segments: Int = 32,
    thickness: Float = 5f,
    depth: Boolean = false
) {
    val batch = consumer.lines[if (depth) DEPTH else NO_DEPTH]
    val angleStep = 2.0 * Math.PI / segments
    val rgba = color.rgba

    for (i in 0 until segments) {
        val angle1 = i * angleStep
        val angle2 = (i + 1) * angleStep

        val x1 = (radius * cos(angle1)).toFloat()
        val z1 = (radius * sin(angle1)).toFloat()
        val x2 = (radius * cos(angle2)).toFloat()
        val z2 = (radius * sin(angle2)).toFloat()

        val p1Top = center.add(x1.toDouble(), height.toDouble(), z1.toDouble())
        val p2Top = center.add(x2.toDouble(), height.toDouble(), z2.toDouble())
        val p1Bottom = center.add(x1.toDouble(), 0.0, z1.toDouble())
        val p2Bottom = center.add(x2.toDouble(), 0.0, z2.toDouble())

        batch.add(LineData(p1Top, p2Top, rgba, rgba, thickness))
        batch.add(LineData(p1Bottom, p2Bottom, rgba, rgba, thickness))
        batch.add(LineData(p1Bottom, p1Top, rgba, rgba, thickness))
    }
}


fun RenderEvent.Extract.drawBeaconBeam(position: BlockPos, color: Color) {
    val isScoping = mc.player?.isScoping == true
    val gameTime = mc.level?.gameTime ?: 0L

    consumer.beaconBeams.add(BeaconData(position, color, isScoping, gameTime))
}


fun RenderEvent.Extract.drawCustomBeacon(
    title: String,
    position: BlockPos,
    color: Color,
    increase: Boolean = true,
    distance: Boolean = true
) {
    val dist = mc.player?.blockPosition()?.distManhattan(position) ?: return

    drawWireFrameBox(AABB(position), color, depth = false)
    drawBeaconBeam(position, color)
    drawText(
        (if (distance) ("$title §r§f(§3${dist}m§f)") else title),
        position.center.addVec(y = 1.7),
        if (increase) max(1f, dist * 0.05f) else 2f,
        false
    )
}

fun RenderEvent.Extract.drawFlatTexture(
    texture: ResourceLocation,
    aabb: AABB,
    color: Color = Color(255f, 255f, 255f, 255f), //ikdk why Color.WHITE wans't working=?
    depth: Boolean = false
) {
    consumer.textures[if (depth) DEPTH else NO_DEPTH].add(
        TextureData(texture, aabb, color.redFloat, color.greenFloat, color.blueFloat, color.alphaFloat, depth)
    )
}

fun draw2DImage(
    graphics: GuiGraphics,
    texture: ResourceLocation,
    x: Int,
    y: Int,
    width: Int,
    height: Int
) {
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        texture,
        x,
        y,
        0f,                           // uOffset
        0f,                           // vOffset
        width,
        height,
        width,
        height,
        width,
        height,
        -1
    )
}
fun RenderEvent.Extract.drawCyberpunkScanner(aabb: AABB, color: Color, lineThickness: Float) {
    val baseAabb = AABB(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.minY + 0.05, aabb.maxZ)
    drawFilledBox(baseAabb, Color(color.red, color.green, color.blue, 50f), depth = false)

    drawWireFrameBox(aabb, color, lineThickness, depth = false)

    val time = (System.currentTimeMillis() % 2000) / 2000.0
    val sineOffset = (sin(time * Math.PI * 2) * 0.5) + 0.5

    val heightRange = aabb.maxY - aabb.minY
    val scanY = aabb.minY + (heightRange * sineOffset)

    val scannerAabb = AABB(aabb.minX, scanY, aabb.minZ, aabb.maxX, scanY + 0.02, aabb.maxZ)
    drawFilledBox(scannerAabb, Color(color.red, color.green, color.blue, 200f), depth = false)
}
// i need to fix this but not rn ngl
fun RenderEvent.Extract.drawIsraelRing(aabb: AABB, depth: Boolean = false) {
    val alpha = 200f
    val blue = Color(0, 56, 184, alpha)
    val white = Color(255, 255, 255, alpha)

    val y0 = aabb.minY + 0.002
    val y1 = y0 + 0.05

    val stripeW = (aabb.maxZ - aabb.minZ) * 0.15

    drawFilledBox(AABB(aabb.minX, y0, aabb.minZ, aabb.maxX, y1, aabb.minZ + stripeW), blue, depth)

    drawFilledBox(AABB(aabb.minX, y0, aabb.minZ + stripeW, aabb.maxX, y1, aabb.maxZ - stripeW), white, depth)

    drawFilledBox(AABB(aabb.minX, y0, aabb.maxZ - stripeW, aabb.maxX, y1, aabb.maxZ), blue, depth)

    val cx = (aabb.minX + aabb.maxX) / 2.0
    val cz = (aabb.minZ + aabb.maxZ) / 2.0

    val ly = y1 + 0.02

    val r = min(aabb.maxX - aabb.minX, aabb.maxZ - aabb.minZ) * 0.3
    val cos30 = r * 0.866
    val sin30 = r * 0.5

    val t1p1 = Vec3(cx, ly, cz - r)
    val t1p2 = Vec3(cx - cos30, ly, cz + sin30)
    val t1p3 = Vec3(cx + cos30, ly, cz + sin30)
    drawLine(listOf(t1p1, t1p2, t1p3, t1p1), blue, depth, 3f)

    val t2p1 = Vec3(cx, ly, cz + r)
    val t2p2 = Vec3(cx - cos30, ly, cz - sin30)
    val t2p3 = Vec3(cx + cos30, ly, cz - sin30)
    drawLine(listOf(t2p1, t2p2, t2p3, t2p1), blue, depth, 3f)
}

// testing random shit here
// defo not fps rapey

fun RenderEvent.Extract.drawAstralOphanim(aabb: AABB, baseColor: Color, depth: Boolean = false) {
    val t = (System.currentTimeMillis() % 100000) / 1000.0

    val cx = (aabb.minX + aabb.maxX) / 2.0
    val cy = aabb.minY
    val cz = (aabb.minZ + aabb.maxZ) / 2.0

    val radius = max(aabb.maxX - aabb.minX, aabb.maxZ - aabb.minZ) * 0.6
    val height = aabb.maxY - aabb.minY

    fun rotate3D(x: Double, y: Double, z: Double, pitch: Double, yaw: Double, roll: Double): Vec3 {
        val y1 = y * cos(pitch) - z * sin(pitch)
        val z1 = y * sin(pitch) + z * cos(pitch)
        val x2 = x * cos(yaw) + z1 * sin(yaw)
        val z2 = -x * sin(yaw) + z1 * cos(yaw)
        val x3 = x2 * cos(roll) - y1 * sin(roll)
        val y3 = x2 * sin(roll) + y1 * cos(roll)
        return Vec3(cx + x3, cy + (height / 2.0) + y3, cz + z2)
    }

    val glowColor = baseColor.multiplyAlpha(0.8f)
    val coreColor = Color(255, 255, 255, (baseColor.alpha * 0.9f))


    val segments = 24
    val outerCircle = mutableListOf<Vec3>()
    val innerStar = mutableListOf<Vec3>()

    for (i in 0..segments) {
        val angle1 = (i.toDouble() / segments) * 2 * Math.PI + (t * 1.5)
        outerCircle.add(Vec3(cx + cos(angle1) * radius, cy + 0.02, cz + sin(angle1) * radius))

        val angle2 = (i.toDouble() / segments) * 2 * Math.PI * 3 - (t * 2.0) // * 3 creates a star/spiky pattern
        innerStar.add(Vec3(cx + cos(angle2) * (radius * 0.7), cy + 0.05, cz + sin(angle2) * (radius * 0.7)))
    }
    drawLine(outerCircle, glowColor, depth, 3f)
    drawLine(innerStar, baseColor, depth, 2f)


    val ringSegments = 30
    val ring1 = mutableListOf<Vec3>()
    val ring2 = mutableListOf<Vec3>()
    val ring3 = mutableListOf<Vec3>()

    val orbitRadius = radius * 0.85

    for (i in 0..ringSegments) {
        val angle = (i.toDouble() / ringSegments) * 2 * Math.PI
        val px = cos(angle) * orbitRadius
        val py = sin(angle) * orbitRadius

        ring1.add(rotate3D(px, py, 0.0, Math.PI / 4, t * 2.5, 0.0))
        ring2.add(rotate3D(px, py, 0.0, -Math.PI / 4, -t * 1.8, 0.0))
        ring3.add(rotate3D(px, 0.0, py, t * 1.2, t * 0.8, t * 2.1))
    }
    drawLine(ring1, glowColor, depth, 2f)
    drawLine(ring2, glowColor, depth, 2f)
    drawLine(ring3, baseColor, depth, 1.5f)


    val helixSegments = 40
    val helix1 = mutableListOf<Vec3>()
    val helix2 = mutableListOf<Vec3>()
    val helixRadius = radius * 0.3

    for (i in 0..helixSegments) {
        val hProgress = i.toDouble() / helixSegments
        val hY = cy + (hProgress * height)
        val hAngle = (hProgress * Math.PI * 4) + (t * 4.0)

        helix1.add(Vec3(cx + cos(hAngle) * helixRadius, hY, cz + sin(hAngle) * helixRadius))
        helix2.add(Vec3(cx + cos(hAngle + Math.PI) * helixRadius, hY, cz + sin(hAngle + Math.PI) * helixRadius)) // Offset by 180 degrees
    }
    drawLine(helix1, baseColor.multiplyAlpha(0.6f), depth, 2f)
    drawLine(helix2, baseColor.multiplyAlpha(0.6f), depth, 2f)

    val coreBob = sin(t * 3.0) * 0.1
    val coreY = cy + (height / 2.0) + coreBob
    val cr = radius * (0.15 + sin(t * 5.0) * 0.05)

    val top = rotate3D(0.0, cr, 0.0, 0.0, t * 3.0, 0.0).add(0.0, coreBob, 0.0)
    val bottom = rotate3D(0.0, -cr, 0.0, 0.0, t * 3.0, 0.0).add(0.0, coreBob, 0.0)
    val north = rotate3D(0.0, 0.0, -cr, 0.0, t * 3.0, 0.0).add(0.0, coreBob, 0.0)
    val south = rotate3D(0.0, 0.0, cr, 0.0, t * 3.0, 0.0).add(0.0, coreBob, 0.0)
    val east = rotate3D(cr, 0.0, 0.0, 0.0, t * 3.0, 0.0).add(0.0, coreBob, 0.0)
    val west = rotate3D(-cr, 0.0, 0.0, 0.0, t * 3.0, 0.0).add(0.0, coreBob, 0.0)

    drawLine(listOf(top, north, east, top, south, west, top, north), coreColor, depth, 3f)
    drawLine(listOf(east, south), coreColor, depth, 3f)
    drawLine(listOf(west, north), coreColor, depth, 3f)

    drawLine(listOf(bottom, north, east, bottom, south, west, bottom, north), coreColor, depth, 3f)
}