package shizo.module.impl.kuudra.qolplus.vesuvius

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.ChatPacketEvent
import shizo.events.GuiEvent
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.module.impl.dungeon.croesus.Croesus
import shizo.utils.*
import shizo.utils.handlers.schedule
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.renderUtils.renderUtils.text
import shizo.utils.renderUtils.renderUtils.textDim

object Jewing : Module(
    name = "Jewing",
    description = "Profit Calculations and auto vesuvius.",
    subcategory = "QOL"

) {
    private var autoClaim by BooleanSetting("Auto Claim", false, desc = "Master switch for the auto-claimer.")
    private val minProfit by NumberSetting("Min Profit", 200_000.0, 0.0, 10_000_000.0, 50_000.0, desc = "Minimum profit to auto-claim a chest.")
    private val clickDelay by NumberSetting("Click Delay", 500.0, 100.0, 2000.0, 50.0, desc = "Delay between actions in ms.")

    private val hideClaimed by BooleanSetting("Hide Claimed", true, desc = "Hides chests that have already been claimed.")
    private val vesuviusHud by HUD("Vesuvius Chest HUD", "Displays all chest contents with prices, sorted by profit.") {
        if (!it) return@HUD 0 to 0
        drawOverlay(true)
    }

    private val trackerMode = SelectorSetting(
        "Tracker Mode",
        "Session",
        arrayListOf("Session", "Lifetime"),
        "Choose which stats to display on the HUD."
    )
    init {this.registerSetting(trackerMode)}
    private val resetSessionKey by KeybindSetting(
        "Reset Session Stats",
        GLFW.GLFW_KEY_UNKNOWN,
        "Clears current session profit and resets the hourly rate timer."
    ).onPress {
        ProfitData.resetSession()
        modMessage("§aSession profit and hourly rate have been reset!")
    }
    val profitTrackerHud by HUD("Kuudra Profit Tracker", "Displays your overall Kuudra profit.", true) { example ->
        val isSession = trackerMode.value == 0
        val modeStr = if (isSession) "SESSION" else "LIFETIME"
        val titleStr = "§3§lProfit Tracker §8(§7$modeStr§8)"

        val statProfit: Double
        val statChests: Int
        val statPaid: Int
        val statFree: Int
        val statRate: Double

        if (example) {
            statProfit = 145_200_000.0
            statChests = 12
            statPaid = 8
            statFree = 4
            statRate = 25_500_000.0
        } else {
            if (isSession) {
                statProfit = ProfitData.sessionProfit
                statChests = ProfitData.sessionChests
                statPaid = ProfitData.sessionPaid
                statFree = ProfitData.sessionFree
                statRate = ProfitData.getSessionProfitPerHour()
            } else {
                statProfit = ProfitData.lifetimeProfit
                statChests = ProfitData.lifetimeChests
                statPaid = ProfitData.lifetimePaid
                statFree = ProfitData.lifetimeFree
                statRate = 0.0
            }
        }

        val statAvg = if (statChests > 0) statProfit / statChests else 0.0

        val profitColor = if (statProfit > 0) "§a+" else if (statProfit < 0) "§c" else "§7+"
        val avgColor = if (statAvg > 0) "§a" else if (statAvg < 0) "§c" else "§7"

        val profitStr = "§fProfit: $profitColor${"%,.0f".format(statProfit)}"

        val chestsStr = "§fChests: §f$statChests §8(§e${statPaid}P§8/§a${statFree}F§8) §7-> $avgColor${NPCUtils.formatCompact(statAvg)}/avg"

        val rateColor = if (statRate > 0) "§a" else "§7"
        val rateStr = if (isSession) "§fRate: $rateColor${NPCUtils.formatCompact(statRate)}/h" else "§fRate: §7N/A"

        textDim(titleStr, 0, 0, Colors.WHITE, shadow = true)
        textDim(profitStr, 0, 10, Colors.WHITE, shadow = true)
        textDim(chestsStr, 0, 20, Colors.WHITE, shadow = true)
        textDim(rateStr, 0, 30, Colors.WHITE, shadow = true)

        val maxWidth = listOf(titleStr, profitStr, chestsStr, rateStr).maxOf { mc.font.width(it) }
        return@HUD maxWidth to 40
    }

    private val toggleModeKey by KeybindSetting(
        "Toggle Tracker Mode",
        GLFW.GLFW_KEY_UNKNOWN,
        "Quickly swap between Session and Lifetime"
    ).onPress {

        trackerMode.value = if (trackerMode.value == 0) 1 else 0

        modMessage("§bTracker Mode set to: §f${if (trackerMode.value == 0) "Session" else "Lifetime"}")

    }

    private var currentState = State.IDLE

    private var lastActionTime = 0L
    private val failedRunSlots = mutableSetOf<Int>()
    private var lastPage = -1
    private var currentChest: ChestData? = null
    private var lastOpenedChestType = ""
    private var skippedKeyCount = 0
    private var lastSeenTitle = ""
    private var lastFirstSlotLore = emptyList<String>()

    private val previewEnchantedBookRegex = Regex("^Enchanted Book \\(?([\\w ]+) (\\w+)\\)$")
    private val previewEssenceRegex = Regex("^(\\w+) Essence x(\\d+)$")
    private val shardRegex = Regex("^([A-Za-z' ]+) Shard(?: x(\\d+))?$")
    private val teethRegex = Regex("^Kuudra Teeth x(\\d+)$")
    private val pearlRegex = Regex("^Heavy Pearl x(\\d+)$")
    private val chestRegex = Regex("^(Free|Paid) Chest$")
    val chestInMenu = Regex(".*(Free|Paid) Chest.*")
    private val uselessLinesRegex = Regex("^Contents|Cost|Click to open!|FREE|Already opened!|Can't open another chest!|Paid Chest|")
    private val ultimateEnchants = setOf("Fatal Tempo", "Inferno")

    private data class Key(val type: String, val coins: Int, val quantity: Int)
    private data class ChestItem(val name: Component, val price: Double)
    private data class ChestData(val items: List<ChestItem>, val cost: Double, val profit: Double)

    fun startScript() {
        if (!this.enabled) return modMessage("§cPlease enable the Jewing module first!")
        autoClaim = true
        currentState = State.SEARCHING_NPC
        failedRunSlots.clear()
        lastActionTime = System.currentTimeMillis()
        skippedKeyCount = 0
        lastSeenTitle = ""
        //jewMessage("§f Initiating Operation Iron Sword. Securing funds for Israel!")
    }

    fun stopScript() {
        autoClaim = false
        currentState = State.IDLE
        currentChest = null

        mc.player?.closeContainer()
        //jewMessage("§f Mission halted. Returning to Jerusalem.")
    }


    override fun onDisable() {
        stopScript()
        modMessage("I cba not to make this close ")
    }
    // i hate doing this but idk if i can fix it ot


    init {
        ProfitData.load()

        on<ChatPacketEvent> {

            val msg = value.noControlCodes

            if (msg.contains("CHEST REWARDS")) {
                if (currentChest != null) {

                    val isPaid = msg.contains("PAID") || lastOpenedChestType == "Paid"
                    ProfitData.addProfit(currentChest!!.profit, isPaid)
                    currentChest = null
                } else {
                    devMessage("§cWarning: Chest opened but currentChest was null! Profit missed.")
                }
            }
        }
        // packet machine :D
        onReceive<ClientboundOpenScreenPacket> {
            if (!autoClaim) return@onReceive
            val titleStr = title.string.noControlCodes

            if (titleStr.contains("Kuudra - ") || titleStr.matches(chestInMenu)) {
                currentChest = null
                currentState = State.ANALYZING_CHEST
                lastActionTime = System.currentTimeMillis()
            } else if (titleStr.matches(chestInMenu)) {

                currentState = State.ANALYZING_CHEST
                lastActionTime = System.currentTimeMillis()

            } else if (titleStr == "Vesuvius" || titleStr == "Croesus") {
                currentState = State.SCANNING_PAGES
                lastActionTime = System.currentTimeMillis()
            }
        }

        onReceive<ClientboundContainerSetSlotPacket> {
            if (!autoClaim || mc.screen == null) return@onReceive
            val titleStr = mc.screen!!.title.string.noControlCodes

            if (currentState == State.ANALYZING_CHEST && titleStr.contains("Kuudra - ")) {
                if (slot == 13 && item.item == Items.PLAYER_HEAD) {
                    handleKuudraChest(item)
                    if (currentChest != null) {
                        devMessage("§aLoot analyzed! Queuing selection...")
                        currentState = State.CLAIMING
                    }
                }
            }
            if (slot == 31 && item.item == Items.CHEST && titleStr.matches(chestInMenu)) {
                handleKuudraChest(item)
                if (currentState == State.ANALYZING_CHEST) {
                    devMessage("§aReward Chest loaded! Queuing claim...")
                    currentState = State.CLAIMING
                }
            }
        }

        on<TickEvent.Start> {
            if (KuudraUtils.inKuudra) {
                ProfitData.markSessionStart()
            }
            if (mc.player == null || mc.level == null || !autoClaim) return@on
            val isShiftDown = mc.options.keyShift.isDown
            val isEscDown = InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_ESCAPE)
            if (currentState != State.IDLE && (isShiftDown || isEscDown)) {
                stopScript()
                //mc.player?.closeContainer()
                NPCUtils.jewMessage("§c§lIron Dome Protocol! Abort mission, protect the state!")
                return@on
            }

            if (mc.player!!.tickCount %10 == 0) devMessage("State: §b$currentState")

            if (currentState != State.IDLE && System.currentTimeMillis() - lastActionTime > 5000L) {
                stopScript()
                return@on
            }
            val passiveStates = setOf(State.WAITING_FOR_GUI, State.WAITING_FOR_PAGE, State.OPENING_RUN, State.ANALYZING_CHEST)
            if (currentState !in passiveStates && System.currentTimeMillis() - lastActionTime < clickDelay.toLong()) return@on

            when (currentState) {
                State.IDLE -> {
                    return@on
                }

                State.SEARCHING_NPC -> {
                    //jewMessage("Scouting for the Nigga to fund the Holy Land")
                    if (mc.screen != null) {
                        mc.player?.closeContainer()
                        lastActionTime = System.currentTimeMillis()
                        return@on
                    }
                    if (NPCUtils.tryClickNPC("Vesuvius")) {
                        NPCUtils.jewMessage("§fClicking the jew, GLORY TO NETANYAHU")
                        lastActionTime = System.currentTimeMillis()
                        currentState = State.WAITING_FOR_GUI
                    }
                }

                State.WAITING_FOR_GUI -> {
                    if (NPCUtils.inVesuvius()) {
                        //jewMessage("§fIsrael is sending us informations about our runs, our prayers are answered")
                        lastActionTime = System.currentTimeMillis()
                        currentState = State.SCANNING_PAGES
                    }
                }

                State.SCANNING_PAGES -> {
                    if (!NPCUtils.inVesuvius() || !NPCUtils.isInvLoaded()) return@on
                    val currentPage = NPCUtils.getCurrPage()
                    if (currentPage != lastPage) {
                        lastPage = currentPage
                    }

                    val (slotToClick, _, skippedCount) = NPCUtils.findUnopenedKuudraRun(failedRunSlots, currentPage)

                    if (skippedCount > 0) {
                        skippedKeyCount += skippedCount
                    }

                    if (slotToClick != null) {
                        clickSlot(slotToClick)
                        NPCUtils.jewMessage("§fLooting Crate with id $slotToClick THANK YOU NETHANYAHU FOR BELSSING US WIHT HITS")

                        failedRunSlots.add(slotToClick + (currentPage - 1) * 54)

                        lastActionTime = System.currentTimeMillis()
                        currentState = State.OPENING_RUN
                    } else {
                        val container = mc.player!!.containerMenu
                        val arrow = container.getSlot(53).item

                        if (arrow.item == Items.ARROW && arrow.hoverName.string.contains("Next Page")) {
                            clickSlot(53)
                            lastActionTime = System.currentTimeMillis()
                            lastPage = currentPage

                            // bad code AHAED IGNORE
                            lastFirstSlotLore = container.getSlot(10).item.loreString

                            currentState = State.WAITING_FOR_PAGE
                            return@on
                        } else {
                            NPCUtils.jewMessage("§fNo more assets found. The promised land has been milked dry.")
                            currentState = State.FINISHED
                        }
                    }
                }
                State.WAITING_FOR_PAGE -> {
                    if (!NPCUtils.inVesuvius() || !NPCUtils.isInvLoaded()) return@on
                    // BAD BAD CODE BAD CODE BAD CODE AHEAD BEWARE BAD CODE AHAED

                    val container = mc.player?.containerMenu ?: return@on
                    val currentFirstSlotLore = container.getSlot(10).item.loreString

                    if (currentFirstSlotLore != lastFirstSlotLore || System.currentTimeMillis() - lastActionTime > 1000) {
                     currentState = State.SCANNING_PAGES
                    }
                }

                State.OPENING_RUN -> {
                    if (NPCUtils.inKuudraRunGui()) {
                        currentChest = null
                        lastActionTime = System.currentTimeMillis()
                        currentState = State.ANALYZING_CHEST
                    } else if (NPCUtils.inVesuvius() && System.currentTimeMillis() - lastActionTime > 2000) {
                        currentState = State.SCANNING_PAGES
                    }
                }

                State.ANALYZING_CHEST -> {
                    val screen = mc.screen ?: return@on
                    val titleStr = screen.title.string.noControlCodes

                    if (!NPCUtils.inKuudraRunGui() && !titleStr.matches(chestInMenu)) {
                        currentState = State.SCANNING_PAGES
                        return@on
                    }

                    val container = mc.player?.containerMenu ?: return@on
                    if (currentChest == null) {
                        for (i in 0 until container.items.size) {
                            val item = container.getSlot(i).item
                            val name = item.hoverName.string.noControlCodes

                            if (item.item == Items.PLAYER_HEAD && (name.contains("Paid Chest") || name.contains("Free Chest"))) {
                                handleKuudraChest(item)
                                if (currentChest != null) {
                                    currentState = State.CLAIMING
                                }
                                break
                            }
                        }
                    } else {
                        currentState = State.CLAIMING
                    }
                }
                State.CLAIMING -> {
                    if (handleClaimingLogic()) {
                        lastActionTime = System.currentTimeMillis() // maybe modify!
                        currentState = State.SEARCHING_NPC
                    }
                }

                State.FINISHED -> {
                    mc.player?.closeContainer()
                    if (skippedKeyCount > 0) {
                        NPCUtils.jewMessage("§fYo stopping: §fSkipped §6$skippedKeyCount §fchests cause u got no keys fam. Get your bread up you broke boy.")
                    } else {
                        NPCUtils.jewMessage("§aOperation Success. All funds transferred to the Bank of Israel.")
                    }
                    stopScript()
                }
            }
        }

        on<GuiEvent.DrawTooltip> {
            val title = screen.title?.string ?: return@on
            if (vesuviusHud.enabled && title.matches(chestRegex)) {
                guiGraphics.pose().pushMatrix()
                val sf = mc.window.guiScale
                guiGraphics.pose().scale(1f / sf, 1f / sf)
                guiGraphics.pose().translate(vesuviusHud.x.toFloat(), vesuviusHud.y.toFloat())
                guiGraphics.pose().scale(vesuviusHud.scale)
                guiGraphics.drawOverlay(false)
                guiGraphics.pose().popMatrix()
            }
        }

        on<GuiEvent.DrawSlot> {
            if (screen.title?.string.equalsOneOf("Vesuvius", "Croesus") && slot.item?.hoverName?.string == "Kuudra's Hollow") {
                if (hideClaimed && slot.item?.loreString?.any { it == "No more chests to open!"} == true) cancel()
            }
        }

    }

    // shiould theoritclaly fix the insta clicking
    private fun handleClaimingLogic(): Boolean {
        val screen = mc.screen ?: return false
        val container = mc.player?.containerMenu ?: return false
        val title = screen.title.string.noControlCodes

        if (title.matches(chestInMenu)) {
            for (i in 0 until 54) {
                val item = container.getSlot(i).item
                if (item.item == Items.CHEST && item.hoverName.string.noControlCodes.contains("Open Reward Chest")) {
                    clickSlot(i)
                    lastActionTime = System.currentTimeMillis()
                    return true
                }
            }
            return false
        }

        val data = currentChest ?: return false

        var paidSlot = -1
        var freeSlot = -1

        for (i in 0..26) {
            val item = container.getSlot(i).item
            if (item.item != Items.PLAYER_HEAD) continue

            val name = item.hoverName.string.noControlCodes
            if (name.contains("Paid Chest")) paidSlot = i
            if (name.contains("Free Chest")) freeSlot = i
        }

        val hasKey = hasKuudraKey()
        val isInfernal = title.contains("Infernal")

        if (paidSlot != -1 && data.profit >= minProfit) {
            if (hasKey) {
                clickSlot(paidSlot)
                lastOpenedChestType = "Paid"
                lastActionTime = System.currentTimeMillis()
                NPCUtils.jewMessage("§a§lSecuring Assets for Netanyahu! Profit: §6${"%,.0f".format(data.profit)}")
                currentState = State.ANALYZING_CHEST
                return false
            } else {
                NPCUtils.jewMessage("§c§lTactical Retreat! High value target but no key. Netanyahu is watching.")
                skippedKeyCount++
                safeCloseGUI()
                return true
            }
        }

        if (freeSlot != -1) {
            clickSlot(freeSlot)
            lastOpenedChestType = "Free"
            lastActionTime = System.currentTimeMillis()
            NPCUtils.jewMessage("§aClaiming reparations. Glory to Israel!")
            currentState = State.ANALYZING_CHEST
            return false
        }

        if (isInfernal && paidSlot != -1) {
            NPCUtils.jewMessage("§fSlums detected, skipping.")
            safeCloseGUI()
            return true
        }

        NPCUtils.jewMessage("§cBarren land. Moving to settle elsewhere.")
        safeCloseGUI()
        return true
    }


    private fun safeCloseGUI() {
        if (mc.screen == null) return
        schedule(2) {
            if (mc.screen != null) {
                mc.player?.closeContainer()
                lastActionTime = System.currentTimeMillis()
            }
        }
    }
    private fun clickSlot(slotId: Int) {
        val screen = mc.screen ?: return
        if (screen !is AbstractContainerScreen<*>) return
        val player = mc.player ?: return
        mc.gameMode?.handleInventoryMouseClick(player.containerMenu.containerId, slotId, 0, ClickType.PICKUP, player)
        lastActionTime = System.currentTimeMillis()
    }

    private fun hasKuudraKey(): Boolean {
        val inventory = mc.player?.inventory ?: return false
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty && stack.hoverName.string.contains("Kuudra Key")) {
                return true
            }
        }
        return false
    }
    private fun handleKuudraChest(item: ItemStack) {
        val chestItems = mutableListOf<ChestItem>()
        var profit = 0.0
        var chestCost = 0.0

        val lore = item.loreString

        lore.forEach { string ->
            if (string.contains("Kuudra Key")) {
                chestCost = getPriceOfKey(string)
                return@forEach
            }
            if (string.matches(uselessLinesRegex)) return@forEach
            val cleanString = string.replace("✪", "").trim()
            val price = parseItemValue(string.replace("✪", "").trim()) ?: 0.0

            devMessage("Lore Line: '$cleanString' | Price Found: $price")

            profit += price
            chestItems.add(ChestItem(Component.literal(string), price))
        }
        // ty ai for db
        devMessage("TOTAL PROFIT CALC: Raw=$profit | KeyCost=$chestCost | Final=${profit - chestCost}")
        currentChest = ChestData(chestItems, chestCost, (profit - chestCost))
    }

    private fun parseItemValue(item: String): Double? {
        previewEnchantedBookRegex.find(item)?.destructured?.let { (name, level) ->
            val ult = if (name in ultimateEnchants) "ULTIMATE_" else ""
            return Croesus.cachedPrices["ENCHANTED_BOOK-$ult${name.uppercase().replace(" ", "_")}-${romanToInt(level)}"]
        }

        previewEssenceRegex.find(item)?.destructured?.let { (name, quantity) ->
            val price = Croesus.cachedPrices["ESSENCE_${name.uppercase()}"] ?: return null
            return price * quantity.toDouble()
        }

        shardRegex.find(item)?.let { shard ->
            val price = Croesus.cachedPrices["SHARD_${shard.groupValues[1].uppercase().replace(" ", "_")}"] ?: 0.0
            return price * (shard.groupValues.getOrNull(2)?.toDoubleOrNull() ?: 1.0)
        }

        teethRegex.find(item)?.destructured?.let { (quantity) ->
            val price = Croesus.cachedPrices["KUUDRA_TEETH"] ?: 0.0
            return price * quantity.toDouble()
        }

        pearlRegex.find(item)?.destructured?.let { (quantity) ->
            val price = Croesus.cachedPrices["HEAVY_PEARL"] ?: 0.0
            return price * quantity.toDouble()
        }

        itemReplacements[item]?.let { itemId -> return Croesus.cachedPrices[itemId] }

        return Croesus.cachedPrices[item.uppercase().replace(" ", "_")]
    }

    private fun getPriceOfKey(key: String): Double {
        keys.find { it.type == key }?.let {
            val material = minOf(Croesus.cachedPrices["ENCHANTED_RED_SAND"] ?: 0.0, Croesus.cachedPrices["ENCHANTED_MYCELIUM"] ?: 0.0)
            val star = (Croesus.cachedPrices["CORRUPTED_NETHER_STAR"] ?: 0.0)
            return it.coins + material * it.quantity + star * 2
        }
        return 0.0
    }

    private fun GuiGraphics.drawOverlay(isEditing: Boolean): Pair<Int, Int> {
        val dataToDisplay = if (isEditing) sampleChestData else currentChest
        var yOffset = 0
        val maxWidth = 251

        val cost = "%,.0f".format(dataToDisplay?.cost ?: 0.0)
        val profit = "%,.0f".format(dataToDisplay?.profit ?: 0.0)

        dataToDisplay?.items?.forEach { item ->
            val price: String = "%,.0f".format(item.price)
            drawString(mc.font, item.name, 0, yOffset, -1)
            text(price, maxWidth - mc.font.width(price), yOffset, Colors.MINECRAFT_GRAY)
            yOffset += 9
        }

        yOffset += 6
        text("§cKey Cost:", 0, yOffset)
        text(cost, maxWidth - mc.font.width(cost), yOffset, Colors.MINECRAFT_RED)
        yOffset += 12
        text("§aProfit:", 0, yOffset)
        text(profit, maxWidth - mc.font.width(profit), yOffset, Colors.MINECRAFT_GREEN)
        yOffset += 9

        return maxWidth to yOffset
    }

    private val keys = listOf(
        Key("Kuudra Key", 155200, 2),
        Key("Hot Kuudra Key", 310400, 4),
        Key("Burning Kuudra Key", 582000, 16),
        Key("Fiery Kuudra Key", 1164000, 40),
        Key("Infernal Kuudra Key", 2328000, 80)
    )

    private val itemReplacements = mapOf(
        "Hellstorm Wand" to "HELLSTORM_STAFF",
        "Aurora Staff" to "RUNIC_STAFF",
    )

    private val sampleChestData = ChestData(
        items = listOf(
            ChestItem(Component.literal("Fervor Helmet").withStyle(ChatFormatting.GOLD), 748000.0),
            ChestItem(Component.literal("Crimson Essence x2000").withStyle(ChatFormatting.LIGHT_PURPLE), 2420000.0)
        ),
        cost = 3247000.0,
        profit = 118542706.0
    )
}