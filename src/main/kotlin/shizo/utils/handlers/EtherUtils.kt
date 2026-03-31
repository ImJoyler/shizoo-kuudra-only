package shizo.utils.handlers

// idk
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shizo.Shizo.mc
import java.util.BitSet
import kotlin.math.*

object EtherUtils {
    const val STAND_EYE_HEIGHT = 1.6200000047683716
    const val SNEAK_EYE_HEIGHT = 1.5399999618530273 // Change to 1.27d when update to 1.21.10
    const val SNEAK_HEIGHT_INVERTED = 0.0800000429153443
    const val DEGREES_TO_RADIAN = Math.PI / 180.0
    const val EPSILON = 0.001

    private val validTypes: Set<Class<out Block>> = setOf(
        // fuck 1.21.10 idfk which blocks im misisng??
        ButtonBlock::class.java, CarpetBlock::class.java, SkullBlock::class.java,
        WallSkullBlock::class.java, LadderBlock::class.java, SaplingBlock::class.java,
        FlowerBlock::class.java, StemBlock::class.java, CropBlock::class.java,
        RailBlock::class.java, SnowLayerBlock::class.java, BubbleColumnBlock::class.java,
        TripWireBlock::class.java, TripWireHookBlock::class.java, FireBlock::class.java,
        AirBlock::class.java, TorchBlock::class.java, FlowerPotBlock::class.java,
        TallFlowerBlock::class.java, TallGrassBlock::class.java, BushBlock::class.java,
        SeagrassBlock::class.java, TallSeagrassBlock::class.java, SugarCaneBlock::class.java,
        LiquidBlock::class.java, VineBlock::class.java, MushroomBlock::class.java,
        PistonHeadBlock::class.java, WebBlock::class.java,
        NetherWartBlock::class.java, NetherPortalBlock::class.java, RedStoneWireBlock::class.java,
        ComparatorBlock::class.java, RedstoneTorchBlock::class.java, RepeaterBlock::class.java,
        GlowLichenBlock::class.java,
        SculkVeinBlock::class.java,
        HangingRootsBlock::class.java,
        SporeBlossomBlock::class.java,
        AmethystClusterBlock::class.java,
        PointedDripstoneBlock::class.java,
        BasePressurePlateBlock::class.java,
        SignBlock::class.java,
        WallSignBlock::class.java,
        LeverBlock::class.java
    )
    private const val STEPS = 100.0

    private val IGNORED: Set<Class<out Block>> = setOf(
        AirBlock::class.java, FireBlock::class.java, LiquidBlock::class.java, CarpetBlock::class.java,
        MushroomBlock::class.java, NetherWartBlock::class.java, NetherPortalBlock::class.java,
        RedStoneWireBlock::class.java, ComparatorBlock::class.java, RedstoneTorchBlock::class.java,
        RepeaterBlock::class.java, TripWireBlock::class.java, ButtonBlock::class.java, RailBlock::class.java,
        BubbleColumnBlock::class.java, SaplingBlock::class.java
    )

    private val IGNORED2: Set<Class<out Block>> = setOf(
        SlabBlock::class.java
    )

    private val SPECIAL: Set<Class<out Block>> = setOf(
        LadderBlock::class.java,
        VineBlock::class.java,
        WaterlilyBlock::class.java
    )

    private val IGNORED_BLOCKS_CLASSES: Set<Class<out Block>> = setOf(
        ButtonBlock::class.java, AirBlock::class.java, CarpetBlock::class.java, RedStoneWireBlock::class.java, MushroomBlock::class.java,
        FlowerBlock::class.java, StemBlock::class.java, CropBlock::class.java, TripWireBlock::class.java, RailBlock::class.java,
        GlowLichenBlock::class.java,
        SculkVeinBlock::class.java,
        HangingRootsBlock::class.java,
        SporeBlossomBlock::class.java,
        AmethystClusterBlock::class.java,
        PointedDripstoneBlock::class.java,
        BasePressurePlateBlock::class.java,
        SignBlock::class.java,
        WallSignBlock::class.java,
        LeverBlock::class.java
    )

    private val IGNORED_BLOCKS: List<Block> = listOf(
        Blocks.LAVA,
        Blocks.WATER
    )

    private val SPECIAL_BLOCKS: Set<Class<out Block>> = setOf(
        LadderBlock::class.java, VineBlock::class.java, WaterlilyBlock::class.java
    )

    private val validEtherwarpFeetIds = BitSet(0)
    private val ignored = BitSet(0)
    private val ignored2 = BitSet(0)
    private val special = BitSet(0)

    init {
        initIDs()
    }

    private fun initIDs() {
        BuiltInRegistries.BLOCK.forEach { block ->
            for (type in validTypes) {
                if (type.isInstance(block)) {
                    validEtherwarpFeetIds.set(Block.getId(block.defaultBlockState()))
                    break
                }
            }
        }
    }

    fun getYawAndPitch(dx: Double, dy: Double, dz: Double): FloatArray {
        val horizontalDistance = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz))
        val pitch = -Math.toDegrees(atan2(dy, horizontalDistance))
        val normalizedYaw = if (yaw < -180) yaw + 360 else yaw
        return floatArrayOf(normalizedYaw.toFloat(), pitch.toFloat())
    }

    fun getYawAndPitch(pos: Vec3, sneaking: Boolean, playerSP: net.minecraft.client.player.LocalPlayer, doY: Boolean): FloatArray {
        val dx = pos.x - playerSP.x
        val dy = if (!doY) 0.0 else pos.y - (playerSP.y + 1.62f - (if (sneaking) SNEAK_HEIGHT_INVERTED else 0.0))
        val dz = pos.z - playerSP.z
        return getYawAndPitch(dx, dy, dz)
    }

    fun fastGetEtherFromOrigin(start: Vec3, yaw: Float, pitch: Float, dist: Int): BlockPos? {
        val player = mc.player ?: return null
        val world = mc.level ?: return null

        val end = Vec3.directionFromRotation(pitch, yaw).scale(dist.toDouble()).add(start)
        val direction = end.subtract(start)

        val step = IntArray(3)
        for (i in 0..2) {
            step[i] = sign(getCoord(direction, i)).toInt()
        }

        val invDirection = DoubleArray(3)
        for (i in 0..2) {
            val d = getCoord(direction, i)
            invDirection[i] = if (d != 0.0) 1.0 / d else Double.MAX_VALUE
        }

        val tDelta = DoubleArray(3)
        for (i in 0..2) {
            tDelta[i] = invDirection[i] * step[i]
        }

        val currentPos = IntArray(3)
        val endPos = IntArray(3)
        for (i in 0..2) {
            currentPos[i] = floor(getCoord(start, i)).toInt()
            endPos[i] = floor(getCoord(end, i)).toInt()
        }

        val tMax = DoubleArray(3)
        for (i in 0..2) {
            val startCoord = getCoord(start, i)
            tMax[i] = abs((floor(startCoord) + max(step[i], 0) - startCoord) * invDirection[i])
        }

        val pos = BlockPos.MutableBlockPos()
        for (i in 0 until 1000) {
            pos.set(currentPos[0], currentPos[1], currentPos[2])

            if (!world.hasChunk(pos.x shr 4, pos.z shr 4)) return null
            val chunk = world.getChunk(pos)

            val currentBlock = chunk.getBlockState(pos).block
            val currentBlockId = Block.getId(currentBlock.defaultBlockState())

            if (!validEtherwarpFeetIds.get(currentBlockId)) {
                val footBlockId = Block.getId(
                    chunk.getBlockState(BlockPos(pos.x, pos.y + 1, pos.z)).block.defaultBlockState()
                )
                if (!validEtherwarpFeetIds.get(footBlockId)) return null

                val headBlockId = Block.getId(
                    chunk.getBlockState(BlockPos(pos.x, pos.y + 2, pos.z)).block.defaultBlockState()
                )
                if (!validEtherwarpFeetIds.get(headBlockId)) return null

                return pos
            }

            if (currentPos[0] == endPos[0] && currentPos[1] == endPos[1] && currentPos[2] == endPos[2]) {
                return null
            }

            val minIndex: Int
            if (tMax[0] <= tMax[1]) {
                minIndex = if (tMax[0] <= tMax[2]) 0 else 2
            } else {
                minIndex = if (tMax[1] <= tMax[2]) 1 else 2
            }

            tMax[minIndex] += tDelta[minIndex]
            currentPos[minIndex] += step[minIndex]
        }
        return null
    }

    fun getEtherPosFromOrigin(origin: Vec3, yaw: Float, pitch: Float, dist: Int): Pair<BlockPos?, Boolean> {
        val player = mc.player ?: return Pair(null, false)
        val endPos = Vec3.directionFromRotation(pitch, yaw).scale(dist.toDouble()).add(origin)
        return traverseVoxels(origin, endPos)
    }

    fun getEtherPosFromOrigin(origin: Vec3, distance: Int): Pair<BlockPos?, Boolean> {
        val player = mc.player ?: return Pair(null, false)
        val endPos = player.lookAngle.scale(distance.toDouble()).add(origin)
        return traverseVoxels(origin, endPos)
    }

    private fun getCoord(vec: Vec3, i: Int): Double {
        return when (i) {
            0 -> vec.x
            1 -> vec.y
            2 -> vec.z
            else -> 0.0
        }
    }

    private fun traverseVoxels(start: Vec3, end: Vec3): Pair<BlockPos?, Boolean> {
        val world = mc.level ?: return Pair(null, false)
        val direction = end.subtract(start)

        val step = IntArray(3)
        for (i in 0..2) {
            step[i] = sign(getCoord(direction, i)).toInt()
        }

        val invDirection = DoubleArray(3)
        for (i in 0..2) {
            val d = getCoord(direction, i)
            invDirection[i] = if (d != 0.0) 1.0 / d else Double.MAX_VALUE
        }

        val tDelta = DoubleArray(3)
        for (i in 0..2) {
            tDelta[i] = invDirection[i] * step[i]
        }

        val currentPos = IntArray(3)
        val endPos = IntArray(3)
        for (i in 0..2) {
            currentPos[i] = floor(getCoord(start, i)).toInt()
            endPos[i] = floor(getCoord(end, i)).toInt()
        }

        val tMax = DoubleArray(3)
        for (i in 0..2) {
            val startCoord = getCoord(start, i)
            tMax[i] = abs((floor(startCoord) + max(step[i], 0) - startCoord) * invDirection[i])
        }

        for (i in 0 until 1000) {
            val pos = BlockPos(currentPos[0], currentPos[1], currentPos[2])

            if (!world.hasChunk(pos.x shr 4, pos.z shr 4)) return Pair(null, false)
            val chunk = world.getChunk(pos)

            val currentBlock = chunk.getBlockState(pos).block
            val currentBlockId = Block.getId(currentBlock.defaultBlockState())

            if (!validEtherwarpFeetIds.get(currentBlockId)) {
                val footBlockId = Block.getId(
                    chunk.getBlockState(BlockPos(pos.x, pos.y + 1, pos.z)).block.defaultBlockState()
                )
                if (!validEtherwarpFeetIds.get(footBlockId)) return Pair(pos, false)

                val headBlockId = Block.getId(
                    chunk.getBlockState(BlockPos(pos.x, pos.y + 2, pos.z)).block.defaultBlockState()
                )
                if (!validEtherwarpFeetIds.get(headBlockId)) return Pair(pos, false)

                return Pair(pos, true)
            }

            if (currentPos.contentEquals(endPos)) {
                return Pair(null, false)
            }

            val minIndex: Int
            if (tMax[0] <= tMax[1]) {
                minIndex = if (tMax[0] <= tMax[2]) 0 else 2
            } else {
                minIndex = if (tMax[1] <= tMax[2]) 1 else 2
            }

            tMax[minIndex] += tDelta[minIndex]
            currentPos[minIndex] += step[minIndex]
        }

        return Pair(null, false)
    }

    private fun getBlockId(pos: BlockPos, chunk: ChunkAccess): Int {
        return Block.getId(chunk.getBlockState(pos).block.defaultBlockState())
    }

    fun isValidEtherwarpPosition(pos: BlockPos): Boolean {
        val world = mc.level ?: return false
        val chunk = world.getChunk(pos)

        if (validEtherwarpFeetIds.get(getBlockId(pos, chunk))) return false
        if (!validEtherwarpFeetIds.get(getBlockId(pos.above(1), chunk))) return false
        if (!validEtherwarpFeetIds.get(getBlockId(pos.above(2), chunk))) return false
        return true
    }

    fun rayTraceBlock(maxDistance: Int, yaw: Float, pitch: Float, playerEyePos: Vec3): Vec3? {
        val roundedYaw = round(yaw.toDouble(), 14) * DEGREES_TO_RADIAN
        val roundedPitch = round(pitch.toDouble(), 14) * DEGREES_TO_RADIAN

        val cosPitch = cos(roundedPitch)
        val dx = -cosPitch * sin(roundedYaw)
        val dy = -sin(roundedPitch)
        val dz = cosPitch * cos(roundedYaw)

        var x = floor(playerEyePos.x).toInt()
        var y = floor(playerEyePos.y).toInt()
        var z = floor(playerEyePos.z).toInt()

        val stepX = if (dx < 0) -1 else 1
        val stepY = if (dy < 0) -1 else 1
        val stepZ = if (dz < 0) -1 else 1

        val tDeltaX = abs(1.0 / dx)
        val tDeltaY = abs(1.0 / dy)
        val tDeltaZ = abs(1.0 / dz)

        var tMaxX = (if (dx < 0) playerEyePos.x - x else x + 1 - playerEyePos.x) * tDeltaX
        var tMaxY = (if (dy < 0) playerEyePos.y - y else y + 1 - playerEyePos.y) * tDeltaY
        var tMaxZ = (if (dz < 0) playerEyePos.z - z else z + 1 - playerEyePos.z) * tDeltaZ

        if (!isAir(BlockPos(x, y, z))) {
            return Vec3(playerEyePos.x, playerEyePos.y, playerEyePos.z)
        }

        var i = 0
        while (i < maxDistance) {
            i++

            val c = min(tMaxX, min(tMaxY, tMaxZ))

            val hitX = round((playerEyePos.x + dx * c) * 1e10) * 1e-10
            val hitY = round((playerEyePos.y + dy * c) * 1e10) * 1e-10
            val hitZ = round((playerEyePos.z + dz * c) * 1e10) * 1e-10

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX
                tMaxX += tDeltaX
            } else if (tMaxY < tMaxZ) {
                y += stepY
                tMaxY += tDeltaY
            } else {
                z += stepZ
                tMaxZ += tDeltaZ
            }

            if (!isAir(BlockPos(x, y, z))) {
                return Vec3(hitX, hitY, hitZ)
            }
        }

        return null
    }

    private fun isAir(pos: BlockPos): Boolean {
        val world = mc.level ?: return true
        if (!world.hasChunk(pos.x shr 4, pos.z shr 4)) return true
        val block = world.getBlockState(pos).block
        val currentBlockId = Block.getId(block.defaultBlockState())
        return validEtherwarpFeetIds.get(currentBlockId)
    }

    private fun round(value: Double, places: Int): Double {
        val scale = 10.0.pow(places)
        return round(value * scale) / scale
    }

    fun predictTeleport(distance: Int, start: Vec3, yaw: Float, pitch: Float): Vec3? {
        val forward = Vec3.directionFromRotation(pitch, yaw).scale(1.0 / STEPS)
        val player = start.add(0.0, STAND_EYE_HEIGHT, 0.0)
        var cur = player
        var i = 0

        while (true) {
            if (i < distance * STEPS) {
                if (i % STEPS == 0.0 && !isSpecial(cur) && !isSpecial(cur) && !isIgnored(cur)) {
                    cur = cur.add(forward.scale(-STEPS))
                    return if (i != 0 && isIgnored(cur)) Vec3(floor(cur.x) + 0.5, floor(cur.y), floor(cur.z) + 0.5) else null
                }

                if ((isIgnored2(cur) || !inBB(cur)) && (isIgnored2(cur.add(0.0, 1.0, 0.0)) || !inBB(cur.add(0.0, 1.0, 0.0)))) {
                    cur = cur.add(forward)
                    ++i
                    continue
                }

                cur = cur.add(forward.scale(-STEPS))
                if (i == 0 || !isIgnored(cur) && inBB(cur) || !isIgnored(cur.add(0.0, 1.0, 0.0)) && inBB(cur.add(0.0, 1.0, 0.0))) {
                    return null
                }
            }

            val pos = player.add(Vec3.directionFromRotation(pitch, yaw).scale(floor(i / STEPS)))
            if ((isIgnored(cur) || !inBB(cur)) && (isIgnored(cur.add(0.0, 1.0, 0.0)) || !inBB(cur.add(0.0, 1.0, 0.0)))) {
                return Vec3(floor(pos.x) + 0.5, floor(pos.y), floor(pos.z) + 0.5)
            }

            return null
        }
    }

    fun predictTeleportNoCheck(distance: Int, start: Vec3, yaw: Float, pitch: Float): Vec3 {
        val player = start.add(0.0, STAND_EYE_HEIGHT, 0.0)
        val dir = Vec3.directionFromRotation(pitch, yaw)
        val end = player.add(dir.scale(distance.toDouble()))
        return Vec3(
            floor(end.x) + 0.5,
            floor(end.y),
            floor(end.z) + 0.5
        )
    }

    private fun isIgnored(pos: Vec3): Boolean {
        val state = mc.level!!.getBlockState(BlockPos.containing(pos))
        return isIgnored(state)
    }

    private fun isIgnored(state: BlockState): Boolean {
        return IGNORED_BLOCKS.contains(state.block) ||
                IGNORED_BLOCKS_CLASSES.any { it.isInstance(state.block) }
    }

    private fun isIgnored2(pos: Vec3): Boolean {
        val state = mc.level!!.getBlockState(BlockPos.containing(pos))
        return isIgnored(state) || state.block is SlabBlock
    }

    fun isSpecial(pos: Vec3): Boolean {
        val state = mc.level!!.getBlockState(BlockPos.containing(pos))
        return SPECIAL_BLOCKS.any { it.isInstance(state.block) }
    }

    fun inBB(pos: Vec3): Boolean {
        val blockPos = BlockPos.containing(pos)
        val block = mc.level!!.getBlockState(blockPos)
        val bb = block.getShape(mc.level!!, blockPos).bounds()
        return bb.contains(pos)
    }
}