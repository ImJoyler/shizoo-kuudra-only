package shizo.module.impl.misc

import shizo.events.TickEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.skyblock.LocationUtils

object AutoJump : Module(
    name = "Auto Jump",
    description = "turns on auto jump outside skyblock, we need a better pb in proto parkour"

)
{
   private var originalState: Boolean?= null
    // pretty useless functions but hey!!
    override fun onEnable() {
        if (mc.options != null) {
        originalState = mc.options.autoJump().get()
        }
        super.onEnable()
    }

    override  fun onDisable() {
        if (originalState != null && mc.options != null) {
            mc.options.autoJump().set(originalState!!)
        }
        super.onDisable()
    }

    init {
        on<TickEvent.End> {
            if (mc.player == null || mc.player!!.tickCount % 20 != 0) return@on
            val inSkyblock = LocationUtils.isInSkyblock
            val currentSetting = mc.options.autoJump().get()

            if (inSkyblock) {
                if (currentSetting) {
                    mc.options.autoJump().set(false)
                }
            } else {
                if (!currentSetting) {
                    mc.options.autoJump().set(true)
                }
            }
        }

    }
}