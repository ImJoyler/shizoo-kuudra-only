package shizo.module.impl.misc

import shizo.utils.skyblock.Island
import shizo.utils.skyblock.LocationUtils

import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import shizo.Shizo.mc
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.ChatPacketEvent
import shizo.events.RenderEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.Color
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.drawCustomBeacon
import shizo.utils.renderUtils.renderUtils.drawStyledBox

object CorpseESP : Module(
    name = "Corpse ESP",
    description = "Highlights unlooted corpses in Glacite Mineshafts",
    subcategory = "Mining"
) {
    private val renderStyle by SelectorSetting("Render Style", "Both", arrayListOf("Box Only", "Beacon Only", "Both"), "How to render the corpse.")
    private val depth by BooleanSetting("Depth Check", false, "Hides the ESP behind walls if enabled.")

    private val claimedPositions = mutableListOf<Vec3>()

    private enum class CorpseType(val helmetName: String, val displayName: String, val color: Color) {
        LAPIS("Lapis Armor Helmet", "Lapis", Color(0, 0, 255, 255f)),
        TUNGSTEN("Mineral Helmet", "Tungsten", Color(255, 255, 255, 255f)),
        UMBER("Yog Helmet", "Umber", Color(181, 98, 34, 255f)),
        VANGUARD("Vanguard Helmet", "Vanguard", Color(242, 36, 184, 255f))
    }

    init {
        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            if (msg.contains("CORPSE LOOT!")) {
                mc.player?.let { player ->
                    claimedPositions.add(player.position())
                }
            }
        }

        on<WorldEvent.Load> {
            claimedPositions.clear()
        }

        on<RenderEvent.Extract> {
            if (LocationUtils.currentArea != Island.Mineshaft) return@on
            val player = mc.player ?: return@on

            mc.level?.entitiesForRendering()?.forEach { entity ->
                if (entity !is ArmorStand || entity.isInvisible || entity.name.string != "Armor Stand") return@forEach

                val pos = entity.position()
                if (claimedPositions.any { it.distanceTo(pos) < 5.0 }) return@forEach

                val helmetName = entity.getItemBySlot(EquipmentSlot.HEAD).hoverName.string.noControlCodes
                if (helmetName.isEmpty()) return@forEach

                val type = CorpseType.entries.find { helmetName.contains(it.helmetName, ignoreCase = true) }
                if (type != null) {
                    val styleInt = when (renderStyle) { 0 -> 0; 1 -> 1; else -> 2 }

                    if (styleInt == 0 || styleInt == 2) {
                        this.drawStyledBox(entity.boundingBox, type.color, style = 2, depth = depth)
                    }

                    if (styleInt == 1 || styleInt == 2) {
                        this.drawCustomBeacon(
                            title = type.displayName,
                            position = entity.blockPosition(),
                            color = type.color,
                            increase = false,
                            distance = true
                        )
                    }
                }
            }
        }
    }
}