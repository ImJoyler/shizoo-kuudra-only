package shizo.module.impl.floor7.phase5

import net.minecraft.client.KeyMapping
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.DropdownSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.mixin.accessors.KeyMappingAccessor
import shizo.module.impl.Module
import shizo.utils.Colors
import shizo.utils.createSoundSettings
import shizo.utils.handlers.schedule
import shizo.utils.playSoundSettings
import shizo.utils.renderUtils.renderUtils.getStringWidth
import shizo.utils.renderUtils.renderUtils.textDim
import shizo.utils.skyblock.dungeon.DungeonUtils
import shizo.utils.skyblock.dungeon.M7Phases
import kotlin.math.max
import kotlin.math.min

object Debuff : Module(
    name = "Debuff Helper",
    description = "Automatically pulls and fires bows based on Server Ticks.",
    subcategory = "Phase 5"
) {
    private val semiAuto by BooleanSetting("Semi-Auto", true, "Automatically releases and re-draws the bow.")
    private val soundEnabled by BooleanSetting("Sound Enabled", true, "Plays a sound when fully charged.")
    private val soundSettings = createSoundSettings("Sound", "entity.experience_orb.pickup") { soundEnabled }

    private val tickSettings by DropdownSetting("Tick Settings")
    private val defaultTicks by NumberSetting(
        "Default Ticks",
        8.0,
        0.0,
        20.0,
        1.0,
        "Fallback ticks, I suggest putting 0 for normal gameplay."
    ).withDependency { tickSettings }

    private val p1Ticks by NumberSetting("P1 Ticks", 8.0, 0.0, 20.0, 1.0, "p1 ticks").withDependency { tickSettings }
    private val p2Ticks by NumberSetting("P2 Ticks", 8.0, 0.0, 20.0, 1.0, "p2 ticks").withDependency { tickSettings }
    private val p3Ticks by NumberSetting("P3 Ticks", 8.0, 0.0, 20.0, 1.0, "p3 ticks").withDependency { tickSettings }

    private val dragonSettings by DropdownSetting("Dragon Settings")
    private val purpleTicks by NumberSetting("Purple Dragon", 8.0, 0.0, 20.0, 1.0, "Purple Drag").withDependency { dragonSettings }
    private val greenTicks by NumberSetting("Green Dragon", 8.0, 0.0, 20.0, 1.0, " Green Dragon").withDependency { dragonSettings }
    private val redTicks by NumberSetting("Red Dragon", 8.0, 0.0, 20.0, 1.0, "Red Dragon").withDependency { dragonSettings }
    private val orangeTicks by NumberSetting("Orange Dragon", 8.0, 0.0, 20.0, 1.0, " Orange Dragon").withDependency { dragonSettings }
    private val blueTicks by NumberSetting("Blue Dragon", 8.0, 0.0, 20.0, 1.0, " Blue ").withDependency { dragonSettings }

    val debuffHud by HUD("Debuff Ticks", "Displays current bow charge ticks.", true) { example ->
        val target = if (example) 8 else getTicksBasedOnPosition()
        val current = if (example) 5 else ticks

        if (!example && target == 0) return@HUD 0 to 0
        if (!example && !lbCharged) return@HUD 0 to 0

        val color = if (current < target) "§c" else "§a"
        val text = "${color}LB: $current"

        textDim(text, 0, 0, Colors.WHITE)
        return@HUD getStringWidth(text) to 10
    }

    private var ticks = 0
    private var lbCharged = false
    private var isShooting = false

    init {
        on<TickEvent.Server> {
            val targetTicks = getTicksBasedOnPosition()
            if (targetTicks == 0) return@on

            val item = mc.player?.mainHandItem
            val isHoldingLB = item?.hoverName?.string?.lowercase()?.contains("last breath") == true

            val isKeyDown = mc.options.keyUse.isDown

            if (mc.screen != null || !isHoldingLB || !isKeyDown) {
                lbCharged = false
                ticks = 0
                isShooting = false
                return@on
            }

            if (isShooting) return@on

            lbCharged = true
            if (ticks < targetTicks) {
                ticks++
            }

            if (ticks == targetTicks) {
                if (soundEnabled) {
                    playSoundSettings(soundSettings())
                }

                if (semiAuto) {
                    isShooting = true

                    schedule(1, true) {
                        setRightClickState(false)
                    }

                    schedule(3, true) {
                        setRightClickState(true)
                    }

                } else {
                    isShooting = true
                }
            }
        }
    }

    private fun setRightClickState(pressed: Boolean) {
        val accessor = mc.options.keyUse as KeyMappingAccessor
        val boundKey = accessor.getBoundKey()
        KeyMapping.set(boundKey, pressed)
    }

    private fun inBoxSafe(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Boolean {
        val p = mc.player ?: return false
        val minX = min(x1, x2); val maxX = max(x1, x2)
        val minY = min(y1, y2); val maxY = max(y1, y2)
        val minZ = min(z1, z2); val maxZ = max(z1, z2)
        return p.x in minX..maxX && p.y in minY..maxY && p.z in minZ..maxZ
    }

    private fun getTicksBasedOnPosition(): Int {
        if (!DungeonUtils.inDungeons && !DungeonUtils.inBoss) return 0

        val phase = DungeonUtils.getF7Phase()
        when (phase) {
            M7Phases.P1 -> return p1Ticks.toInt()
            M7Phases.P2 -> return p2Ticks.toInt()
            M7Phases.P3 -> return p3Ticks.toInt()
            else -> {}
        }

        if (inBoxSafe(47.0, 28.0, 113.0, 64.0, 8.0, 135.0)) return purpleTicks.toInt()
        if (inBoxSafe(40.0, 27.0, 85.0, 13.0, 5.0, 103.0)) return greenTicks.toInt()
        if (inBoxSafe(40.0, 20.0, 68.0, 13.0, 4.0, 47.0)) return redTicks.toInt()
        if (inBoxSafe(72.0, 31.0, 65.0, 97.0, 3.0, 47.0)) return orangeTicks.toInt()
        if (inBoxSafe(72.0, 31.0, 85.0, 97.0, 3.0, 107.0)) return blueTicks.toInt()

        return defaultTicks.toInt()
    }
}