package shizo.module.impl.kuudra.qolplus.fireball

import net.minecraft.world.phys.Vec3
import shizo.Shizo.mc
import kotlin.math.atan2
import kotlin.math.sqrt

object FireballUtils {
    val ballistaCoords = listOf(
        Vec3(-107.5, 79.0, -113.5),
        Vec3(-111.5, 79.0, -105.5),
        Vec3(-106.5, 79.0, -97.5),
        Vec3(-96.5, 79.0, -97.5),
        Vec3(-92.5, 79.0, -106.5),
        Vec3(-97.5, 79.0, -114.5)
    )

    fun getDistance2D(target: Vec3): Double {
        val player = mc.player ?: return 999.0
        val dx = target.x - player.x
        val dz = target.z - player.z
        return sqrt(dx * dx + dz * dz)
    }
    // do we not ahve this anywhere elsE???
    fun getYawAndPitch(target: Vec3, source: Vec3 = mc.player!!.position()): Pair<Float, Float> {
        val dx = target.x - source.x
        val dz = target.z - source.z

        var yaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
        while (yaw < -180f) yaw += 360f
        while (yaw > 180f) yaw -= 360f

        val eyeY = source.y + (mc.player?.eyeHeight ?: 1.62f)
        val dy = target.y - eyeY
        val distanceXZ = sqrt(dx * dx + dz * dz)
        val pitch = -Math.toDegrees(atan2(dy, distanceXZ)).toFloat()

        return Pair(yaw, pitch)
    }
}