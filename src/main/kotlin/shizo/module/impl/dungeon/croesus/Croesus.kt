package shizo.module.impl.dungeon.croesus

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import shizo.Shizo
import shizo.Shizo.scope
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.ActionSetting
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.clickgui.settings.impl.StringSetting
import shizo.events.ChatPacketEvent
import shizo.events.GuiEvent
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.module.impl.kuudra.qolplus.vesuvius.NPCUtils.RUN_SLOTS
import shizo.module.impl.kuudra.qolplus.vesuvius.NPCUtils.clickSlot
import shizo.module.impl.kuudra.qolplus.vesuvius.NPCUtils.getCurrPage
import shizo.module.impl.kuudra.qolplus.vesuvius.NPCUtils.tryClickNPC
import shizo.utils.Colors
import shizo.utils.alert
import shizo.utils.customData
import shizo.utils.devMessage
import shizo.utils.equalsOneOf
import shizo.utils.itemId
import shizo.utils.lore
import shizo.utils.loreString
import shizo.utils.modMessage
import shizo.utils.network.WebUtils.fetchJson
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.getStringWidth
import shizo.utils.renderUtils.renderUtils.text
import shizo.utils.renderUtils.renderUtils.textDim
import shizo.utils.romanToInt
import shizo.utils.skyblock.dungeon.DungeonUtils
// todo black list
object Croesus : Module(
    name = "Croesus",
    description = "Enhances the Croesus chest menu with profit calculations, Kismets, and Auto-Claim."
) {
    private val clickDelay by NumberSetting("Click Delay", 500.0, 100.0, 2000.0, 50.0, desc = "Delay between actions in ms.")

    private val useKismets by BooleanSetting("Use Kismets", false, desc = "Automatically reroll Bedrock chests if profit is too low.")
    private val kismetMinProfit by NumberSetting("Kismet Min Profit", 2_000_000.0, 0.0, 10_000_000.0, 100_000.0, desc = "Reroll if Bedrock chest profit is below this.").withDependency { useKismets }
    private val kismetFloors by StringSetting(
        "Kismet Floors",
        "F7, M7",
        desc = "Comma separated list of floors to reroll on."
    ).withDependency { useKismets }

    private val highlightState by BooleanSetting("Highlight State", true, desc = "Highlights chests in the Croesus menu based on their claim status.")
    private val highlightProfitable by BooleanSetting("Highlight Profitable", true, desc = "Highlights the most and 2nd most profitable chests.")
    private val includeEssence by BooleanSetting("Include Essence", true, desc = "Includes essence value in profit calculations.")
    private val hideClaimed by BooleanSetting("Hide Claimed", true, desc = "Hides chests that have already been claimed.")
    private val includeKey by BooleanSetting("Include Key", desc = "Count Dungeon Chest Key as unclaimed.").withDependency { hideClaimed }
    private val minimized by BooleanSetting("Minimized", false, desc = "Only display profit for each chest instead of all items.")

    private val croesusHud by HUD("Croesus Chest HUD", "Displays all chest contents with prices, sorted by profit.") {
        if (!it) return@HUD 0 to 0
        drawOverlay(true)
    }

    private val chestCount by HUD("Chest Count HUD", "Displays the number of chests opened in the current Croesus session.") {
        if (DungeonUtils.inDungeons || it) textDim("§6Chests: §a${currentChestCount}", 0, 0)
        else 0 to 0
    }

    private val chestWarning by NumberSetting("Chest Warning Threshold", 55, 0, 60, desc = "Displays a warning in the chest profit HUD if the profit is below this amount.")
    private val refresh by ActionSetting("Refresh Prices", desc = "Manually refresh the cached prices used for profit calculations.") {
        scope.launch {
            cachedPrices = fetchJson<Map<String, Double>>("https://api.odtheking.com/lb/lowestbins").getOrElse { Shizo.logger.error("Failed to fetch lowest bin prices.", it); emptyMap() }
            modMessage("§aCroesus prices refreshed.")
        }
    }

    var cachedPrices = emptyMap<String, Double>()
    private var currentChestCount = 0

    private var autoClaimToggle = false
    private var currentState = CroesusState.IDLE
    private var lastActionTime = 0L
    private val failedRuns = mutableSetOf<Int>()
    private var currentRunId = -1
    private var skipKismetForCurrentRun = false
    private var currentRunFloor = ""

    private val chestNameRegex = Regex(".*(Wood|Iron|Gold|Diamond|Emerald|Obsidian|Bedrock).*")
    private val previewEnchantedBookRegex = Regex("^Enchanted Book \\(?([\\w ]+) (\\w+)\\)$")
    private val chestPreviewScreenRegex = Regex("^(?:Master )?Catacombs - ([FloorVI\\d ]*)$")
    private val chestStatusRegex = Regex("^Opened Chest: (.+)$|^No more chests to open!$")
    private val chestOpenedRegex = Regex("^Opened Chest: (.+)$")
    private val unclaimedChestsRegex = Regex("^ Unclaimed chests: (\\d+)$")
    private val chestEnchantsRegex = Regex("^\\{([a-zA-Z0-9_]+):(\\d+)}$")
    private val previewEssenceRegex = Regex("^(\\w+) Essence x(\\d+)$")
    private val previewShardRegex = Regex("^([A-Za-z ]+) Shard x1$")
    private val extraStatsRegex = Regex(" {29}> EXTRA STATS <")
    private val chestCostRegex = Regex("^([\\d,]+) Coins$")
    private val shardRegex = Regex("^([A-Za-z ]+) Shard$")

    private val ultimateEnchants = setOf(
        "Soul Eater", "Combo", "Legion", "One For All", "Rend",
        "Bank", "Swarm", "Last Stand", "Wisdom", "No Pain No Gain"
    )

    private val alwaysBuy = setOf(
        "NECRON_HANDLE", "DARK_CLAYMORE", "GIANTS_SWORD",
        "WITHER_SHIELD_SCROLL", "IMPLOSION_SCROLL", "SHADOW_WARP_SCROLL",
        "DYE_NECRON", "DYE_LIVID", "ENCHANTMENT_ULTIMATE_ONE_FOR_ALL_1"
    )

    private data class ChestItem(val name: String, val price: Double)
    private data class ChestData(val name: Component, val items: List<ChestItem>, val profit: Double, val slotIndex: Int, val isOpened: Boolean)

    private var chestData = listOf<ChestData>()
    private var currentChestProfit: Double? = null
    private var mostProfitableSlots = setOf<Int>()

    init {
        scope.launch {
            cachedPrices = fetchJson<Map<String, Double>>("https://api.odtheking.com/lb/lowestbins").getOrElse { emptyMap() }
        }

        onReceive<ClientboundContainerSetSlotPacket> {
            if (!autoClaimToggle || mc.screen == null) return@onReceive
            val screenTitle = mc.screen!!.title.string
            val menu = (mc.screen as? AbstractContainerScreen<*>)?.menu ?: return@onReceive

            if (screenTitle.matches(chestPreviewScreenRegex) || screenTitle == "The Catacombs" || screenTitle == "Master Mode The Catacombs") {
                handleCroesusScreen(menu.items)
            }

            if (screenTitle.matches(chestNameRegex)) {
                handleChestContents(menu.items)

                if (currentState == CroesusState.WAITING_FOR_CHEST && slot == 31 && item.item == Items.CHEST) {
                    devMessage("§aReward Chest loaded! Queuing claim...")
                    currentState = CroesusState.CLAIMING
                }
            }
        }

        onReceive<ClientboundOpenScreenPacket> {
            mostProfitableSlots = emptySet()
            currentChestProfit = null
            chestData = emptyList()
        }

        on<TickEvent.Start> {
            if (!autoClaimToggle || mc.player == null) {
                if (currentState != CroesusState.IDLE) stopScript()
                return@on
            }

            if (InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_ESCAPE) || mc.options.keyShift.isDown) {
                stopScript()
                modMessage("§cAuto Croesus aborted by user!")
                return@on
            }

            if (currentState != CroesusState.IDLE && System.currentTimeMillis() - lastActionTime > 5000L) {
                devMessage("§cServer timed out! Aborting to prevent softlock.")
                failedRuns.add(currentRunId)
                if (mc.screen != null) mc.player?.closeContainer()
                currentState = CroesusState.SEARCHING_NPC
                lastActionTime = System.currentTimeMillis()
                return@on
            }

            if (System.currentTimeMillis() - lastActionTime < clickDelay.toLong()) return@on

            when (currentState) {
                CroesusState.IDLE -> {
                    failedRuns.clear()
                    currentState = CroesusState.SEARCHING_NPC
                }

                CroesusState.SEARCHING_NPC -> {
                    if (mc.screen != null) {
                        mc.player?.closeContainer()
                        lastActionTime = System.currentTimeMillis()
                        return@on
                    }
                    if (tryClickNPC("Croesus", 16.0)) {
                        lastActionTime = System.currentTimeMillis()
                        currentState = CroesusState.WAITING_FOR_MAIN
                    }
                }

                CroesusState.WAITING_FOR_MAIN -> {
                    if (mc.screen?.title?.string == "Croesus") {
                        lastActionTime = System.currentTimeMillis()
                        currentState = CroesusState.SCANNING_PAGES
                    }
                }

                CroesusState.SCANNING_PAGES -> {
                    val menu = mc.player?.containerMenu ?: return@on
                    if (menu.items.size < 54) return@on

                    val page = getCurrPage()
                    var found = false

                    for (slot in RUN_SLOTS) {
                        val runId = slot + (page * 100)
                        if (failedRuns.contains(runId)) continue

                        val item = menu.getSlot(slot).item
                        if (item.item != Items.PLAYER_HEAD) continue

                        val lore = item.loreString
                        if (lore.any { it.contains("No more chests to open!") || it.contains("Opened Chest:") }) {
                            failedRuns.add(runId)
                            continue
                        }

                        val isMaster = item.hoverName.string.contains("Master Mode")
                        val floorMatch = lore.firstOrNull { it.contains("Floor") }

                        currentRunFloor = if (floorMatch != null) {
                            val numStr = floorMatch.substringAfter("Floor ").substringBefore(" ").trim()
                            if (isMaster) "M${romanToInt(numStr)}" else "F${romanToInt(numStr)}"
                        } else {
                            "UNKNOWN"
                        }

                        currentRunId = runId
                        skipKismetForCurrentRun = false
                        devMessage("§8Opening Run $currentRunId on Floor $currentRunFloor (Slot $slot)")
                        clickSlot(slot)
                        lastActionTime = System.currentTimeMillis()
                        currentState = CroesusState.ANALYZING_RUN
                        found = true
                        break
                    }

                    if (!found) {
                        val nextSlot = menu.getSlot(53).item
                        if (nextSlot.item == Items.ARROW && nextSlot.hoverName.string.contains("Next Page")) {
                            clickSlot(53)
                            lastActionTime = System.currentTimeMillis()
                            currentState = CroesusState.WAITING_FOR_PAGE
                        } else {
                            modMessage("§aAll Croesus runs checked! Finished.")
                            autoClaimToggle = false
                            stopScript()
                        }
                    }
                }

                CroesusState.WAITING_FOR_PAGE -> {
                    if (System.currentTimeMillis() - lastActionTime > clickDelay.toLong()) {
                        currentState = CroesusState.SCANNING_PAGES
                    }
                }

                CroesusState.ANALYZING_RUN -> {
                    val screen = mc.screen ?: return@on
                    val title = screen.title.string
                    val isRunGui = title.matches(chestPreviewScreenRegex) || title == "The Catacombs" || title == "Master Mode The Catacombs"

                    if (!isRunGui) return@on
                    val menu = mc.player?.containerMenu ?: return@on

                    if (chestData.isEmpty()) return@on
                    var actualFloor = "UNKNOWN"

                    if (title.contains("Floor")) {
                        val isMaster = title.contains("Master")
                        val numeral = title.substringAfter("Floor ").trim()
                        actualFloor = "${if (isMaster) "M" else "F"}${romanToInt(numeral)}"
                    }

                    val bedrockChest = chestData.find { it.name.string.contains("Bedrock") }
                    val allowedFloors = kismetFloors.split(",").map { it.trim().uppercase() }
                    val canKismetOnThisFloor = allowedFloors.contains(actualFloor)

                    if (useKismets && canKismetOnThisFloor && !skipKismetForCurrentRun && bedrockChest != null && !bedrockChest.isOpened && bedrockChest.profit < kismetMinProfit) {
                        devMessage("§eBedrock profit too low (${"%,.0f".format(bedrockChest.profit)}). Floor: $actualFloor. Opening to reroll...")
                        clickSlot(bedrockChest.slotIndex)
                        lastActionTime = System.currentTimeMillis()
                        currentState = CroesusState.REROLLING
                        return@on
                    }

                    val unopenedChests = chestData.filter { !it.isOpened }.sortedByDescending { it.profit }
                    val bestChest = unopenedChests.firstOrNull()

                    if (bestChest != null) {
                        devMessage("§aClicking best chest: ${bestChest.name.string.noControlCodes} (Profit: ${"%,.0f".format(bestChest.profit)})")
                        clickSlot(bestChest.slotIndex)
                        lastActionTime = System.currentTimeMillis()
                        currentState = CroesusState.WAITING_FOR_CHEST
                    } else {
                        devMessage("§cAll chests in run are already opened. Going back.")
                        failedRuns.add(currentRunId)
                        val stayedInMenu = clickCloseOrBack(menu)
                        lastActionTime = System.currentTimeMillis()
                        currentState = if (stayedInMenu) CroesusState.WAITING_FOR_MAIN else CroesusState.SEARCHING_NPC
                    }
                }

                CroesusState.REROLLING -> {
                    val screen = mc.screen ?: return@on
                    val title = screen.title.string

                    if (title.contains("Bedrock")) {
                        val menu = mc.player?.containerMenu ?: return@on
                        val rerollSlotIndex = findSlotByName(menu, "Reroll Chest")

                        if (rerollSlotIndex != -1) {
                            val rerollLore = menu.getSlot(rerollSlotIndex).item.loreString

                            if (rerollLore.any { it.contains("Bring a Kismet Feather") } || rerollLore.any { it.contains("You already rerolled") }) {
                                modMessage("§cCannot reroll (No feather or already rerolled).")
                                skipKismetForCurrentRun = true
                                clickCloseOrBack(menu)
                                lastActionTime = System.currentTimeMillis()
                                currentState = CroesusState.ANALYZING_RUN
                            } else {
                                devMessage("§eClicking Reroll Feather! (Hypixel will drag us back)")
                                clickSlot(rerollSlotIndex)
                                skipKismetForCurrentRun = true
                                lastActionTime = System.currentTimeMillis()
                                chestData = emptyList()
                                currentState = CroesusState.ANALYZING_RUN // cubey said hypixel does this i didn't check
                            }
                        }
                    }
                }

                CroesusState.WAITING_FOR_CHEST -> {
                }

                CroesusState.CLAIMING -> {
                    val menu = mc.player?.containerMenu ?: return@on
                    if (menu.items.size < 54) return@on

                    val openSlotIndex = findSlotByName(menu, "Open Reward Chest")

                    if (openSlotIndex != -1) {
                        devMessage("§aClicking 'Open Reward Chest' at Slot $openSlotIndex.")
                        clickSlot(openSlotIndex)
                        lastActionTime = System.currentTimeMillis()
                        currentState = CroesusState.SEARCHING_NPC
                    } else {
                        devMessage(" 'Open Reward Chest' button missing. Aborting run.")
                        if (mc.screen != null) mc.player?.closeContainer()

                        lastActionTime = System.currentTimeMillis()
                        failedRuns.add(currentRunId)
                        currentState = CroesusState.SEARCHING_NPC
                    }
                }
            }
        }

        on<GuiEvent.DrawTooltip> {
            val title = screen.title?.string ?: return@on
            if (croesusHud.enabled && (title.matches(chestNameRegex) || title.matches(chestPreviewScreenRegex) || title == "The Catacombs" || title == "Master Mode The Catacombs")) {
                guiGraphics.pose().pushMatrix()
                val sf = mc.window.guiScale
                guiGraphics.pose().scale(1f / sf, 1f / sf)
                guiGraphics.pose().translate(croesusHud.x.toFloat(), croesusHud.y.toFloat())
                guiGraphics.pose().scale(croesusHud.scale)
                guiGraphics.drawOverlay(false)
                guiGraphics.pose().popMatrix()
            }
        }

        on<GuiEvent.DrawSlot> {
            if (screen.title?.string == "Croesus" && slot.item?.hoverName?.string.equalsOneOf("The Catacombs", "Master Mode The Catacombs")) {
                val lore = slot.item?.lore ?: return@on
                val loreString = slot.item?.loreString ?: return@on

                if (hideClaimed && loreString.any { it.matches(chestStatusRegex) } && (!includeKey || hasStrikeThrough("Dungeon Chest Key", lore))) cancel()
                else if (highlightState)
                    guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16,
                        if (loreString.any { it.matches(chestOpenedRegex) }) Colors.MINECRAFT_GOLD.rgba else Colors.MINECRAFT_GREEN.rgba)

            } else if (highlightProfitable && (screen.title?.string?.matches(chestPreviewScreenRegex) == true || screen.title?.string == "The Catacombs" || screen.title?.string == "Master Mode The Catacombs") && slot.index in mostProfitableSlots) {
                val color = when (mostProfitableSlots.indexOf(slot.index)) {
                    0 -> Colors.MINECRAFT_DARK_GREEN.rgba
                    1 -> Colors.MINECRAFT_YELLOW.rgba
                    else -> return@on
                }
                guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color)
            }
        }

        onReceive<ClientboundPlayerInfoUpdatePacket> {
            if (actions().none { it.equalsOneOf(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER) }) return@onReceive
            val tabListEntries = entries()?.mapNotNull { it.displayName?.string }?.ifEmpty { return@onReceive } ?: return@onReceive
            tabListEntries.forEach { tabListEntry ->
                unclaimedChestsRegex.find(tabListEntry)?.groupValues?.get(1)?.toIntOrNull()?.let { unclaimedChests ->
                    currentChestCount = unclaimedChests
                    if (currentChestCount > chestWarning) alert("§cChest limit reached!")
                }
            }
        }

        on<ChatPacketEvent> {
            if (DungeonUtils.inBoss && value.matches(extraStatsRegex)) {
                currentChestCount++
                if (currentChestCount > chestWarning) alert("§cChest limit reached!")
            }
        }
    }

    private fun handleCroesusScreen(items: List<ItemStack>) {
        val chests = mutableListOf<ChestData>()

        items.forEachIndexed { index, stack ->
            if (stack.isEmpty || index > 26 || stack.item != Items.PLAYER_HEAD) return@forEachIndexed

            val lore = stack.loreString
            val costLineIndex = lore.indexOfFirst { it.contains("Cost", ignoreCase = true) }
            if (costLineIndex == -1 && !lore.any { it.contains("Already opened!", ignoreCase = true) }) return@forEachIndexed

            val isOpened = lore.any { it.contains("Already opened!") }
            val loreStartIndex = lore.indexOfFirst { it.contains("Contents", ignoreCase = true) } + 1
            if (loreStartIndex == 0) return@forEachIndexed

            var loreEndIndex = lore.indexOfFirst { it.isEmpty() }
            if (loreEndIndex == -1 || loreEndIndex < loreStartIndex) {
                loreEndIndex = if (costLineIndex != -1) costLineIndex - 1 else lore.size
            }

            var chestCost = 0.0
            if (costLineIndex != -1) {
                val costStr = lore.getOrNull(costLineIndex + 1)?.replace(Regex("[^0-9]"), "")
                chestCost = costStr?.toDoubleOrNull() ?: 0.0
            }

            val itemsList  = mutableListOf<ChestItem>()
            var totalValue = 0.0

            if (loreEndIndex > loreStartIndex) {
                lore.subList(loreStartIndex, loreEndIndex).forEach { item ->
                    if (item.isNotBlank()) {
                        val price = parseItemValue(item) ?: 0.0
                        totalValue += price
                        itemsList.add(ChestItem(item, price))
                    }
                }
            }

            val profit = totalValue - chestCost
            chests.add(ChestData(stack.hoverName, itemsList, profit, index, isOpened))
        }

        chestData = chests.sortedByDescending { it.profit }
        mostProfitableSlots = chestData.filter { it.profit > 0 && !it.isOpened }.take(2).map { it.slotIndex }.toSet()
    }

    private fun parseItemValue(item: String): Double? {
        val cleanItem = item.noControlCodes.trim()
        previewEnchantedBookRegex.find(cleanItem)?.destructured?.let { (name, level) ->
            val ult = if (name in ultimateEnchants) "ULTIMATE_" else ""
            val id = "ENCHANTED_BOOK-$ult${name.uppercase().replace(" ", "_")}-${romanToInt(level)}"
            if (alwaysBuy.contains(id)) return 10_000_000_000.0
            return cachedPrices[id]
        }

        previewEssenceRegex.find(cleanItem)?.destructured?.let { (name, quantity) ->
            if (!includeEssence) return null
            val price = cachedPrices["ESSENCE_${name.uppercase()}"] ?: return null
            return price * quantity.toDouble()
        }

        shardRegex.find(cleanItem)?.groupValues?.get(1)?.let { shardName ->
            return cachedPrices["SHARD_${shardName.uppercase().replace(" ", "_")}"]
        }

        val mappedId = itemReplacements[cleanItem] ?: cleanItem.uppercase().replace(" ", "_")
        if (alwaysBuy.contains(mappedId)) return 10_000_000_000.0

        return cachedPrices[mappedId]
    }

    private fun findSlotByName(menu: AbstractContainerMenu, nameToFind: String): Int {
        for (i in 0 until menu.items.size) {
            val item = menu.getSlot(i).item
            if (!item.isEmpty && item.hoverName.string.contains(nameToFind, ignoreCase = true)) {
                return i
            }
        }
        return -1
    }

    private fun clickCloseOrBack(menu: AbstractContainerMenu): Boolean {
        val goBackSlot = findSlotByName(menu, "Go Back")
        val closeSlot = findSlotByName(menu, "Close")
        if (goBackSlot != -1) {
            clickSlot(goBackSlot)
            return true
        } else if (closeSlot != -1) {
            clickSlot(closeSlot)
            return false
        } else {
            if (mc.screen != null) mc.player?.closeContainer()
            return false
        }
    }

    private fun handleChestContents(items: List<ItemStack>) {
        val chestItems = mutableListOf<ChestItem>()
        var chestCost = 0.0
        var profit = 0.0

        items.forEachIndexed { index, stack ->
            if (stack.isEmpty || index > 40) return@forEachIndexed

            when (stack.item) {
                Items.CHEST -> {
                    stack.loreString.forEach { loreLine ->
                        chestCostRegex.find(loreLine)?.groupValues?.get(1)?.let { cost ->
                            chestCost = cost.replace(",", "").toDouble()
                        }
                    }
                }
                Items.ENCHANTED_BOOK -> {
                    chestEnchantsRegex.find(stack.customData.get("enchantments").toString())?.destructured?.let { (name, level) ->
                        val id = "ENCHANTED_BOOK-${name.uppercase()}-$level"
                        val price = if (alwaysBuy.contains(id)) 10_000_000_000.0 else cachedPrices[id]
                        if (price != null) {
                            chestItems += ChestItem(stack.hoverName.string, price)
                            profit += price
                        }
                    }
                }
                else -> {
                    previewEssenceRegex.find(stack.hoverName.string)?.destructured?.let { (name, quantity) ->
                        if (!includeEssence) return@forEachIndexed
                        val price = cachedPrices["ESSENCE_${name.uppercase()}"] ?: return@forEachIndexed
                        chestItems += ChestItem(stack.hoverName.string, price * quantity.toDouble())
                        profit += price * quantity.toDouble()
                    } ?: previewShardRegex.find(stack.hoverName.string)?.destructured?.let { (shardName) ->
                        cachedPrices["SHARD_${shardName.uppercase().replace(" ", "_").replace("'s", "")}"]?.let {
                            chestItems += ChestItem(stack.hoverName.string, it)
                            profit += it
                        }
                    } ?: run {
                        val id = stack.itemId
                        val price = if (alwaysBuy.contains(id)) 10_000_000_000.0 else cachedPrices[id]
                        if (price != null) {
                            chestItems += ChestItem(stack.hoverName.string, price)
                            profit += price
                        }
                    }
                }
            }
        }
        currentChestProfit = profit - chestCost
        chestData = listOf(
            ChestData(Component.literal("§eChest"), chestItems, currentChestProfit ?: 0.0, -1, false)
        )
    }

    private fun hasStrikeThrough(itemName: String, loreComponents: List<Component>): Boolean =
        loreComponents.any { line -> line.siblings.any { sibling -> sibling.string == itemName && sibling.style.isStrikethrough } }

    private fun GuiGraphics.drawOverlay(isEditing: Boolean): Pair<Int, Int> {
        val dataToDisplay = if (isEditing) sampleChestData else chestData
        var yOffset = 0
        var maxWidth = 0
        val maxNameWidth = getStringWidth("Diamond ")

        dataToDisplay.forEach { chest ->
            val profitColor = if (chest.profit >= 0) "§2" else "§c"
            val profitText = "§8- §6Profit: $profitColor${"%,.0f".format(chest.profit)}"

            text(chest.name.visualOrderText, 0, yOffset)
            val profitDim = textDim(profitText, maxNameWidth, yOffset)

            maxWidth = maxOf(maxWidth, maxNameWidth + profitDim.first)
            yOffset += 9

            if (!minimized) {
                chest.items.forEach { item ->
                    val priceStr = if (item.price > 1_000_000_000.0) "§d§lRNG DROP" else "§a${"%,.0f".format(item.price)}"
                    val itemText = "  §7${item.name}: $priceStr"
                    val dim = textDim(itemText, 0, yOffset)
                    maxWidth = maxOf(maxWidth, dim.first)
                    yOffset += dim.second
                }
            }
            yOffset += 2
        }
        return maxWidth to yOffset
    }

    private val itemReplacements = mapOf(
        "Shiny Wither Chestplate" to "WITHER_CHESTPLATE",
        "Shiny Wither Leggings" to "WITHER_LEGGINGS",
        "Shiny Necron's Handle" to "NECRON_HANDLE",
        "Necron's Handle" to "NECRON_HANDLE",
        "Shiny Wither Helmet" to "WITHER_HELMET",
        "Shiny Wither Boots" to "WITHER_BOOTS",
        "Wither Shield" to "WITHER_SHIELD_SCROLL",
        "Implosion" to "IMPLOSION_SCROLL",
        "Shadow Warp" to "SHADOW_WARP_SCROLL",
        "Necron Dye" to "DYE_NECRON",
        "Livid Dye" to "DYE_LIVID",
        "Giant's Sword" to "GIANTS_SWORD",
    )

    private val sampleChestData = listOf(
        ChestData(Component.literal("Bedrock"), listOf(ChestItem("Enchanted Book (One For All V)", 85000000.0), ChestItem("Wither Catalyst", 12000000.0), ChestItem("Necron's Handle", 550000000.0)), 632000000.0, 0, false),
        ChestData(Component.literal("Obsidian"), listOf(ChestItem("Enchanted Book (Legion V)", 45000000.0), ChestItem("Wither Blood", 8500000.0)), 46500000.0, 1, false),
        ChestData(Component.literal("Diamond"), listOf(ChestItem("Wither Essence x10", 2000000.0), ChestItem("Recombobulator 3000", 6500000.0)), 5000000.0, 2, false)
    )

    fun startScript() {
        if (!this.enabled) {
            modMessage("§cPlease enable the Croesus module in your GUI first!")
            return
        }
        autoClaimToggle = true
        failedRuns.clear()
        currentState = CroesusState.SEARCHING_NPC
        lastActionTime = System.currentTimeMillis()
        modMessage("§aStarted Auto Croesus. Searching for assets...")
    }

    fun stopScript() {
        autoClaimToggle = false
        currentState = CroesusState.IDLE
        if (mc.screen != null) mc.player?.closeContainer()
        devMessage("§cAuto Croesus stopped.")
    }

    fun refreshApi() {
        scope.launch {
            cachedPrices = fetchJson<Map<String, Double>>("https://api.odtheking.com/lb/lowestbins").getOrElse {
                Shizo.logger.error("Failed to fetch lowest bin prices.", it)
                emptyMap()
            }
            modMessage("§aCroesus Lowest BIN prices refreshed from API.")
        }
    }
}