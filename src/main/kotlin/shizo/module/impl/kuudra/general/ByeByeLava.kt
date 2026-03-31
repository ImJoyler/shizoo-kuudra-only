package shizo.module.impl.kuudra.general

import net.minecraft.world.phys.Vec3
import shizo.events.ClickEvent
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.devMessage
import shizo.utils.handlers.EtherwarpHelper
import shizo.utils.isEtherwarpItem
import shizo.utils.modMessage
import shizo.utils.skyblock.kuudra.KuudraUtils

object ByeByeLava : Module(
    name = "Bye bye Lava",
    description = "òetìs ttry!"
) {
    private var predictedPos: Vec3? = null
    private var lastClickTime = 0L

    init {
        on<TickEvent.Start> {
            val player = mc.player ?: return@on
            if (System.currentTimeMillis() - lastClickTime > 1000 || player.deltaMovement.lengthSqr() > 0.1) {
                predictedPos = null
            }
        }

        on<ClickEvent> {
            if (!KuudraUtils.inKuudra || !enabled) return@on
            if (button != 1 || action != 1) return@on

            val player = mc.player ?: return@on
            if (!player.isShiftKeyDown) return@on

            val item = player.mainHandItem
            val etherData = item.isEtherwarpItem() ?: return@on

            val tuners = etherData.getByte("tuned_transmission").orElse(0.toByte()).toInt()
            val distance = 57.0 + (tuners * 2.0)

            val originPos = predictedPos ?: player.position()

            val target = EtherwarpHelper.getEtherPos(
                position = originPos,
                distance = distance,
                etherWarp = true,
                avoidLava = false
            )

            if (target.pos == null) return@on

            val check = EtherwarpHelper.getEtherPos(
                position = originPos,
                distance = distance,
                etherWarp = true,
                avoidLava = true
            )

            if (check.pos == null) {
                this.cancel()
                devMessage("Tp blocekd")
            } else {
                val blockPos = target.pos!!
                predictedPos = Vec3(blockPos.x + 0.5, blockPos.y.toDouble() + 1.0, blockPos.z + 0.5)
                lastClickTime = System.currentTimeMillis()
            }
        }
    }
}