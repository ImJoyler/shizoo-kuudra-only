package shizo.module.impl.floor7.terminals

import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.utils.Colors
import shizo.utils.renderUtils.renderUtils.textDim
import shizo.utils.skyblock.dungeon.DungeonUtils
import shizo.utils.skyblock.dungeon.M7Phases

object LeapNotification : Module(
    name = "Leap Notification",
    description = "Alerts instantly when players leap to your section.",
    subcategory = "Terminals"
) {

    private val hud by HUD("Leap HUD", "Displays the leap count.") {
        if (it) {
            textDim("§a3/3 Players", 0, 0, Colors.WHITE)
        }
        else if (shouldDisplay) {
            // only when ee3 we do this cause if imnot a retard its only there were only 3 people leap
            val leaps = leapedPlayers.size
            val neededOthers = if (currentSpot == 3) 3 else 4 // i can 't count gg
            val color = if (leaps >= neededOthers) "§a" else "§c"
            textDim("$color$leaps§f/$neededOthers Players Leaped", 0, 0, Colors.WHITE)
        }
        else {
            0 to 0
        }
    }

    private val REGIONS = listOf(
        AABB(90.0, 106.0, 50.0, 111.0, 145.0, 123.0),
        AABB(17.0, 106.0, 121.0, 108.0, 145.0, 143.0),  //ee2
        AABB(-2.0, 106.0, 51.0, 20.0, 145.0, 142.0),    // ee 3
        AABB(-1.0, 26.0, 29.0, 191.0, 145.0, 58.0),     // core / goldor
        AABB(3.0, 5.0, 0.0, 128.0, 48.0, 140.0)         // relcs // idk if we need
    )

    private val EE2_BOX = AABB(57.0, 108.0, 130.0, 59.0, 110.0, 132.0)
    private val HEE2_BOX = AABB(59.0, 132.0, 138.0, 62.0, 133.0, 140.0)
    private val EE3_BOX = AABB(1.0, 108.0, 103.0, 3.0, 110.0, 105.0)
    private val CORE_BOX = AABB(53.5, 114.0, 49.5, 55.5, 116.0, 51.5)
    private val RELIC_BOX = AABB(51.5, 3.0, 73.5, 57.5, 8.0, 79.5)

    private var currentSpot = 0
    private var shouldDisplay = false
    private val leapedPlayers = mutableSetOf<Int>()

    init {
        onReceive<ClientboundTeleportEntityPacket> {
            if (!shouldDisplay || currentSpot < 2) return@onReceive

            val entityId = this.id
            val pos = this.change.position

            val region = REGIONS.getOrNull(currentSpot - 1) ?: return@onReceive

            if (region.contains(pos.x, pos.y, pos.z)) {
                val entity = mc.level?.getEntity(entityId) ?: return@onReceive
                if (isValidPlayer(entity)) {
                    leapedPlayers.add(entityId)
                }
            }
        }

        on<TickEvent.End> {
            // i should really fix f7phase and m7phase having two different names for the same shit lol
            if (mc.player == null || mc.level == null || DungeonUtils.getF7Phase() != M7Phases.P3) {
                reset()
                return@on
            }

            val newSpot = getP3Spot()

            if (newSpot != currentSpot) {
                reset()
                currentSpot = newSpot
                if (currentSpot >= 2) {
                    val region = REGIONS.getOrNull(currentSpot - 1)
                    if (region != null) {
                        val existingCount = mc.level!!.entitiesForRendering()
                            .count { it is Player && region.contains(it.position()) && isValidPlayer(it) }

                        if (existingCount == 0) {
                            shouldDisplay = true
                        } else {
                            shouldDisplay = false
                        }
                    }
                }
            }
            if (shouldDisplay && currentSpot >= 2) {
                val region = REGIONS.getOrNull(currentSpot - 1)
                if (region != null) {
                    mc.level!!.entitiesForRendering().forEach {
                        if (it is Player && region.contains(it.position()) && isValidPlayer(it)) {
                            leapedPlayers.add(it.id)
                        }
                    }
                }
            }
        }
    }

    private fun reset() {
        shouldDisplay = false
        currentSpot = 0
        leapedPlayers.clear()
    }

    private fun isValidPlayer(entity: Entity): Boolean {
        return entity is Player &&
                entity != mc.player &&
                entity.name.string != mc.player?.name?.string
    }

    private fun getP3Spot(): Int {
        val pos = mc.player?.position() ?: return 0
        return when {
            HEE2_BOX.contains(pos) -> 2
            EE2_BOX.contains(pos) -> 2
            EE3_BOX.contains(pos) -> 3
            CORE_BOX.contains(pos) -> 4
            else -> 0
        }
    }
}