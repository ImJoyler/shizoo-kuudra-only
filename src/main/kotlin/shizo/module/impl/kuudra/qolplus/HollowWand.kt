package shizo.module.impl.kuudra.qolplus

import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import shizo.clickgui.settings.impl.ActionSetting
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.ChatPacketEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.*
import shizo.utils.handlers.schedule
import shizo.utils.skyblock.kuudra.KuudraUtils

object HollowWand : Module(
    name = "Auto Hollow Wand",
    description = "peak.",
    subcategory = "QOL"

) {
    private var hasClickedForPhase = false
    private var isTriggerArmed = false

    val start by NumberSetting("Start ticks", 0.0, 0.0, 20.0, 1.0, "Ticks to wait before Left Click")
    val end by NumberSetting("End ticks", 2.0, 0.0, 20.0, 1.0, "Ticks to wait before Right Click")
    val packets by BooleanSetting("Use packets", false, "Uses packets")
    val clicks by BooleanSetting("Use clicks", false, "Uses clicks")
    val test by ActionSetting("Test this module", "") {
        if (start.toInt() >= end.toInt()) {
            modMessage("§cFUCKING RETARD the timers ARE WRONG ITS GONNA BAN YOU")
            return@ActionSetting
        }

        val heldItem = mc.player?.mainHandItem
        val itemName = heldItem?.hoverName?.string?.noControlCodes ?: ""

        if (itemName.contains("Hollow Wand")) {
            mc.setScreen(null)
            devMessage("§eSequence started. Left click at ${start.toInt()}, Right click at ${end.toInt()}")

            schedule(start.toInt()) {
                if (mc.screen == null) {
                    modMessage("§etestLeft Click...")
                    if (mc.screen == null) {
                        if (clicks) {
                            leftClick()
                        }
                        if (packets) {
                            mc.connection?.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
                        }
                    }
                }
            }

            schedule(end.toInt()) {
                if (mc.screen == null) {
                    devMessage("§ctest Right Click...")
                    if (mc.screen == null) {
                        if (clicks) {
                            rightClick()
                        }
                        if (packets) {
                            val player = mc.player ?: return@schedule
                            val yRot = player.yRot
                            val xRot = player.xRot
                            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yRot, xRot))
                        }
                    }
                }
            }
        } else {
            devMessage("§cNo Hollow Wand found in hand! Aborting test.")
        }
    }

    init {
        on<ChatPacketEvent> {
            if (value.contains("[NPC] Elle: POW! SURELY THAT'S IT!")) {
                hasClickedForPhase = false
                isTriggerArmed = true

                schedule(200) {
                    isTriggerArmed = false
                }
            }
        }

        on<TickEvent.Server> {
            if (!isTriggerArmed || hasClickedForPhase) return@on
            if (!KuudraUtils.inKuudra || mc.player == null) return@on
            if (mc.screen != null) return@on
            if (packets && clicks) {
                return@on
                devMessage("You have both packets and clicks turned on retard")
            }

            val y = mc.player!!.y
            if (y !in 5.9..6.1) return@on

            val kuudra = KuudraUtils.kuudraEntity ?: return@on
            val currentHP = kuudra.health

            if (currentHP <= 25000f) {
                if (start.toInt() >= end.toInt()) {
                    devMessage("§cretard timers are wropng! Aborting to prevent bans.")
                    isTriggerArmed = false
                    return@on
                }

                hasClickedForPhase = true
                isTriggerArmed = false

                val heldItem = mc.player?.mainHandItem
                val itemName = heldItem?.hoverName?.string?.noControlCodes ?: ""

                if (itemName.contains("Hollow Wand")) {
                    devMessage("§eHollow Wand Sequence Activated!")

                    schedule(start.toInt(),true) {
                        if (mc.screen == null) {
                            if (clicks) {
                                leftClick()
                            }
                            if (packets) {
                                devMessage("LEFT CLICK PACKET")
                                mc.connection?.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
                            }
                        }
                    }

                    schedule(end.toInt(), true) {
                        if (mc.screen == null) {
                            if (clicks) {
                                rightClick()
                            }
                            if (packets) {
                                val player = mc.player ?: return@schedule
                                val yRot = player.yRot
                                val xRot = player.xRot
                                devMessage("RIGHT CLICK PACKET")
                                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yRot, xRot))
                            }
                        }
                    }
                } else {
                    devMessage("§cNo Hollow Wand found in hand! Missed phase.")
                }
            }
        }

        on<WorldEvent.Load> {
            hasClickedForPhase = false
            isTriggerArmed = false
        }
    }
}