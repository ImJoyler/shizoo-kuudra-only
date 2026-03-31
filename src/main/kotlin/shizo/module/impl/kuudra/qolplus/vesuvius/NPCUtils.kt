package shizo.module.impl.kuudra.qolplus.vesuvius

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shizo.Shizo.mc
import shizo.utils.loreString
import shizo.utils.modMessage

object NPCUtils{
    const val REACH = 4.0
    //sorry cubey BUT SLAVA ISREAL ALWAYS
    const val PREFIX = "§0§l[§f§lSlava §b§lIsrael§l§0]§f"

    fun formatCompact(num: Double): String {
        return when {
            num >= 1_000_000_000 -> "%.2fB".format(num / 1_000_000_000)
            num >= 1_000_000 -> "%.2fM".format(num / 1_000_000)
            num >= 1_000 -> "%.1fK".format(num / 1_000)
            else -> "%.0f".format(num)
        }
    }


        // this might not be the same for Croesus
    // it is :D
    val RUN_SLOTS = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
        )

    fun inMenu(partialName: String): Boolean {
        val screen = mc.screen ?: return false

        if (screen !is AbstractContainerScreen<*>) return false
        val title = screen.title.string
        return title.contains(partialName, ignoreCase = true)
    }

    fun inVesuvius() = inMenu("Vesuvius")

    // rename this after cause the name is confusing af
    fun inKuudraRunGui(): Boolean {
        val screen = mc.screen ?: return false
        if (screen !is AbstractContainerScreen<*>) return false

        val title = screen.title.string
        //return title.contains(Regex("^Kuudra - (Basic|Hot|Burning|Fiery|Infernal)$"))
        return title.matches(Regex("Kuudra - (Basic|Hot|Burning|Fiery|Infernal).*"))
    }

    fun isInvLoaded(): Boolean {
        val menu = mc.player?.containerMenu ?: return false
        val items = menu.items

        if (items.size < 45) return false
        val testSlot = items.getOrNull(items.size - 5)
        return testSlot != null && !testSlot.isEmpty
    }
    fun tryClickNPC(partialName: String, reach: Double = REACH): Boolean {
        val player = mc.player ?: return false
        if (mc.screen != null) return false

        val target = getClickableNPC(partialName) ?: return false

        if (target == null) modMessage("§cCould not find NPC: §f$partialName")

        val distSq = player.distanceToSqr(target)
        if (distSq > reach * reach) {
            modMessage("§cNPC §f$partialName §cis too far away!")
            return false
        }

        mc.gameMode?.interact(player, target, InteractionHand.MAIN_HAND)
        return true
    }
    fun getClickableNPC(partialName: String): Entity? {
        val level = mc.level ?: return null

        val allEntities = level.entitiesForRendering().toList()
        val stands = allEntities.filterIsInstance<ArmorStand>().filter {
            it.name.string.contains(partialName, ignoreCase = true)
        }

        if (stands.isEmpty()) return null

        val displayStand = stands[0]

        val npcs = allEntities.filterIsInstance<Player>().filter { p ->
            val distX = displayStand.x - p.x
            val distY = displayStand.y - p.y
            val distZ = displayStand.z - p.z

            val distSq = distX * distX + distY * distY + distZ * distZ

            p.uuid.version() == 2 && distSq < 0.01
        }

        if (npcs.isEmpty()) return null

        // prevents clicking two things at once!
        if (npcs.size > 1) {
            modMessage("Found multiple possible NPCs for $partialName?")
            return null
        }
        return npcs[0]
    }
    fun getCurrPage(): Int {
        val menu = mc.player?.containerMenu ?: return 1

        val nextItem = menu.getSlot(53).item
        val prevItem = menu.getSlot(45).item

        fun parsePage(stack: ItemStack): Int? {
            val lore = stack.loreString
            if (lore.size < 2) return null

            val pageLine = lore[1]
            val match = Regex("Page (\\d+)").find(pageLine) ?: return null
            return match.groupValues[1].toIntOrNull()
        }

        if (nextItem.hoverName.string.contains("Next Page")) {
            val page = parsePage(nextItem) ?: return 1
            return page - 1
        }

        if (prevItem.hoverName.string.contains("Previous Page")) {
            val page = parsePage(prevItem) ?: return 1
            return page + 1
        }

        return 1
    }

    fun findUnopenedKuudraRun(excludedIndexes: Set<Int>, page: Int): Triple<Int?, String?, Int> {
        val menu = mc.player?.containerMenu ?: return Triple(null, null, 0)
        var skippedCount = 0

        var hasInfernalKey = false
        val inventory = mc.player?.inventory
        if (inventory != null) {
            for (i in 0 until inventory.containerSize) {
                val stack = inventory.getItem(i)
                if (!stack.isEmpty && stack.hoverName.string.contains("Infernal Kuudra Key")) {
                    hasInfernalKey = true
                    break
                }
            }
        }

        for (slotIndex in RUN_SLOTS) {
            val extendedIndex = slotIndex + (page - 1) * 54
            if (excludedIndexes.contains(extendedIndex)) continue

            val stack = menu.getSlot(slotIndex).item
            if (stack.item != Items.PLAYER_HEAD) continue

            val lore = stack.loreString

            val isUnopened = lore.any { it.contains("Chests expire in") }
            val isLooted = lore.any { it.contains("No more chests to open") }

            if (!isUnopened || isLooted) continue

            val isInfernal = lore.any { it.contains("Infernal Tier") }

            if (isInfernal && !hasInfernalKey) {
                skippedCount++
                continue
            }

            return Triple(slotIndex, if (isInfernal) "Infernal" else "Basic", 0)
        }
        return Triple(null, null, skippedCount)
    }
     fun hasKuudraKey(): Boolean {
        val inventory = mc.player?.inventory ?: return false

        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty && stack.hoverName.string.contains("Kuudra Key")) {
                return true
            }
        }
        return false
    }

    fun jewMessage(message: Any?, prefix: String = "§0§l[§f§lSlava §b§lIsrael§l§0]§f", chatStyle: Style? = null) {
        val text = Component.literal("$prefix$message")
        chatStyle?.let { text.setStyle(chatStyle) }
        mc.execute { mc.gui?.chat?.addMessage(text) }
    }

    fun clickSlot(slotId: Int) {

        val player = mc.player ?: return
        // middle click for faster guis :D!
        mc.gameMode?.handleInventoryMouseClick(player.containerMenu.containerId, slotId, 2, ClickType.CLONE, player)

    }
}