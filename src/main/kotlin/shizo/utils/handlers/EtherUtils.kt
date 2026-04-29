package shizo.utils.handlers

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.SignBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import shizo.Shizo.mc
import net.minecraft.client.gui.screens.ChatScreen
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sign

object EtherUtils {
    const val SNEAK_EYE_HEIGHT = 1.54
    const val STAND_EYE_HEIGHT = 1.62

    data class EtherPos(val succeeded: Boolean, val pos: BlockPos?, val state: BlockState?) {
        // atLowerCornerOf for exact block corner, 0.5 for the center // need to check this..
        val vec3: Vec3? by lazy { pos?.let { Vec3.atLowerCornerOf(it) } }

        companion object {
            val NONE = EtherPos(false, null, null)
        }
    }

    /**
     * Calculates Etherwarp/Teleport position using Voxel Raycasting (DDA).
     * By default, uses the player's lookAngle.
     */
    fun getEtherPos(
        position: Vec3?,
        distance: Double,
        returnEnd: Boolean = false,
        etherWarp: Boolean = true,
        avoidLava: Boolean = false
    ): EtherPos {
        val player = mc.player ?: return EtherPos.NONE
        if (position == null) return EtherPos.NONE

        val eyeHeight = if (player.isCrouching) 1.54 else 1.62
        val startPos = position.add(0.0, eyeHeight, 0.0)

        val lookVec = player.lookAngle
        val endPos = startPos.add(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance)

        val result = traverseVoxels(startPos, endPos, etherWarp, avoidLava)

        return if (result != EtherPos.NONE) {
            result
        } else if (returnEnd) {
            EtherPos(true, BlockPos.containing(endPos), null)
        } else {
            EtherPos.NONE
        }
    }

    fun getEtherPosRotated(
        position: Vec3?,
        yaw: Float,
        pitch: Float,
        distance: Double,
        returnEnd: Boolean = false,
        etherWarp: Boolean = true,
        avoidLava: Boolean = false
    ): EtherPos {
        val player = mc.player ?: return EtherPos.NONE
        if (position == null) return EtherPos.NONE

        val eyeHeight = if (player.isCrouching) 1.54 else 1.62
        val startPos = position.add(0.0, eyeHeight, 0.0)

        val lookVec = Vec3.directionFromRotation(pitch, yaw)
        val endPos = startPos.add(lookVec.scale(distance))

        val result = traverseVoxels(startPos, endPos, etherWarp, avoidLava)

        return if (result != EtherPos.NONE) {
            result
        } else if (returnEnd) {
            EtherPos(true, BlockPos.containing(endPos), null)
        } else {
            EtherPos.NONE
        }
    }

    private fun traverseVoxels(start: Vec3, end: Vec3, etherWarp: Boolean, avoidLava: Boolean): EtherPos {
        var x = floor(start.x).toInt()
        var y = floor(start.y).toInt()
        var z = floor(start.z).toInt()

        val endX = floor(end.x).toInt()
        val endY = floor(end.y).toInt()
        val endZ = floor(end.z).toInt()

        val dirX = end.x - start.x
        val dirY = end.y - start.y
        val dirZ = end.z - start.z

        val stepX = sign(dirX).toInt()
        val stepY = sign(dirY).toInt()
        val stepZ = sign(dirZ).toInt()

        val tDeltaX = abs(1.0 / dirX)
        val tDeltaY = abs(1.0 / dirY)
        val tDeltaZ = abs(1.0 / dirZ)

        var tMaxX = abs((floor(start.x) + max(0.0, stepX.toDouble()) - start.x) / dirX)
        var tMaxY = abs((floor(start.y) + max(0.0, stepY.toDouble()) - start.y) / dirY)
        var tMaxZ = abs((floor(start.z) + max(0.0, stepZ.toDouble()) - start.z) / dirZ)

        if (tMaxX < tMaxY) {
            if (tMaxX < tMaxZ) {
                tMaxX += tDeltaX
                x += stepX
            } else {
                tMaxZ += tDeltaZ
                z += stepZ
            }
        } else {
            if (tMaxY < tMaxZ) {
                tMaxY += tDeltaY
                y += stepY
            } else {
                tMaxZ += tDeltaZ
                z += stepZ
            }
        }

        val currentPos = BlockPos.MutableBlockPos()

        repeat(1000) {
            currentPos.set(x, y, z)

            val chunk = mc.level?.getChunk(
                SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(z)
            ) as? LevelChunk ?: return EtherPos.NONE

            val currentBlockState = chunk.getBlockState(currentPos)

            if (avoidLava && currentBlockState.fluidState.`is`(FluidTags.LAVA)) {
                return EtherPos.NONE
            }

            if (!isPassable(currentPos, chunk)) {

                if (!etherWarp) {
                    return EtherPos(true, currentPos.immutable(), currentBlockState)
                }

                val feetPos = currentPos.above(1)
                val feetState = chunk.getBlockState(feetPos)
                if (avoidLava && feetState.fluidState.`is`(FluidTags.LAVA)) return EtherPos.NONE
                if (!isPassable(feetPos, chunk)) return EtherPos.NONE

                val headPos = currentPos.above(2)
                val headState = chunk.getBlockState(headPos)
                if (avoidLava && headState.fluidState.`is`(FluidTags.LAVA)) return EtherPos.NONE
                if (!isPassable(headPos, chunk)) return EtherPos.NONE

                return EtherPos(true, currentPos.immutable(), currentBlockState)
            }

            if (x == endX && y == endY && z == endZ) {
                return EtherPos.NONE
            }

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                tMaxX += tDeltaX
                x += stepX
            } else if (tMaxY <= tMaxZ) {
                tMaxY += tDeltaY
                y += stepY
            } else {
                tMaxZ += tDeltaZ
                z += stepZ
            }
        }

        return EtherPos.NONE
    }

    /**
     * Raycasts exactly from the given origin vector (does NOT add eye height).
     */
    fun getEtherFromOrigin(
        origin: Vec3,
        yaw: Float,
        pitch: Float,
        distance: Double,
        etherWarp: Boolean = true,
        avoidLava: Boolean = false
    ): BlockPos? {
        val lookVec = Vec3.directionFromRotation(pitch, yaw)
        val endPos = origin.add(lookVec.scale(distance))

        val result = traverseVoxels(origin, endPos, etherWarp, avoidLava)

        return if (result.succeeded) result.pos else null
    }

    private fun isPassable(pos: BlockPos, chunk: LevelChunk): Boolean {
        val level = mc.level ?: return true
        val state = chunk.getBlockState(pos)
        return when (state.block) {
            is FlowerPotBlock -> true
            is LadderBlock -> true
            is SignBlock -> false
            else -> state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty
        }
    }
    fun getYawAndPitch(dx: Double, dy: Double, dz: Double): FloatArray {
        val horizontalDistance = Math.sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(Math.atan2(-dx, dz))
        val pitch = -Math.toDegrees(Math.atan2(dy, horizontalDistance))
        val normalizedYaw = if (yaw < -180) yaw + 360 else yaw
        return floatArrayOf(normalizedYaw.toFloat(), pitch.toFloat())
    }


    fun getYawAndPitch(
        pos: Vec3,
        sneaking: Boolean,
        playerSP: net.minecraft.client.player.LocalPlayer,
        doY: Boolean
    ): FloatArray {
        val dx = pos.x - playerSP.x
        val dy = if (!doY) 0.0 else pos.y - (playerSP.y + 1.62 - (if (sneaking) 0.08 else 0.0))
        val dz = pos.z - playerSP.z
        return getYawAndPitch(dx, dy, dz)
    }

    /**
     * Raycasts exactly from the given origin vector (does NOT add eye height).
     */

    fun fastGetEtherFromOrigin(start: Vec3, yaw: Float, pitch: Float, dist: Number): BlockPos? {
        val lookVec = Vec3.directionFromRotation(pitch, yaw)
        val endPos = start.add(lookVec.scale(dist.toDouble()))

        val result = traverseVoxels(start, endPos, etherWarp = true, avoidLava = false)
        return if (result.succeeded) result.pos else null
    }

    /**
     * Where Boolean is true if the teleport is successful, false if space is blocked.
     */
    fun getEtherPosFromOrigin(origin: Vec3, yaw: Float, pitch: Float, dist: Number): Pair<BlockPos?, Boolean> {
        val lookVec = Vec3.directionFromRotation(pitch, yaw)
        val endPos = origin.add(lookVec.scale(dist.toDouble()))

        val result = traverseVoxels(origin, endPos, etherWarp = true, avoidLava = false)

        return if (result.succeeded) {
            Pair(result.pos, true)
        } else if (result.pos != null) {
            Pair(result.pos, false)
        } else {
            Pair(null, false)
        }
    }

    /**
     * Uses the player's current look angle to calculate the direction.
     */
    fun getEtherPosFromOrigin(origin: Vec3, dist: Number): Pair<BlockPos?, Boolean> {
        val player = mc.player ?: return Pair(null, false)
        val lookVec = player.lookAngle
        val endPos = origin.add(lookVec.scale(dist.toDouble()))

        val result = traverseVoxels(origin, endPos, etherWarp = true, avoidLava = false)

        return if (result.succeeded) {
            Pair(result.pos, true)
        } else if (result.pos != null) {
            Pair(result.pos, false)
        } else {
            Pair(null, false)
        }
    }
}
