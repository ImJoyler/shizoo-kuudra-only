package shizo.module.impl.floor7.general

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.ColorSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.renderUtils.renderUtils.drawStyledBox
import shizo.utils.skyblock.dungeon.DungeonUtils

object BossESP : Module(
    name = "Boss ESP",
    description = "Highlights boss withers in dungeons."
) {
    private val depthCheck by BooleanSetting("Depth Check", false, desc = "Disable to enable ESP through walls.")

    private val color by ColorSetting(
        "Boss Color", Colors.MINECRAFT_BLUE, true, desc = "The color of the ESP"
    )

    private val renderStyle by SelectorSetting(
        "Render Style",
        "Outline",
        listOf("Filled", "Outline", "Filled Outline"),
        desc = "Style of the box."
    )

    private val highlightedEntities = mutableMapOf<Entity, Color>()

    init {
        // same thing as below render every tick might impact performance BUT i'm not an expert on that
        on<TickEvent.End> {
            if (!DungeonUtils.inDungeons || !DungeonUtils.inBoss) /* only in dungeons*/{
                highlightedEntities.clear()
                return@on
            }

            val newMap = mutableMapOf<Entity, Color>()
            val world = mc.level ?: return@on

            for (entity in world.entitiesForRendering()) {
                if (entity == null || !entity.isAlive || entity !is WitherBoss) continue

                if (!entity.isInvisible && entity.invulnerableTicks != 800 /* wither isn't spawining*/) {
                    newMap[entity] = color
                }
            }
            highlightedEntities.clear()
            highlightedEntities.putAll(newMap)
        }

        on<RenderEvent.Extract> {
            val styleIdx = when (renderStyle) {
                0 -> 0 // Filled
                1 -> 1 // Outline
                2 -> 2 // Filled Outline
                else -> 1
            }

            highlightedEntities.forEach { (entity, entityColor) ->
                drawStyledBox(
                    entity.boundingBox,
                    entityColor,
                    style = styleIdx,
                    depth = depthCheck
                )
            }
        }

        on<WorldEvent.Load> {
            highlightedEntities.clear()
        }
    }
}