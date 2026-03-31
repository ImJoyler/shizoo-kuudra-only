package shizo.module.impl.floor7.terminals

import net.minecraft.world.entity.decoration.ArmorStand
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.ColorSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.RenderEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.Color
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.drawStyledBox
import shizo.utils.skyblock.dungeon.DungeonUtils
import shizo.utils.skyblock.dungeon.M7Phases

object TerminalEsp : Module(
    name = "Terminal ESP",
    description = "Shows undone terminals.",
    subcategory = "Terminals"
) {
    private val color by ColorSetting(
        "Terminal ESP Color",
        Color(0, 0, 255),
        false,
        desc = "Color for the Terminal ESP."
    )
    private val depth by BooleanSetting("Depth Check", false, desc = "Render through walls (disable depth).")
    private val boxStyle by NumberSetting("Box Style", 2.0, 0.0, 2.0, 1.0, desc = "0 = Outline, 1 = Filled, 2 = Both")

    init {
        on<RenderEvent.Extract> {
            if (DungeonUtils.getF7Phase() != M7Phases.P3) return@on

            val level = mc.level ?: return@on

            for (entity in level.entitiesForRendering()) {
                if (entity !is ArmorStand) continue

                val name = entity.customName?.string?.noControlCodes ?: continue

                if (DungeonUtils.termInactiveTitles.contains(name)) {
                    this.drawStyledBox(entity.boundingBox, color, boxStyle.toInt(), depth)
                }
            }
        }
    }
}