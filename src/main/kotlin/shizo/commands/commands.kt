package shizo.commands


import com.github.stivais.commodore.Commodore

import com.github.stivais.commodore.utils.SyntaxException
import net.minecraft.commands.Commands
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import shizo.Shizo.mc
import shizo.clickgui.ClickGUI
import shizo.clickgui.HudManager
import shizo.module.impl.render.ClickGUIModule
import shizo.module.impl.ModuleManager
import shizo.utils.*
import shizo.utils.handlers.schedule
import shizo.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import shizo.clickgui.ModernGUI
import shizo.events.PacketEvent
import shizo.events.core.EventBus
import shizo.events.core.on
import shizo.events.core.onReceive


fun logPlayerCommandActions() {
    ServerboundPlayerCommandPacket.Action.entries.forEach {
        shizo.Shizo.logger.info("Action: ${it.name}")
    }
}
val mainCommand = Commodore("shizo", "joy","cubey","sz") {
    runs {
        schedule(0) {
            if (ClickGUIModule.theme.value == 0) {
                mc.setScreen(ClickGUI)
            } else {
                mc.setScreen(ModernGUI)
            }
        }
    }

    literal("edithud").runs {
        schedule(0) { mc.setScreen(HudManager) }
    }
    literal("magic2").runs {
        doJump()
    }

    literal("help").runs {
        modMessage("§b§lShizo Commands:")
        modMessage("§9/shizo §f- Opens the ClickGUI")
        modMessage("§9/shizo edithud §f- Edit HUD positions")
        modMessage("§9/shizo reset module <name> §f- Reset a module's settings")
        modMessage("§9/shizo reset clickgui §f- Reset GUI positions")
        modMessage("§9/shizo reset hud §f- Reset HUD positions")
    }
    literal("reset") {
        literal("module").executable {
            param("moduleName") {
                suggests { ModuleManager.modules.keys.map { it.replace(" ", "_") } }
            }
            runs { moduleName: String ->
                val module = ModuleManager.modules[moduleName.replace("_", " ")]
                    ?: throw SyntaxException("Module not found.")

                module.settings.forEach { (_, setting) -> setting.reset() }
                modMessage("§aSettings for module §f${module.name} §ahas been reset to default values.")
            }
        }

        literal("clickgui").runs {
            ClickGUIModule.resetPositions()
            modMessage("Reset click gui positions.")
        }

        literal("hud").runs {
            HudManager.resetHUDS()
            modMessage("Reset HUD positions.")
        }
    }

    literal("ep").runs {
        fillItemFromSack(16, "ENDER_PEARL", "ender_pearl", true)
    }

    literal("ij").runs {
        fillItemFromSack(64, "INFLATABLE_JERRY", "inflatable_jerry", true)
    }

    literal("sl").runs {
        fillItemFromSack(16, "SPIRIT_LEAP", "spirit_leap", true)
    }

    literal("sb").runs {
        fillItemFromSack(64, "SUPERBOOM_TNT", "superboom_tnt", true)
    }


//
//    on<PacketEvent.Receive> {
//        if (sneak && packet is ClientboundSetEntityDataPacket) {
//            if (packet.id == mc.player?.id) {
//                val isSneakingOnServer = packet.packedItems?.any {
//                    it.id == 0 && (it.value as Byte).toInt() and 0x02 != 0
//                } ?: false
//                devMessage ("Sneaking" + isSneakingOnServer)
//
//                if (isSneakingOnServer) {
//                    modMessage("sneaking!")
//                    sneak = false
//
//                    mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, 6F, 43F))
//                    mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, 5F, 53F))
//                    mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, 4F, 33F))
//                    mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, 3F, 13F))
//
//                    modMessage("Warped!")
//                    sneak = false
//                }
//            }
//        }
//    }
//
//    literal("magic").runs {
//        modMessage("Test")
//        schedule(17) {setSneaking(true)}
//        schedule(20) {
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,0,0F,-90F))
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,1,28F,1F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,2,14F,1F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,1,40F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,2,40F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,3,40F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,4,40F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,5,40F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,6,40F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,7,40F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,8,40F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,9,0F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,10,0F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,11,0F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,12,0F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,13,0F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,14,0F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,15,0F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,16,0F,0F))
//                mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,17,0F,0F))
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,10,0F,0F))
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,11,0F,0F))
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,12,0F,0F))
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,13,0F,0F))
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,14,0F,0F))
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,15,0F,0F))
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,16,0F,0F))
//            mc.connection?.send(ServerboundUseItemPacket(InteractionHand.MAIN_HAND,17,0F,0F))
//        }
//    }

    literal("debugitem").runs { // debug with ai cause i cba to type shit shit out icl
        val player = mc.player ?: return@runs
        val item = player.mainHandItem

        if (item.isEmpty) {
            modMessage("§cYou aren't holding anything!")
            return@runs
        }
        val sbID = item.itemId
        val loreLines = item.loreString

        modMessage("§b§lItem:")
        modMessage("§9Display Name: §f${item.hoverName.string}")
        if (sbID.isNotEmpty()) {
            modMessage("§9Skyblock ID: §f$sbID")
        } else {
            modMessage("§cNo Sb ID found .")
        }
        modMessage("§9Lore Line Count: §f${loreLines.size}")
        if (loreLines.isNotEmpty()) {
            modMessage("§8First line of lore: §7${loreLines.firstOrNull()}")
        }
    }

    // debug pt 2
    literal("getclass").runs {
        val inDungeon = DungeonUtils.inDungeons
        val currentClass = DungeonUtils.currentDungeonPlayer.clazz
        val name = mc.player?.name?.string ?: "Unknown"

        modMessage("§b§lDungeon Debug:")
        modMessage("§9In Dungeons: §f$inDungeon")
        modMessage("§9Detected Class: §f$currentClass")
        modMessage("§9Player Name: §f$name")

        if (!inDungeon) {
            modMessage("§cWarning: Not currently in a dungeon. Class detection may be 'Unknown'.")
        }
    }
//    literal("droptest").runs {
//        modMessage("§eYeah its debug time idk")
//        schedule(1,false, {
//            modMessage("Schedule debug")
//        })
//    }

    literal("jump").runs {
        modMessage("§eYeah its debug time idk")
        schedule(1){
            doJump()
        }
    }

    literal("freeze").runs {
        modMessage("testing freeze")
        TimerSpeedController.freezeForMillis(500)
    }
}

val dungeonHub = Commodore("dh", "d") {
    runs { sendCommand("warp dh") }
}

val floorCommands = mapOf(
    1 to "joindungeon catacombs 1",
    2 to "joindungeon catacombs 2",
    3 to "joindungeon catacombs 3",
    4 to "joindungeon catacombs 4",
    5 to "joindungeon catacombs 5",
    6 to "joindungeon catacombs 6",
    7 to "joindungeon catacombs 7",
)

val floorJoinCommand: Array<Commodore> =
    floorCommands.map { (floor, cmd) ->
        Commodore("f$floor") {
            runs { sendCommand(cmd) }
        }
    }.toTypedArray()

val masterCommands = mapOf(
    1 to "joindungeon master_catacombs 1",
    2 to "joindungeon master_catacombs 2",
    3 to "joindungeon master_catacombs 3",
    4 to "joindungeon master_catacombs 4",
    5 to "joindungeon master_catacombs 5",
    6 to "joindungeon master_catacombs 6",
    7 to "joindungeon master_catacombs 7",
)

val masterJoinCommand: Array<Commodore> =
    masterCommands.map { (floor, cmd) ->
        Commodore("m$floor") {
            runs { sendCommand(cmd) }
        }
    }.toTypedArray()

val kuudraCommands = mapOf(
    1 to "joindungeon kuudra_normal",
    2 to "joindungeon kuudra_hot",
    3 to "joindungeon kuudra_burning",
    4 to "joindungeon kuudra_fiery",
    5 to "joindungeon kuudra_infernal"
)

val kuudraJoinCommand: Array<Commodore> =
    kuudraCommands.map { (tier, cmd) ->
        Commodore("t$tier") {
            runs { sendCommand(cmd) }
        }
    }.toTypedArray()