package shizo.module.impl.kuudra.phasethree

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.ColorSetting
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.utils.Color
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.renderUtils.renderUtils.drawStyledBox

object StunWaypoints : Module(
    name = "Stun Waypoints",
    description = "Renders a waypoint for the supply block inside Kuudra's belly.",
    subcategory = "Phase 3"

) {
    // todo add skip waypoint
    private val depth by BooleanSetting("Waypoints Depth", true, desc = "Depth test for the waypoint.")
    private var wpColour by ColorSetting(
        "Waypoint Colour",
        Color(0, 255, 255),
        false,
        desc = "Color of the belly waypoint."
    )

    private val blockPos = Vec3(-168.0, 27.0, -169.0)
    private val enterPos = Vec3(-161.0, 49.0, -186.0)

    private var inBelly = false
    private var drawPos = Vec3(0.0, 0.0, 0.0)

    init {
        on<WorldEvent.Load> {
            inBelly = false
        }

        onReceive<ClientboundPlayerPositionPacket> {
                val pos = this.change.position

                if (pos.x == -161.0 && pos.y == 49.0 && pos.z == -186.0) {
                    inBelly = true
                }
        }


        on<TickEvent.Server> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 3) {
                inBelly = false
                return@on
            }

            val p = mc.player ?: return@on

            if (!inBelly) {
                val diff = blockPos.subtract(enterPos)
                drawPos = p.position().add(diff)
            } else {
                drawPos = blockPos
            }
        }

        on<RenderEvent.Extract> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 3) return@on

            val s = 0.5
            val box = AABB(
                drawPos.x - s + 0.5, drawPos.y, drawPos.z - s + 0.5,
                drawPos.x + s + 0.5, drawPos.y + 1.0, drawPos.z + s + 0.5
            )

            this.drawStyledBox(box, wpColour, 1, depth)

            val innerColor = Color(wpColour.red, wpColour.green, wpColour.blue, 64f)
            this.drawStyledBox(box, innerColor, 0, depth)
        }
    }
}