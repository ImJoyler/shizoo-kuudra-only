package shizo.utils

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator
import shizo.Shizo.mc
import shizo.mixin.accessors.DeltaTrackerTimerAccessor
import shizo.mixin.accessors.MinecraftAccessor

object TimerSpeedController {
    private var originalOperator: FloatUnaryOperator? = null

    fun setTimerSpeed(speed: Float) {
        require(speed >= 0.0f) { "speed must be > 0" }

        val timer = (mc as MinecraftAccessor).`shizo$getDeltaTracker`()
        val accessor = timer as DeltaTrackerTimerAccessor

        if (originalOperator == null) {
            originalOperator = accessor.`shizo$getTargetMsptProvider`()
        }

        val original = originalOperator ?: return
        accessor.`shizo$setTargetMsptProvider`(FloatUnaryOperator { tps ->
            val base = original.apply(tps)
            base / speed
        })
    }

    fun resetTimerSpeed() {
        val timer = (mc as MinecraftAccessor).`shizo$getDeltaTracker`()
        val accessor = timer as DeltaTrackerTimerAccessor

        originalOperator?.let {
            accessor.`shizo$setTargetMsptProvider`(it)
        }
    }

    fun freezeForMillis(durationMs: Long) {
        setTimerSpeed(0f)

        Thread {
            try {
                Thread.sleep(durationMs)
            } catch (_: InterruptedException) {
            }

            mc.execute {
                resetTimerSpeed()
            }
        }.start()
    }
}