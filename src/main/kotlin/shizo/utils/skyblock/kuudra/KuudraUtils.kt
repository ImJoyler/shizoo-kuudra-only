package shizo.utils.skyblock.kuudra

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.MagmaCube
import net.minecraft.world.entity.monster.Zombie
import shizo.Shizo
import shizo.events.ChatPacketEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.utils.handlers.TickTask
import shizo.utils.handlers.schedule
import shizo.utils.noControlCodes
import shizo.utils.skyblock.Island
import shizo.utils.skyblock.LocationUtils
import shizo.utils.skyblock.LocationUtils.currentArea
import shizo.utils.skyblock.Supply
import shizo.utils.skyblock.dungeon.DungeonUtils.currentRoom
import shizo.utils.skyblock.dungeon.DungeonUtils.currentRoomName
import kotlin.jvm.optionals.getOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object KuudraUtils {
    enum class Crate(val id: String, val chatName: String, val x: Double, val z: Double, val radiusSq: Double) {
        X("x", "X", -142.5, -151.0, 30.0 * 30.0),
        XC("xc", "X Cannon", -143.0, -125.0, 16.0 * 16.0),
        TRI("tri", "Triangle", -67.5, -122.5, 15.0 * 15.0),
        SHOP("shop", "Shop", -81.0, -143.0, 18.0 * 18.0),
        EQUALS("equals", "Equals", -61.5, -83.5, 15.0 * 15.0),
        SLASH("slash", "Slash", -113.5, -68.5, 15.0 * 15.0),
        SQUARE("square", "Square", -143.0, -80.0, 20.0 * 20.0)
    }

    val spawnedCrates = mutableSetOf<Crate>()
    val hittableZombies = arrayListOf<Zombie>()

    inline val inKuudra get() = LocationUtils.isCurrentArea(Island.Kuudra)

    val freshers: MutableMap<String, Long?> = mutableMapOf()
    val giantZombies: ArrayList<Giant> = arrayListOf()
    var kuudraEntity: MagmaCube? = null
        private set
    var phase = 0
        private set

    val buildingPiles = arrayListOf<ArmorStand>()
    var playersBuildingAmount = 0
        private set
    var buildDonePercentage = 0
        private set

    var kuudraTier: Int = 0
        private set

    private val ownFreshRegex = Regex("^Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!$")
    private val buildRegex = Regex("Building Progress (\\d+)% \\((\\d+) Players Helping\\)")
    private val partyFreshRegex = Regex("^Party > (\\[[^]]*?])? ?(\\w{1,16}): FRESH$")
    private val tierRegex = Regex("Kuudra's Hollow \\(T(\\d)\\)$")
    private val progressRegex = Regex("PROGRESS: (\\d+)%")

    init {
        TickTask(10) {
            if (!inKuudra) return@TickTask
            val entities = Shizo.mc.level?.entitiesForRendering() ?: return@TickTask

            giantZombies.clear()
            buildingPiles.clear()
            hittableZombies.clear()

            entities.forEach { entity ->
                when (entity) {
                    is Giant -> {
                        if (entity.mainHandItem?.hoverName?.string?.endsWith("Head") == true) {
                            giantZombies.add(entity)

                            if (phase == 1) {
                                val yaw = entity.yRot
                                val cx = entity.x + (3.7 * cos((yaw + 130.0) * (PI / 180.0)))
                                val cz = entity.z + (3.7 * sin((yaw + 130.0) * (PI / 180.0)))

                                Crate.entries.forEach { crate ->
                                    if (!spawnedCrates.contains(crate)) {
                                        val distSq = (cx - crate.x) * (cx - crate.x) + (cz - crate.z) * (cz - crate.z)
                                        if (distSq < crate.radiusSq) spawnedCrates.add(crate)
                                    }
                                }
                            }
                        }
                    }

                    is Zombie -> {
                        if (phase == 1 && entity.y in 60.0..78.0) {
                            hittableZombies.add(entity)
                        }
                    }

                    is MagmaCube -> {
                        if (entity.size == 30 && entity.getAttributeBaseValue(Attributes.MAX_HEALTH) == 100000.0) {
                            kuudraEntity = entity
                        }
                    }

                    is ArmorStand -> {
                        if (entity.name.string.matches(progressRegex)) buildingPiles.add(entity)

                        if (phase == 2) {
                            buildRegex.find(entity.name.string)?.let {
                                playersBuildingAmount = it.groupValues[2].toIntOrNull() ?: 0
                                buildDonePercentage = it.groupValues[1].toIntOrNull() ?: 0
                            }
                        }
                        if (phase != 1 || entity.name.string != "✓ SUPPLIES RECEIVED ✓") return@forEach

                        val x = entity.x.toInt()
                        val z = entity.z.toInt()

                        when {
                            x == -98 && z == -112 -> Supply.Shop.isActive = false
                            x == -98 && z == -99 -> Supply.Equals.isActive = false
                            x == -110 && z == -106 -> Supply.xCannon.isActive = false
                            x == -106 && z == -112 -> Supply.X.isActive = false
                            x == -94 && z == -106 -> Supply.Triangle.isActive = false
                            x == -106 && z == -99 -> Supply.Slash.isActive = false
                        }
                    }
                }
            }
        }

        on<ChatPacketEvent> {
            if (!inKuudra) return@on

            when (value) {
                "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!" -> {
                    phase = 1
                    spawnedCrates.clear()
                }

                "[NPC] Elle: OMG! Great work collecting my supplies!" -> phase = 2
                "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" -> phase = 3
                "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!" -> phase = 4
            }

            partyFreshRegex.find(value)?.groupValues?.get(2)?.let { playerName ->
                freshers[playerName] = System.currentTimeMillis()
                schedule(200, true) {
                    freshers[playerName] = null
                }
            }

            ownFreshRegex.find(value)?.let {
                freshers[Shizo.mc.player?.name?.string ?: "self"] = System.currentTimeMillis()
                schedule(200, true) {
                    freshers[Shizo.mc.player?.name?.string ?: "self"] = null
                }
            }
        }

        onReceive<ClientboundSetPlayerTeamPacket> {
            if (!inKuudra) return@onReceive
            val teamLine = parameters.getOrNull() ?: return@onReceive
            val text = teamLine.playerPrefix.string?.plus(teamLine.playerSuffix.string)?.noControlCodes ?: return@onReceive

            tierRegex.find(text)?.groupValues?.get(1)?.let { kuudraTier = it.toInt() }
        }

        on<WorldEvent.Load> {
            Supply.entries.forEach { it.isActive = true }
            playersBuildingAmount = 0
            buildDonePercentage = 0
            buildingPiles.clear()
            giantZombies.clear()
            spawnedCrates.clear()
            hittableZombies.clear()
            kuudraEntity = null
            freshers.clear()
            kuudraTier = 0
            phase = 0
        }
    }

    fun getPlayerPreSpot(x: Double, z: Double): Crate {
        return when {
            z > -100 -> if (x > -90) Crate.EQUALS else Crate.SLASH
            else -> if (x > -90) Crate.TRI else Crate.X
        }
    }
    private fun isValidKuudraEntity(entity: Entity): Boolean {
        if (!entity.isAlive) return false
        //if (entity.isInvisible) return false

        val y = entity.y
        if (y !in 60.0..78.0) return false

        if (entity is LivingEntity) {
            val hasArmor = !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty ||
                    !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty ||
                    !entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty ||
                    !entity.getItemBySlot(EquipmentSlot.FEET).isEmpty
            // surely there is a better wayt od o this NO????
            if (hasArmor) return false
        }

        return true
    }
    // 4 routes
    val activeRouteArea: String
        get() {
            if (inKuudra) return "Kuudra"
            return currentRoom?.let { currentRoomName } ?: currentArea.toString()
        }

    fun getRealLoc(pos: BlockPos): BlockPos {
        if (inKuudra) return pos
        return currentRoom?.getRealCoords(pos) ?: pos
    }

    fun getRelLoc(pos: BlockPos): BlockPos {
        if (inKuudra) return pos
        return currentRoom?.getRelativeCoords(pos) ?: pos
    }

    fun getRealY(yaw: Float): Float {
        if (inKuudra) return yaw
        return currentRoom?.getRealYaw(yaw) ?: yaw
    }

    fun getRelY(yaw: Float): Float {
        if (inKuudra) return yaw
        return currentRoom?.getRelativeYaw(yaw) ?: yaw
    }

}