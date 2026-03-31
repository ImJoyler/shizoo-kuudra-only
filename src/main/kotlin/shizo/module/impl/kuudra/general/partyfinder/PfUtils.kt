package shizo.module.impl.kuudra.general.partyfinder

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import shizo.utils.calculateDungeonLevel
import shizo.utils.formatNumber
import shizo.utils.modMessage
import shizo.utils.network.hypixelapi.HypixelData
import shizo.utils.toFixed
import kotlin.text.contains

object PfUtils {


     fun fetchAndDisplayKuudraStats(result: Result<HypixelData.PlayerInfo>) {
        result.fold(
            onSuccess = { playerInfo ->
                playerInfo.memberData?.let { displayKuudraStats(playerInfo.name, it) }
                    ?: modMessage("§cNo profile found for §6${playerInfo.name}§c!")
            },
            onFailure = { modMessage("§cFailed to fetch stats: ${it.message}") }
        )
    }

     fun displayKuudraStats(name: String, member: HypixelData.MemberData) {
        val sbLevel = member.level
        val sbColor = getSbLevelColor(sbLevel)
        val cataLevel = calculateDungeonLevel(member.dungeons.dungeonTypes.catacombs.experience)
        val mp = member.assumedMagicalPower

        val comps = member.kuudraCompletions
        val basic = comps["basic"] ?: 0
        val hot = comps["hot"] ?: 0
        val burning = comps["burning"] ?: 0
        val fiery = comps["fiery"] ?: 0
        val infernal = comps["infernal"] ?: 0
        val totalRuns = basic + hot + burning + fiery + infernal

        val gDrag = member.pets.pets.find { it.type.equals("GOLDEN_DRAGON", ignoreCase = true) }
        val hasGdrag = gDrag != null

        val startString = "\n§3§m▬▬▬▬▬▬▬▬▬▬▬§r §b§l[ §3$name §b§l] §3§m▬▬▬▬▬▬▬▬▬▬▬\n"
        val mainMessage = Component.literal(startString)
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal("§b§lUser Info:\n\n§8▪ §3Username: §b$name\n§8▪ §3SB Level: $sbColor$sbLevel\n§8▪ §3Purse: §e${formatNumber(member.currencies.coins.toString())}")))}

        val runsHover = Component.literal("§b§lRuns Breakdown:\n\n§3Basic: §f$basic\n§3Hot: §f$hot\n§3Burning: §f$burning\n§3Fiery: §f$fiery\n§3Infernal: §f$infernal\n\n§3§lTotal: §f$totalRuns")
        mainMessage.append(Component.literal("§8▪ §3Runs: ${getRunsColor(infernal)}$infernal §7(${getRunsColor(totalRuns)}$totalRuns§7)\n")
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(runsHover)) })

        val mpHover = Component.literal("§b§lMP Breakdown:\n\n§8▪ §3Power: §f§l${member.accessoryBagStorage.selectedPower ?: "None"}\n§8▪ §3Tunings: §7${member.tunings.joinToString(", ")}")
        mainMessage.append(Component.literal("§8▪ §3Magical Power: ${getMpColor(mp)}$mp\n")
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(mpHover)) })

        mainMessage.append(Component.literal("§8▪ §3Cata: §6${cataLevel.toFixed(2)}\n"))

        val witherBlades = findItems(member, "HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE")
        val terms = findItems(member, "TERMINATOR")
        val ragAxes = findItems(member, "RAGNAROCK_AXE")
        val kuudraTalis = findAccessories(member, "KUUDRAS_HEART", "KUUDRAS_LUNG", "KUUDRAS_KIDNEY", "KUUDRAS_STOMACH", "KUUDRAS_JAW_BONE", "KUUDRAS_MANDIBLE", "KUUDRAS_HEART_OF_FIRE", "KUUDRAS_GELATINOUS_EYE")

        val tuxPieces = findItems(member, "ELEGANT_TUXEDO_BOOTS", "ELEGANT_TUXEDO_LEGGINGS", "ELEGANT_TUXEDO_CHESTPLATE")
        val bones = findItems(member, "STARRED_BONE_BOOMERANG", "BONE_BOOMERANG")
        val terrorPieces = findItems(member, "FIERY_TERROR_BOOTS", "FIERY_TERROR_LEGGINGS", "FIERY_TERROR_CHESTPLATE", "FIERY_TERROR_HELMET", "INFERNAL_TERROR_BOOTS", "INFERNAL_TERROR_CHESTPLATE", "INFERNAL_TERROR_LEGGINGS", "INFERNAL_TERROR_HELMET")

        mainMessage.append(buildItemHoverComponent("Hyperion", witherBlades))
        mainMessage.append(buildItemHoverComponent("Terminator", terms,2))
        mainMessage.append(buildItemHoverComponent("Ragnarock Axe", ragAxes))

        val heartDisplayName = kuudraTalis.firstOrNull()?.name ?: "Kuudra's Heart"
        mainMessage.append(buildItemHoverComponent(heartDisplayName, kuudraTalis))

        mainMessage.append(Component.literal("\n§8▪ §3Meta: "))
        mainMessage.append(buildInlineHover("Fiery+ Terror", "Fiery+ Terror", terrorPieces))
        mainMessage.append(Component.literal(" §8| §3Util: "))
        mainMessage.append(buildInlineHover("Elegant Tuxedo", "Eleg Tux", tuxPieces))
        mainMessage.append(Component.literal(" §8| "))
        mainMessage.append(buildInlineHover("Bonemerang", "Bone", bones))
        mainMessage.append(Component.literal("\n"))

        val armor = getArmorPieces(member)
        val helm = armor.find { it.slot == "⛑" }?.itemStack
        val chest = armor.find { it.slot == "\uD83D\uDEE1" }?.itemStack
        val legs = armor.find { it.slot == "\uD83D\uDC56" }?.itemStack
        val boots = armor.find { it.slot == "\uD83D\uDC62" }?.itemStack

        mainMessage.append(buildArmorHover(helm, "helmet"))
        mainMessage.append(buildArmorHover(chest, "chestplate"))
        mainMessage.append(buildArmorHover(legs, "leggings"))
        mainMessage.append(buildArmorHover(boots, "boots"))

        val petStr = if (hasGdrag) "§a✔ Golden Dragon" else "§c✖ No GDrag"
        val activePetName = member.pets.activePet?.type?.replace("_", " ")?.lowercase()?.split(" ")?.joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } } ?: "None"
        val petHover = Component.literal("§b§lPets Info:\n\n§8▪ §3Bank: §f${formatNumber(member.currencies.coins.toString())}\n§8▪ §3Owns GDrag: ${if (hasGdrag) "§aYes" else "§cNo"}\n§8▪ §3Active Pet: §f$activePetName")

        mainMessage.append(Component.literal("\n§8▪ §3Pet: $petStr\n")
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(petHover)) })

        mainMessage.append(Component.literal("§3§m▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n"))

        modMessage(mainMessage, "")
    }

     fun buildItemHoverComponent(name: String, items: List<HypixelData.ItemData>, requiredCount: Int = 1): Component {
        val status = when {
            items.size >= requiredCount -> "§a✔"
            items.isNotEmpty() -> "§e${items.size}/$requiredCount"
            else -> "§c✖"
        }

        val component = Component.literal("\n§8▪ §b$name§r: $status")

        if (items.isNotEmpty()) {
            val hoverText = Component.literal("")
            items.forEachIndexed { index, item ->
                hoverText.append(Component.literal("${item.name}\n"))
                item.lore.forEach { hoverText.append(Component.literal(it + "\n")) }
                if (index < items.size - 1) hoverText.append(Component.literal("\n§b§m----------------------\n\n"))
            }
            component.withStyle { it.withHoverEvent(HoverEvent.ShowText(hoverText)) }
        } else {
            val cleanName = name.replace(Regex("§[0-9a-fk-or]"), "")
            component.withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal("§cPlayer does not have $cleanName."))) }
        }
        return component
    }

     fun buildInlineHover(fullName: String, shortName: String, items: List<HypixelData.ItemData>): Component {
        val statusStr = if (items.isNotEmpty()) "§a✔ $shortName" else "§c✖ $shortName"
        val component = Component.literal(statusStr)

        if (items.isNotEmpty()) {
            val hoverText = Component.literal("")
            items.forEachIndexed { index, item ->
                hoverText.append(Component.literal("${item.name}\n"))
                item.lore.forEach { hoverText.append(Component.literal(it + "\n")) }
                if (index < items.size - 1) hoverText.append(Component.literal("\n§b§m----------------------\n\n"))
            }
            component.withStyle { it.withHoverEvent(HoverEvent.ShowText(hoverText)) }
        } else {
            component.withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal("§cPlayer does not have $fullName."))) }
        }
        return component
    }

     fun buildArmorHover(item: HypixelData.ItemData?, type: String): Component {
        val name = item?.name?.replace("✿ ", "") ?: "§cNo $type"
        val component = Component.literal("\n§8  ▸ §b$name")

        if (item != null) {
            val hover = Component.literal("$name\n\n")

            val ultimate = item.lore.find { it.contains("§d§l") }?.trim() ?: "§8✖ No Ultimate Enchant"
            hover.append(Component.literal("$ultimate\n§3§m----------------------\n"))

            var linesAdded = 0
            val statPrefixes = listOf("health:", "defense:", "strength:", "speed:", "crit", "intelligence:", "magic find:", "true defense:", "ferocity:", "bonus attack")

            for (line in item.lore) {
                val cleanLine = line.replace(Regex("§[0-9a-fk-or]"), "").trim().lowercase()

                if (cleanLine.startsWith("full set bonus") || cleanLine.startsWith("piece bonus") || cleanLine.contains("gemstone slots")) {
                    break
                }

                if (line.contains("§d§l") || line.isBlank()) continue
                if (statPrefixes.any { cleanLine.startsWith(it) }) continue

                hover.append(Component.literal(line + "\n"))
                linesAdded++

                if (linesAdded >= 10) {
                    hover.append(Component.literal("§8... (truncated) ...\n"))
                    break
                }
            }

            component.withStyle { it.withHoverEvent(HoverEvent.ShowText(hover)) }
        }
        return component
    }
     fun buildWeaponComponent(name: String, hasWeapon: Boolean): Component {
        val status = if (hasWeapon) "§a✔" else "§4X"
        return Component.literal("\n§a$name: $status").withStyle {
            if (hasWeapon) it.withHoverEvent(HoverEvent.ShowText(Component.literal("§aPlayer has $name in inventory/enderchest.")))
            else it
        }
    }

     data class ArmorPiece(val slot: String, val itemStack: HypixelData.ItemData?)


     fun getArmorPieces(member: HypixelData.MemberData) = member.inventory?.invArmor?.itemStacks
        ?.takeIf { it.size >= 4 }
        ?.let { listOfNotNull(
            it[3]?.let { stack -> ArmorPiece("⛑", stack) },
            it[2]?.let { stack -> ArmorPiece("\uD83D\uDEE1", stack) },
            it[1]?.let { stack -> ArmorPiece("\uD83D\uDC56", stack) },
            it[0]?.let { stack -> ArmorPiece("\uD83D\uDC62", stack) }
        )} ?: emptyList()



     fun findItems(member: HypixelData.MemberData, vararg itemIds: String): List<HypixelData.ItemData> {
        return member.allItems.filterNotNull().filter { item ->
            itemIds.any { id -> item.id.contains(id) }
        }
    }

     fun findAccessories(member: HypixelData.MemberData, vararg itemIds: String): List<HypixelData.ItemData> {
        val talismanBag = member.inventory?.bagContents?.get("talisman_bag")?.itemStacks ?: return emptyList()
        return talismanBag.filterNotNull().filter { item ->
            itemIds.any { id -> item.id.contains(id) }
        }
    }

     fun getSbLevelColor(level: Int) = when (level / 40) {
        0 -> "§7" ; 1 -> "§f" ; 2 -> "§e" ; 3 -> "§a"
        4 -> "§2" ; 5 -> "§b" ; 6 -> "§3" ; 7 -> "§9"
        8 -> "§d" ; 9 -> "§5" ; 10 -> "§6" ; 11 -> "§c"
        else -> "§4"
    }

     fun getMpColor(mp: Int) = when {
        mp < 1350 -> "§4" ; mp < 1600 -> "§e" ; mp < 1750 -> "§2" ; else -> "§3"
    }

     fun getRunsColor(runs: Int) = when {
        runs < 500 -> "§4" ; runs < 1000 -> "§e" ; runs < 5000 -> "§2" ; else -> "§3"
    }


}