package shizo.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import shizo.module.impl.render.customesp.CustomESP
import shizo.module.impl.render.customesp.CustomESPConfig
import shizo.module.impl.render.customesp.ESPRule
import shizo.module.impl.render.customesp.MatchType
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.modMessage

// yes command was ai made icba
val espCommand = Commodore("esp") {

    runs {
        showHelpMenu()
    }

    literal("help").runs {
        showHelpMenu()
    }

    literal("add").apply {

        // Format: /esp add name <mobName> [#hexColor]
        literal("name").runs { input: GreedyString ->
            parseInput(MatchType.NAME, null, input.string)
        }

        // Format: /esp add <piece> <itemName> <mobName> [#hexColor]
        literal("helmet").runs { item: String, input: GreedyString ->
            parseInput(MatchType.HELMET, item, input.string)
        }

        literal("chestplate").runs { item: String, input: GreedyString ->
            parseInput(MatchType.CHESTPLATE, item, input.string)
        }

        literal("leggings").runs { item: String, input: GreedyString ->
            parseInput(MatchType.LEGGINGS, item, input.string)
        }

        literal("boots").runs { item: String, input: GreedyString ->
            parseInput(MatchType.BOOTS, item, input.string)
        }

        literal("held").runs { item: String, input: GreedyString ->
            parseInput(MatchType.HELD, item, input.string)
        }
    }

    literal("remove").runs { input: GreedyString ->
        val mobName = input.string.trim().removeSurrounding("\"")
        val lowercase = mobName.lowercase()
        val removed = CustomESP.rules.removeIf { it.entityName == lowercase }

        if (removed) {
            modMessage("§aRemoved all rules for §e$mobName§a.")
            CustomESPConfig.saveConfig()
        } else {
            modMessage("§cNo rules found for §e$mobName§c.")
        }
    }

    literal("clear").runs {
        CustomESP.rules.clear()
        modMessage("§aCustom ESP list cleared.")
        CustomESPConfig.saveConfig()
    }

    literal("list").runs {
        if (CustomESP.rules.isEmpty()) return@runs modMessage("§cCustom ESP list is empty.")

        modMessage("§8§m----------------------------------------")
        modMessage("§6§l✦ Active ESP Rules ✦")
        CustomESP.rules.forEach { rule ->
            val condition = if (rule.t == MatchType.NAME) "NAME ONLY" else "${rule.t}: ${rule.itemName}"
            modMessage("§8» §e${rule.entityName} §7[$condition]")
        }
        modMessage("§8§m----------------------------------------")
    }
}


private fun showHelpMenu() {
    modMessage("§8§m----------------------------------------")
    modMessage("§6§l✦ Custom ESP Module ✦")
    modMessage("§7Highlight specific mobs by name or equipment.")
    modMessage("")
    modMessage("§e§lCommands:")
    modMessage("§8» §a/esp add name §b<mob> §7[#hex]")
    modMessage("§8» §a/esp add <piece> §b<item> <mob> §7[#hex]")
    modMessage("    §8└ §7Pieces: §ehelmet, chestplate, leggings, boots, held")
    modMessage("")
    modMessage("§8» §a/esp list   §7- View your active rules")
    modMessage("§8» §a/esp remove §b<mob> §7- Delete rules for a mob")
    modMessage("§8» §a/esp clear  §7- Delete ALL rules")
    modMessage("")
    modMessage("§c§l⚠ Important Info:")
    modMessage("§7• If a name or item has spaces, wrap it in quotes: §f\"Shadow Assassin\"")
    modMessage("§7• Colors are optional! The default color is Aqua.")
    modMessage("")
    modMessage("§e§lExamples:")
    modMessage("§7- §a/esp add name bat §c#ff0000")
    modMessage("§7- §a/esp add boots \"Perfect\" \"Diamond Guy\"")
    modMessage("§7- §a/esp add held \"Terminator\" \"Master Deathmite\"")
    modMessage("§8§m----------------------------------------")
}

private fun parseInput(type: MatchType, itemName: String?, remainingInput: String) {
    val colorRegex = Regex("^(.*?)(?:\\s+#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8}))?$")
    val matchResult = colorRegex.matchEntire(remainingInput.trim()) ?: return modMessage("§cInvalid format.")

    val (mobName, colorCode) = matchResult.destructured

    val finalMobName = mobName.trim().removeSurrounding("\"")
    val finalItemName = itemName?.trim()?.removeSurrounding("\"")

    if (finalMobName.isEmpty()) return modMessage("§cMob name cannot be empty.")

    addRule(type, finalMobName, finalItemName, colorCode.ifEmpty { null })
}

private fun addRule(type: MatchType, mobName: String, itemName: String?, hexCode: String?) {
    val lowercaseMob = mobName.lowercase()
    val lowercaseItem = itemName?.lowercase()

    val color = if (hexCode != null) {
        val cleanHex = hexCode.replace("#", "")
        if (!cleanHex.matches(Regex("^[0-9a-fA-F]{6}|[0-9a-fA-F]{8}$"))) {
            return modMessage("§cInvalid color format. Use RRGGBB or #RRGGBB.")
        }
        try {
            Color(cleanHex.padEnd(8, 'f'))
        } catch (e: Exception) {
            Colors.WHITE
        }
    } else {
        Colors.MINECRAFT_AQUA
    }

    if (CustomESP.rules.any { it.t == type && it.entityName == lowercaseMob && it.itemName == lowercaseItem }) {
        return modMessage("§cThat rule already exists!")
    }

    CustomESP.rules.add(ESPRule(type, lowercaseMob, lowercaseItem, color))
    modMessage("§aAdded ESP for §e$mobName §a(Rule: §7${type.name}§a)")
    CustomESPConfig.saveConfig()
}