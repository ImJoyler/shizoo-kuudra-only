package shizo.utils.skyblock.dungeon

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import net.minecraft.network.HashedStack
import net.minecraft.network.protocol.game.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ClickType
import org.apache.commons.lang3.math.NumberUtils.toByte
import shizo.Shizo.mc
import shizo.events.ChatPacketEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.utils.SwapState
import shizo.utils.itemId
import shizo.utils.modMessage
import shizo.utils.sendUseItemClicksSeq
import shizo.utils.skyblock.dungeon.DungeonUtils.dungeonTeammatesNoSelf
import shizo.utils.skyblock.dungeon.DungeonUtils.getMageCooldownMultiplier
import shizo.utils.swapFromNameAR

object LeapUtils {
    private var leapQueue = mutableListOf<String>()
    private var menuOpened = false
    var inProgress = false
    private var clickedLeap = false
    private var cwid = -1

    private var lastLeap = 0L
    private val leapCD get() = 2400 * getMageCooldownMultiplier()

    private val currentLeap get() = leapQueue[0]
    private val inQueue get() = leapQueue.size > 0

    private fun reloadGui() {
        menuOpened = false
        leapQueue.removeFirst()
        inProgress = false
    }

    fun queueLeap(name: String) {
        leapQueue.add(name)
    }

    fun airClick() {
        val player = mc.player ?: return

        sendUseItemClicksSeq(mutableListOf(player.yRot, player.xRot))
    }

    fun click(slot: Int, stateId : Int) {
        if (cwid == -1) return
        val carried = HashedStack.EMPTY

        mc.connection?.send(
            ServerboundContainerClickPacket(
                cwid,
                stateId,
                slot.toShort(),
                0.toByte(),
                ClickType.PICKUP,
                Int2ObjectMaps.emptyMap(),
                carried
            )
        )
    }

    fun leap(target: Any) {
        if (mc.screen != null) {
            modMessage("§cFailed to leap! found another gui..")
            return
        }

        if (!DungeonUtils.inDungeons || inProgress || target == DungeonClass.Unknown ) return
        val elapsed = System.currentTimeMillis() - lastLeap
        if (elapsed < leapCD) {
            modMessage("§cFailed to leap! On cooldown: ${"%.1f".format((leapCD - elapsed) / 1000.0)}s")
            return
        }
        val teammate = when (target) {
            is String -> dungeonTeammatesNoSelf.firstOrNull { it.name == target }
            is DungeonClass -> dungeonTeammatesNoSelf.firstOrNull { it.clazz == target }
            else -> return
        }
        if (teammate != null) {
            inProgress = true
            val player = mc.player ?: return
//            if (player.mainHandItem.itemId != "INFINITE_SPIRIT_LEAP" && player.mainHandItem.itemId != "SPIRIT_LEAP") return
//            airClick()
//
//            clickedLeap = true
//            lastLeap = System.currentTimeMillis()
//            modMessage("§аLeaping to $target")
//            leapQueue.add(teammate.name)
            val state = swapFromNameAR("leap") {
                airClick()
                clickedLeap = true
                lastLeap = System.currentTimeMillis()
                modMessage("§aLeaping to $target")
                leapQueue.add(teammate.name)
            }
            if (state == SwapState.UNKNOWN || state == SwapState.TOO_FAST) {
                inProgress = false
                return
            }
        //modMessage("in queue " + inQueue + " menu opened " + menuOpened)
        } else {
            inProgress = false
            modMessage("§cFailed to leap! §r$target §cnot found")
        }
    }

    init {
        // clickslot
        onReceive<ClientboundContainerSetSlotPacket> {
            if (!inQueue || !menuOpened) return@onReceive

            val slotId = this.slot
            val stateid = this.stateId
            val itemStack = this.item

            if (itemStack.isEmpty) return@onReceive

            if (slotId > 35) {
                modMessage("§cFailed to leap! §r$currentLeap §cnot found!")
                reloadGui()
                return@onReceive
            }
            it.cancel()

            val itemName = itemStack.hoverName.string
            if (itemName.contains(currentLeap)) {
                click(slot, stateid)
                reloadGui()
            }
        }

        onReceive<ClientboundOpenScreenPacket> {
            cwid = this.containerId
            if (!inQueue) return@onReceive

            val title = this.title.string
            if (!title.contains("Leap")) return@onReceive

            menuOpened = true
            clickedLeap = false
            //modMessage("Leap Menu opened")
            it.cancel()
        }
        onReceive<ClientboundContainerClosePacket> {
            cwid = -1
        }
        onReceive<ServerboundContainerClosePacket> {
            cwid = -1
        }
//        on<ChatPacketEvent> {
//            val msg = value.replace(Regex("§[0-9a-fk-or]"), "")
//            if (msg.matches(Regex("^This ability is on cooldown for (\\d+)s\\.$"))) {
//                clickedLeap = false
//                inProgress = false
//                if (leapQueue.isNotEmpty()) leapQueue.removeLast()
//            }
//        }
    }
}