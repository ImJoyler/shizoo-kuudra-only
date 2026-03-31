package shizo.utils.skyblock.dungeon.tiles

import net.minecraft.core.BlockPos

import shizo.utils.Vec2
import shizo.utils.rotationNumber
import kotlin.times

//TODO could merge all the room enums into 1 file

data class Room(
    var rotation: Rotations = Rotations.NONE,
    var data: RoomData,
    var clayPos: BlockPos = BlockPos(0, 0, 0),
    val roomComponents: MutableSet<RoomComponent>,
//    var waypoints: MutableSet<DungeonWaypoints.DungeonWaypoint> = mutableSetOf(),
//    var nodes: MutableSet<AutoRouteNode> = mutableSetOf(),
) {
    fun getRelativeCoords(realPos: BlockPos): BlockPos {
        // claypos is the room core!
        val x = realPos.x - this.clayPos.x
        val y = realPos.y - this.clayPos.y
        val z = realPos.z - this.clayPos.z

        return when (this.rotation) {
            Rotations.WEST -> BlockPos(z, y, -x)
            Rotations.NORTH -> BlockPos(-x, y, -z)
            Rotations.EAST -> BlockPos(-z, y, x)
            else -> BlockPos(x, y, z)
        }
    }
    fun getRealCoords(relPos: BlockPos): BlockPos {
        val rotated = when (this.rotation) {
            Rotations.WEST -> BlockPos(-relPos.z, relPos.y, relPos.x)
            Rotations.NORTH -> BlockPos(-relPos.x, relPos.y, -relPos.z)
            Rotations.EAST -> BlockPos(relPos.z, relPos.y, -relPos.x)
            else -> relPos
        }
        // based on clay i hope that works
        return BlockPos(
            this.clayPos.x + rotated.x,
            this.clayPos.y + rotated.y,
            this.clayPos.z + rotated.z
        )
    }
    fun getRelativeYaw(yaw: Float) = yaw - (rotationNumber(this.rotation) * 90)

    fun getRealYaw(yaw: Float) = yaw + (rotationNumber(this.rotation) * 90)
}

data class RoomComponent(val x: Int, val z: Int, val core: Int = 0) {
    val vec2 = Vec2(x, z)
    val blockPos = BlockPos(x, 70, z)
}

data class RoomData(
    val name: String, val type: RoomType, val cores: List<Int>,
    val crypts: Int, val secrets: Int, val trappedChests: Int, val shape: RoomShape
)