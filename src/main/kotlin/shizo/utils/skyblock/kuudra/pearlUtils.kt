package shizo.utils.skyblock.kuudra

import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import shizo.Shizo.mc

object PearlUtils {

    fun predictPearlLanding(): Vec3? {
        val player = mc.player ?: return null
        val level = mc.level ?: return null

        val STAND_EYE_HEIGHT = 1.6200000047683716
        val SNEAK_EYE_HEIGHT = 1.5399999618530273
        val eyeHeight = if (player.isCrouching) SNEAK_EYE_HEIGHT else STAND_EYE_HEIGHT

        val pitch = player.xRot.toDouble()
        val yaw = player.yRot.toDouble()

        val pitchRad = pitch / 180.0 * Math.PI
        val yawRad = yaw / 180.0 * Math.PI

        var px = player.x
        var py = player.y + eyeHeight
        var pz = player.z

        px -= Math.cos(yawRad) * 0.1600000023841858
        py -= 0.10000000149011612
        pz -= Math.sin(yawRad) * 0.1600000023841858

        val f = 0.4

        var motionX = -Math.sin(yawRad) * Math.cos(pitchRad) * f
        var motionZ = Math.cos(yawRad) * Math.cos(pitchRad) * f
        var motionY = -Math.sin(pitchRad) * f

        val f1 = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ)
        val velocity = 1.5
        motionX = (motionX / f1) * velocity
        motionY = (motionY / f1) * velocity
        motionZ = (motionZ / f1) * velocity


        for (i in 0..300) {
            val start = Vec3(px, py, pz)
            val end = Vec3(px + motionX, py + motionY, pz + motionZ)

            val hitResult = level.clip(
                ClipContext(
                    start, end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
                )
            )
            if (hitResult.type == HitResult.Type.BLOCK) {
                val hitPos = (hitResult as BlockHitResult).blockPos
                return Vec3(hitPos.x + 0.5, hitPos.y.toDouble() + 1.0, hitPos.z + 0.5)
            }

            px += motionX
            py += motionY
            pz += motionZ

            motionX *= 0.9900000095367432
            motionY *= 0.9900000095367432
            motionZ *= 0.9900000095367432
            motionY -= 0.029999999329447746
        }
        return null
    }
}