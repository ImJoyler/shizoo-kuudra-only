package shizo.utils

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object MathUtils {
    data class Rotation(var yaw: Float, var pitch: Float)

    fun calcYawPitch(targetVec: Vec3, playerPos: Vec3): Rotation {
        val delta = targetVec.subtract(playerPos)
        val yaw = -atan2(delta.x, delta.z) * (180 / PI)
        val pitch = -atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z)) * (180 / PI)
        return Rotation(yaw.toFloat(), pitch.toFloat())
    }

    fun getLook(yaw: Float, pitch: Float): Vec3 {
        val f2 = -cos(-pitch * 0.017453292f).toDouble()
        return Vec3(
            sin(-yaw * 0.017453292f - 3.1415927f) * f2,
            sin(-pitch * 0.017453292f).toDouble(),
            cos(-yaw * 0.017453292f - 3.1415927f) * f2
        )
    }

    fun normalizeYaw(yaw: Float): Float {
        var result = yaw
        while (result >= 180) result -= 360f
        while (result < -180) result += 360f
        return result
    }

    fun normalizePitch(pitch: Float): Float {
        var result = pitch
        while (result >= 90) result -= 180f
        while (result < -90) result += 180f
        return result
    }

    fun BlockPos.toVec3(): Vec3 = Vec3.atLowerCornerOf(this)

    fun Vec3.add(x: Number = 0.0, y: Number = 0.0, z: Number = 0.0) =
        this.add(x.toDouble(), y.toDouble(), z.toDouble())

    fun Vec3.multiply(factor: Double) = this.scale(factor)

    fun getRotationTo(from: Vec3, to: Vec3): Rotation {
        val diffX = to.x - from.x
        val diffY = to.y - from.y
        val diffZ = to.z - from.z
        val dist = sqrt(diffX * diffX + diffZ * diffZ)

        val yaw = (atan2(diffZ, diffX) * 180.0 / PI).toFloat() - 90.0f
        val pitch = (-(atan2(diffY, dist) * 180.0 / PI)).toFloat()
        return Rotation(yaw, pitch)
    }
}