package shizo.utils.handlers

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import shizo.Shizo.mc
import java.util.BitSet
import kotlin.math.*

object EtherwarpHelper {

    data class EtherPos(val succeeded: Boolean, val pos: BlockPos?, val state: BlockState?) {
        val vec3: Vec3? by lazy { pos?.let { Vec3.atLowerCornerOf(it) } }

        companion object {
            val NONE = EtherPos(false, null, null)
        }
    }
    fun getEtherPos(
        position: Vec3?,
        distance: Double,
        returnEnd: Boolean = false,
        etherWarp: Boolean = true,
        avoidLava: Boolean = false
    ): EtherPos {
        val player = mc.player ?: return EtherPos.NONE
        if (position == null) return EtherPos.NONE
        //val eyeHeight = if (player.isCrouching) 1.27 else 1.62 //todo change when 1.21.10 comes
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

    /**
     * Traverses voxels from start to end and returns the first non-air block it hits.
     * @author Bloom
     * ported by odin
     */
    private fun traverseVoxels(start: Vec3, end: Vec3, etherWarp: Boolean, avoidLava: Boolean): EtherPos {
        val (x0, y0, z0) = Triple(start.x, start.y, start.z)
        val (x1, y1, z1) = Triple(end.x, end.y, end.z)

        var x = floor(start.x)
        var y = floor(start.y)
        var z = floor(start.z)

        val endX = floor(end.x)
        val endY = floor(end.y)
        val endZ = floor(end.z)

        val dirX = x1 - x0
        val dirY = y1 - y0
        val dirZ = z1 - z0

        val stepX = sign(dirX).toInt()
        val stepY = sign(dirY).toInt()
        val stepZ = sign(dirZ).toInt()

        val invDirX = if (dirX != 0.0) 1.0 / dirX else Double.MAX_VALUE
        val invDirY = if (dirY != 0.0) 1.0 / dirY else Double.MAX_VALUE
        val invDirZ = if (dirZ != 0.0) 1.0 / dirZ else Double.MAX_VALUE

        val tDeltaX = abs(invDirX * stepX)
        val tDeltaY = abs(invDirY * stepY)
        val tDeltaZ = abs(invDirZ * stepZ)

        var tMaxX = abs((x + max(stepX.toDouble(), 0.0) - x0) * invDirX)
        var tMaxY = abs((y + max(stepY.toDouble(), 0.0) - y0) * invDirY)
        var tMaxZ = abs((z + max(stepZ.toDouble(), 0.0) - z0) * invDirZ)

        repeat(1000) {
            val blockPos = BlockPos(x.toInt(), y.toInt(), z.toInt())

            val chunk = mc.level?.getChunk(
                SectionPos.blockToSectionCoord(blockPos.x),
                SectionPos.blockToSectionCoord(blockPos.z)
            ) ?: return EtherPos.NONE

            val currentBlockState = chunk.getBlockState(blockPos)

            val currentBlockId = Block.getId(currentBlockState)

            if (avoidLava && currentBlockState.fluidState.`is`(FluidTags.LAVA)) {
                return EtherPos.NONE
            }

            if (!validEtherwarpFeetIds.get(currentBlockId)) {

                if (!etherWarp) {
                    return EtherPos(true, blockPos, currentBlockState)
                }

                val feetPos = blockPos.above()
                val feetState = chunk.getBlockState(feetPos)
                val feetId = Block.getId(feetState)


                if (!validEtherwarpFeetIds.get(feetId)) return EtherPos.NONE
                if (avoidLava && feetState.fluidState.`is`(FluidTags.LAVA)) return EtherPos.NONE

                val headPos = blockPos.above(2)
                val headState = chunk.getBlockState(headPos)
                val headId = Block.getId(headState)

                if (!validEtherwarpFeetIds.get(headId)) return EtherPos.NONE
                if (avoidLava && headState.fluidState.`is`(FluidTags.LAVA)) return EtherPos.NONE

                return EtherPos(true, blockPos, currentBlockState)
            }

            if (x == endX && y == endY && z == endZ) return EtherPos.NONE

            when {
                tMaxX <= tMaxY && tMaxX <= tMaxZ -> {
                    tMaxX += tDeltaX
                    x += stepX
                }
                tMaxY <= tMaxZ -> {
                    tMaxY += tDeltaY
                    y += stepY
                }
                else -> {
                    tMaxZ += tDeltaZ
                    z += stepZ
                }
            }
        }

        return EtherPos.NONE
    }


    private val validTypes = setOf(
        ButtonBlock::class, CarpetBlock::class, SkullBlock::class,
        WallSkullBlock::class, LadderBlock::class, SaplingBlock::class,
        FlowerBlock::class, StemBlock::class, CropBlock::class,
        RailBlock::class, SnowLayerBlock::class, BubbleColumnBlock::class,
        TripWireBlock::class, TripWireHookBlock::class, FireBlock::class,
        AirBlock::class, TorchBlock::class, FlowerPotBlock::class,
        TallFlowerBlock::class, TallGrassBlock::class, BushBlock::class,
        SeagrassBlock::class, TallSeagrassBlock::class, SugarCaneBlock::class,
        LiquidBlock::class, VineBlock::class, MushroomBlock::class,
        PistonHeadBlock::class, WebBlock::class,
        NetherWartBlock::class, NetherPortalBlock::class, RedStoneWireBlock::class,
        ComparatorBlock::class, RedstoneTorchBlock::class, RepeaterBlock::class
    )

    private val validEtherwarpFeetIds = BitSet().apply {
        BuiltInRegistries.BLOCK.forEach { block ->
            if (validTypes.any { it.isInstance(block) }) {
                set(Block.getId(block.defaultBlockState()))
            }
        }
    }
}