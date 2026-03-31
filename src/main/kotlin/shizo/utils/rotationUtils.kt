package shizo.utils

import shizo.Shizo.mc
import shizo.events.RenderEvent
import shizo.events.core.on
import net.minecraft.util.Mth
import kotlin.math.*
import kotlin.random.Random

object RotationUtils {
    private var isRotating = false
    private var startTime = 0L
    private var duration = 0L

    private var startYaw = 0.0
    private var startPitch = 0.0

    private var endYaw = 0.0
    private var endPitch = 0.0

    private var controlYaw = 0.0
    private var controlPitch = 0.0

    private var useOvershoot = false
    private var overshootAmount = 0.0

    fun smoothRotate(
        targetYaw: Float,
        targetPitch: Float,
        minTime: Long,
        maxTime: Long,
        overshoot: Boolean
    ) {
        val player = mc.player ?: return

        startYaw = player.yRot.toDouble()
        startPitch = player.xRot.toDouble()

        var deltaYaw = (targetYaw.toDouble() - startYaw) % 360.0
        if (deltaYaw > 180.0) deltaYaw -= 360.0
        if (deltaYaw < -180.0) deltaYaw += 360.0

        endYaw = startYaw + deltaYaw
        endPitch = Mth.clamp(targetPitch, -90f, 90f).toDouble()

        val deltaPitch = endPitch - startPitch
        val distance = sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch)

        val distRatio = (distance / 180.0).coerceIn(0.0, 1.0)
        val baseDuration = minTime + (distRatio * (maxTime - minTime))
        duration = (baseDuration * Random.nextDouble(0.85, 1.15)).toLong()


        val midYaw = startYaw + (deltaYaw * 0.5)
        val midPitch = startPitch + (deltaPitch * 0.5)

        val length = if (distance > 0) distance else 1.0
        val perpYaw = -deltaPitch / length
        val perpPitch = deltaYaw / length

        val arcMultiplier = distance * Random.nextDouble(-0.15, 0.15)

        controlYaw = midYaw + (perpYaw * arcMultiplier)
        controlPitch = midPitch + (perpPitch * arcMultiplier)

        useOvershoot = overshoot
        overshootAmount = if (overshoot) Random.nextDouble(1.02, 1.08) else 1.0 // 2% to 8% past the target

        startTime = System.currentTimeMillis()
        isRotating = true
    }

    private fun easeInOutCubic(x: Double): Double {
        return if (x < 0.5) {
            4.0 * x * x * x
        } else {
            1.0 - (-2.0 * x + 2.0).pow(3.0) / 2.0
        }
    }

    init {
        on<RenderEvent.Extract> {
            if (!isRotating) return@on
            val player = mc.player ?: return@on
            if (mc.screen != null) {
                isRotating = false
                return@on
            }

            val elapsed = System.currentTimeMillis() - startTime

            if (elapsed >= duration) {
                player.yRot = endYaw.toFloat()
                player.xRot = endPitch.toFloat()

                player.yRot %= 360f
                if (player.yRot > 180f) player.yRot -= 360f
                if (player.yRot < -180f) player.yRot += 360f

                isRotating = false
                return@on
            }

            val x = elapsed.toDouble() / duration.toDouble()

            var t = easeInOutCubic(x)

            if (useOvershoot && x > 0.7) {
                val overshootProgression = (x - 0.7) / 0.3
                val spring = sin(overshootProgression * PI) * (overshootAmount - 1.0)
                t += spring
            }

            val u = 1.0 - t
            val currentYaw = (u * u * startYaw) + (2.0 * u * t * controlYaw) + (t * t * endYaw)
            val currentPitch = (u * u * startPitch) + (2.0 * u * t * controlPitch) + (t * t * endPitch)

            player.yRot = currentYaw.toFloat()
            player.xRot = currentPitch.toFloat()

            player.yRot %= 360f
            if (player.yRot > 180f) player.yRot -= 360f
            if (player.yRot < -180f) player.yRot += 360f

            player.xRot = Mth.clamp(player.xRot, -90f, 90f)
        }
    }
}