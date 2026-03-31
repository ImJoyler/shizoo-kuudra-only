//package shizo.commands
//
//import com.github.stivais.commodore.Commodore
//import net.minecraft.core.BlockPos
//import shizo.module.impl.cheats.BreakerAura
//import shizo.module.impl.cheats.DungeonBreakerConfig
//import shizo.utils.modMessage
//import shizo.utils.skyblock.dungeon.DungeonUtils
//
//val dungeonBreakerCommand = Commodore("dungeonbreaker", "db", "breaker") {
//    runs {
//        modMessage("§c§lDungeon Breaker Commands:")
//        modMessage("§c/db clear §f- Remove ALL blocks for the current room.")
//        modMessage("§c/db add <x> <y> <z> §f- Add a specific block coordinate.")
//        modMessage("§c/db remove <x> <y> <z> §f- Remove a specific block coordinate.")
//        modMessage("§c/db list §f- Show how many blocks are set for this room.")
//    }
//
//    literal("clear").runs {
//        val room = DungeonUtils.currentRoom ?: return@runs modMessage("§cYou must be in a dungeon room to use this.")
//        val roomName = room.data.name
//
//        if (DungeonBreakerConfig.breakables.remove(roomName) != null) {
//            DungeonBreakerConfig.saveConfig()
//            BreakerAura.updateCurrentRoomBlocks()
//            modMessage("§aCleared all breaker blocks for room: §f$roomName")
//        } else {
//            modMessage("§cNo configuration found for room: §f$roomName")
//        }
//    }
//
//    literal("add").runs { x: Int, y: Int, z: Int ->
//        val room = DungeonUtils.currentRoom ?: return@runs modMessage("§cYou must be in a dungeon room.")
//
//        val realPos = BlockPos(x, y, z)
//        val relPos = room.getRelativeCoords(realPos)
//        val roomName = room.data.name
//
//        val list = DungeonBreakerConfig.breakables.getOrPut(roomName) { mutableListOf() }
//
//        if (!list.contains(relPos)) {
//            list.add(relPos)
//            DungeonBreakerConfig.saveConfig()
//            BreakerAura.updateCurrentRoomBlocks()
//            modMessage("§aAdded block at §f$x, $y, $z §7(Rel: ${relPos.toShortString()})")
//        } else {
//            modMessage("§cBlock at §f$x, $y, $z §cis already in the list.")
//        }
//    }
//
//    literal("remove").runs { x: Int, y: Int, z: Int ->
//        val room = DungeonUtils.currentRoom ?: return@runs modMessage("§cYou must be in a dungeon room.")
//
//        val realPos = BlockPos(x, y, z)
//        val relPos = room.getRelativeCoords(realPos)
//        val roomName = room.data.name
//
//        val list = DungeonBreakerConfig.breakables[roomName]
//
//        if (list != null && list.remove(relPos)) {
//            DungeonBreakerConfig.saveConfig()
//            BreakerAura.updateCurrentRoomBlocks()
//            modMessage("§bRemoved block at §f$x, $y, $z")
//        } else {
//            modMessage("§cNo block found at §f$x, $y, $z §cin the config.")
//        }
//    }
//
//    literal("list").runs {
//        val room = DungeonUtils.currentRoom ?: return@runs modMessage("§cNot in a dungeon room.")
//        val list = DungeonBreakerConfig.breakables[room.data.name]
//
//        if (list.isNullOrEmpty()) {
//            modMessage("§7No blocks configured for room: §f${room.data.name}")
//        } else {
//            modMessage("§aRoom: §f${room.data.name}")
//            modMessage("§aBlock Count: §f${list.size}")
//            modMessage("§7(Use Dev Mode to see highlights)")
//        }
//    }
//}