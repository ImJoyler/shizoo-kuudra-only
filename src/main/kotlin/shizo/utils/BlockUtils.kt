package shizo.utils

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import shizo.Shizo.mc
import net.minecraft.core.Direction

// ty odin <3
object BlockUtils {
    val blackList: List<Block> = listOf(
        Blocks.CRAFTING_TABLE, Blocks.ANVIL, Blocks.ENDER_CHEST, Blocks.CHEST, Blocks.LEVER,
        Blocks.ACACIA_DOOR, Blocks.BEACON, Blocks.WHITE_BED, Blocks.BIRCH_DOOR,
        Blocks.BREWING_STAND, Blocks.BROWN_MUSHROOM, Blocks.COMMAND_BLOCK, Blocks.DARK_OAK_DOOR,
        Blocks.DAYLIGHT_DETECTOR, Blocks.DISPENSER, Blocks.DROPPER, Blocks.ENCHANTING_TABLE,
        Blocks.FURNACE, Blocks.JUNGLE_DOOR, Blocks.REDSTONE_BLOCK, Blocks.NOTE_BLOCK,
        Blocks.OAK_DOOR, Blocks.COMPARATOR, Blocks.REPEATER, Blocks.RED_MUSHROOM,
        Blocks.PLAYER_HEAD, Blocks.OAK_SIGN, Blocks.OAK_TRAPDOOR, Blocks.TRAPPED_CHEST,
        Blocks.STONE_BUTTON, Blocks.OAK_BUTTON
    )

    fun getStateAt(pos: BlockPos): BlockState = mc.level?.getBlockState(pos) ?: Blocks.AIR.defaultBlockState()
    fun getStateAt(x: Int, y: Int, z: Int): BlockState = getStateAt(BlockPos(x, y, z))
    fun getBlockAt(pos: BlockPos): Block = getStateAt(pos).block
    fun getBlockAt(vec3: Vec3): Block = getBlockAt(BlockPos.containing(vec3))
    fun isAir(blockPos: BlockPos): Boolean =
        getBlockAt(blockPos) == Blocks.AIR
    //most of these are
    fun getBlockAt(x: Number, y: Number, z: Number): Block =
        getBlockAt(BlockPos(x.toInt(), y.toInt(), z.toInt()))

    fun Block.getBlockId(): Int = Block.getId(this.defaultBlockState())

    fun toAir(blockPos: BlockPos?) {
        if (blockPos == null) return
        val block = getBlockAt(blockPos)
        if (blackList.contains(block)) return
        mc.level?.setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState())
    }

    fun ghostBlock(blockPos: BlockPos, blockState: BlockState) {
        mc.level?.setBlock(blockPos, blockState, 3)
    }

    fun BlockPos.toVecCenter(): Vec3 = Vec3.atCenterOf(this)
    fun Vec3.toPos(): BlockPos = BlockPos.containing(this)

    fun BlockState.getMetadata(): Int = Block.getId(this)
    fun BlockState.getBlockId(): Int = Block.getId(this)

    fun collisionRayTrace(
        pos: BlockPos,
        localAabb: AABB,   // in block-local coords (0..1)
        start: Vec3,       // world coords
        end: Vec3          // world coords
    ): BlockHitResult? {
        // Convert the local (0..1) box into world coords by offsetting with the block position
        val worldAabb = localAabb.move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

        // Returns the intersection point on the AABB along the segment start->end, or null if no hit
        val hitPos: Vec3 = worldAabb.clip(start, end).orElse(null) ?: return null

        // Determine which face was hit (roughly equivalent to EnumFacing in old code)
        val face = hitFace(worldAabb, hitPos)

        // BlockHitResult needs: hit location, direction, block pos, isInside
        return BlockHitResult(hitPos, face, pos, false)
    }

    private fun hitFace(aabb: AABB, hit: Vec3): Direction {
        val eps = 1.0e-7

        return when {
            kotlin.math.abs(hit.x - aabb.minX) < eps -> Direction.WEST
            kotlin.math.abs(hit.x - aabb.maxX) < eps -> Direction.EAST
            kotlin.math.abs(hit.y - aabb.minY) < eps -> Direction.DOWN
            kotlin.math.abs(hit.y - aabb.maxY) < eps -> Direction.UP
            kotlin.math.abs(hit.z - aabb.minZ) < eps -> Direction.NORTH
            kotlin.math.abs(hit.z - aabb.maxZ) < eps -> Direction.SOUTH
            else -> Direction.UP
        }
    }
}