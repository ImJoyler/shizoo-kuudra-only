package shizo.module.impl.render.customesp

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.Color
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.drawStyledBox

enum class MatchType { NAME, HELMET, CHESTPLATE, LEGGINGS, BOOTS, HELD }
// /esp add name "Shadow Assassin" #5511aa
// /esp add boots "Perfect" "Diamond Guy" #00ffff
// /esp add held "Terminator" "Skeletor"
//esp add name bat #ff0000

data class ESPRule(
    val t: MatchType,
    val entityName: String,
    val itemName: String?,
    val color: Color
)

object CustomESP : Module(
    name = "CustomESP",
    description = "Highlight custom mobs based on names and equipment.",
    subcategory = "Render"
) {
    private val d by BooleanSetting("Depth Check", false, desc = "Disable to enable ESP through walls.")
    private val s by SelectorSetting("Render Style", "Outline", listOf("Filled", "Outline", "Filled Outline"), desc = "Style of the box.")

    val rules = mutableListOf<ESPRule>()
    private val HE = mutableMapOf<Entity, Color>()

    init {
        CustomESPConfig.loadConfig()

        on<TickEvent.End> {
            if (rules.isEmpty()) {
                if (HE.isNotEmpty()) HE.clear()
                return@on
            }

            val world = mc.level ?: return@on
            HE.keys.removeIf { !it.isAlive || it.isRemoved }

            for (entity in world.entitiesForRendering()) {
                if (!entity.isAlive) continue

                val name = entity.name.string.noControlCodes.lowercase()
                var matchedColor: Color? = null

                var targetEntity = entity

                for (rule in rules) {
                    if (name.contains(rule.entityName)) {

                        if (rule.t == MatchType.NAME) {
                            matchedColor = rule.color

                            if (entity is net.minecraft.world.entity.decoration.ArmorStand) {
                                val mobBelow = world.getEntities(entity, entity.boundingBox.move(0.0, -1.0, 0.0)) {
                                    it is LivingEntity &&
                                            it !is net.minecraft.world.entity.decoration.ArmorStand &&
                                            it != mc.player
                                }.firstOrNull()

                                if (mobBelow != null) {
                                    targetEntity = mobBelow
                                }
                            }
                            break
                        }

                        if (targetEntity is LivingEntity) {
                            val slot = when (rule.t) {
                                MatchType.HELMET -> EquipmentSlot.HEAD
                                MatchType.CHESTPLATE -> EquipmentSlot.CHEST
                                MatchType.LEGGINGS -> EquipmentSlot.LEGS
                                MatchType.BOOTS -> EquipmentSlot.FEET
                                MatchType.HELD -> EquipmentSlot.MAINHAND
                                else -> continue
                            }

                            val equippedItemName = targetEntity.getItemBySlot(slot).hoverName.string.noControlCodes.lowercase()

                            if (rule.itemName != null && equippedItemName.contains(rule.itemName)) {
                                matchedColor = rule.color
                                break
                            }
                        }
                    }
                }

                if (matchedColor != null) {
                    HE[targetEntity] = matchedColor
                } else {
                    HE.remove(targetEntity)
                }
            }        }

        on<RenderEvent.Extract> {
            if (HE.isEmpty()) return@on

            val styleIdx = when (s) {
                0 -> 0
                1 -> 1
                2 -> 2
                else -> 1
            }

            HE.forEach { (entity, color) ->
                drawStyledBox(entity.boundingBox, color, style = styleIdx, depth = d)
            }
        }

        on<WorldEvent.Load> {
            HE.clear()
        }
    }
}