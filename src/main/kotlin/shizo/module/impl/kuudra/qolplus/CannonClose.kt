package shizo.module.impl.kuudra.qolplus

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ClickType
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.ChatPacketEvent
import shizo.events.GuiEvent
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.mixin.accessors.AbstractContainerScreenAccessor
import shizo.module.impl.Module
import shizo.module.impl.kuudra.phasetwo.BuildHUD // Importing our new HUD!
import shizo.utils.noControlCodes
import shizo.utils.skyblock.kuudra.KuudraUtils

object CannonClose : Module(
    name = "Cannon Close",
    description = "Automatically manages Human Cannonball purchasing.",
    subcategory = "QOL"

) {
    private val cannonClose by BooleanSetting(
        "Auto Close",
        true,
        desc = "Automatically closes the menu after purchasing Human Cannonball."
    )
    private val clickSwap by BooleanSetting("Middle Click", true, desc = "Kairo")
    private val blockb4perchent by BooleanSetting("Blocks Click", true, desc = "Blocks clicks before a certain %, please turn off save FPS")
    private val safeClickPercent by NumberSetting(
        "Safe Click %",
        95, 0, 100, 1,
        desc = "Blocks cannonball clicks until build is at least this percent."
    ).withDependency { blockb4perchent }

    private var preventReopenUntil = 0L
    private var closeDelayTicks = -1

    init {
        on<ChatPacketEvent> {
            if (!cannonClose) return@on
            if (!KuudraUtils.inKuudra) return@on

            if (value.contains("You purchased Human Cannonball!")) {
                preventReopenUntil = System.currentTimeMillis() + 500
                val screen = mc.screen
                if (screen is AbstractContainerScreen<*> && screen.title.string.contains("Perk Menu")) {
                    closeDelayTicks = 2
                }
            }
        }

        on<TickEvent.Start> {
            val player = mc.player ?: return@on

            if (closeDelayTicks > 0) {
                closeDelayTicks--
                if (closeDelayTicks == 0) {
                    val screen = mc.screen
                    if (screen is AbstractContainerScreen<*> && screen.title.string.contains("Perk Menu")) {
                        player.closeContainer()
                    }
                    preventReopenUntil = System.currentTimeMillis() + 500
                    closeDelayTicks = -1
                }
            }

            if (System.currentTimeMillis() < preventReopenUntil) {
                val screen = mc.screen
                if (screen is AbstractContainerScreen<*> && screen.title.string.contains("Perk Menu")) {
                    player.closeContainer()
                }
            }
        }

        on<GuiEvent.MouseClick> {
            if (!clickSwap) return@on
            if (click.button() != 0) return@on

            if (mc.options.keyShift.isDown) return@on
            val screen = mc.screen as? AbstractContainerScreen<*> ?: return@on
            if (!screen.title.string.contains("Perk Menu")) return@on

            val slot = (screen as AbstractContainerScreenAccessor).hoveredSlot ?: return@on
            val stack = slot.item

            if (!stack.isEmpty && stack.hoverName.string.noControlCodes.contains("Human Cannonball")) {

                if (KuudraUtils.phase == 2 && blockb4perchent) {
                    if (BuildHUD.currentCalculatedProgress < safeClickPercent) {
                        cancel()
                        return@on
                    }
                }
            }

            cancel()

            mc.gameMode?.handleInventoryMouseClick(
                screen.menu.containerId,
                slot.index,
                2,
                ClickType.CLONE,
                mc.player!!
            )
        }
    }
}