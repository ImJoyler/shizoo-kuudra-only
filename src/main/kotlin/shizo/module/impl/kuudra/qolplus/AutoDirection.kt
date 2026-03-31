package shizo.module.impl.kuudra.qolplus

import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.RotationUtils
import shizo.utils.devMessage
import shizo.utils.modMessage
import shizo.utils.skyblock.kuudra.KuudraUtils
import kotlin.random.Random

object AutoDirection: Module (
    name = "Auto Direction",
    description = "Auto rotates for kuudra",
    subcategory = "QOL"

){
     private val humanlike by BooleanSetting("Human-Like", true, "Human like rotation")
     private val autoBack by BooleanSetting("Back Rotation", true, "Allows back rotation")

    private val basePitch by NumberSetting("Target Pitch", -35.0, -90.0, 90.0, 1.0, "-90 is straight up, 90 is straight down.")
    private val minTime by NumberSetting("Min Time", 50, 0, 1000, 1, "Min rotation time")
    private val maxTime by NumberSetting("Max Time", 250, 0, 1000, 1, "Max rotation time")

    private val backTimeMin by NumberSetting("Min Back Time", 50, 0, 1000, 1, "Min back rotation time")
    private val backTimeMax by NumberSetting("Max Back Time", 250, 0, 1000, 1, "Max back rotation time")
    private var hasRotateForPhase = false

    init {

        on<WorldEvent.Load> {
            hasRotateForPhase = false
        }

        on<TickEvent.End> {
            if (!KuudraUtils.inKuudra) return@on
            if (mc.player == null || mc.level == null) return@on
            val y = mc.player!!.y
            // check when at platform
            if (y !in 5.9..6.5) return@on
            if (hasRotateForPhase) return@on

            val kuudra = KuudraUtils.kuudraEntity ?: return@on
            val hp = kuudra.health
            val x = kuudra.x
            val z = kuudra.z
            // test


            if (!hasRotateForPhase && hp <= 25000f) {
                hasRotateForPhase = true

                devMessage("§fTeleported to DPS {${mc.player!!.x.toInt()}, ${mc.player!!.y.toInt()}, ${mc.player!!.z.toInt()}}")

                val yawOffSet = if (humanlike) Random.nextDouble(-5.0, 5.0).toFloat() else 0f
                val targetPitch = basePitch.toFloat() + (if (humanlike) Random.nextDouble(-2.0, 2.0).toFloat() else 0f)

                when {
                    x < -128 -> RotationUtils.smoothRotate(90f + yawOffSet, targetPitch, minTime.toLong(), maxTime.toLong(), overshoot = true) // rrihgty
                    z > -84 -> RotationUtils.smoothRotate(0f + yawOffSet, targetPitch, minTime.toLong(), maxTime.toLong(), overshoot = true) // fribnt
                    x > -72 -> RotationUtils.smoothRotate(-90f + yawOffSet, targetPitch, minTime.toLong(), maxTime.toLong(), overshoot = true) // left
                    z < -132 -> { // abck GOD DAMN I CANNOT TYPE
                        if (autoBack) {
                            RotationUtils.smoothRotate(-179f, targetPitch, backTimeMin.toLong(), backTimeMax.toLong(), overshoot = true)
                        }
                    }
                }
            }
        }
    }
}