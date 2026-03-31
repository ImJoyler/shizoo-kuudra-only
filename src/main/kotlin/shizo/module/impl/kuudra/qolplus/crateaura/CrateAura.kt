package shizo.module.impl.kuudra.qolplus.crateaura

import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.ActionBarMessageEvent
import shizo.events.ChatPacketEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.utils.devMessage
import shizo.utils.modMessage
import shizo.utils.noControlCodes
import shizo.utils.onGround
import shizo.utils.skyblock.kuudra.KuudraUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object CrateAura : Module(
    name = "Crate Aura",
    description = "aura",
    subcategory = "QOL"

) {
    private val reach by NumberSetting("Reach", 4.5, 3.0, 6.0, 0.1, "Maximum reach distance")
    private val rightClicking by BooleanSetting(
        "Lef click only",
        true,
        "turns on aura only if left click is pressed down (spec safeish)"
    )
    private val requireRod by BooleanSetting("Require Rod", true, "Only works when holding a fishing rod")
    private val isntapickup by BooleanSetting(
        "Insta pick UP",
        true,
        "Ignores left click only for the first second of crates spawning"
    )
    private val onlyOnGround by BooleanSetting(
        "Only On Ground",
        true,
        "Prevents the aura from clicking if you are jumping or falling"
    )

    // test feature
    private val fovCheck by BooleanSetting(
        "Check FOV",
        true,
        "TEST FEATURE"
    )
    private val fov by NumberSetting("FOV", 180.0, 30.0, 360.0, 1.0, "180 = anything in front of you think of it liike an angle..." ).withDependency { fovCheck }
    private var picking = false
    private val pickupRegex = Regex("\\[[|]+\\]\\s*(\\d+)%")
    var instapickupTime = 0L

    init {

        on<WorldEvent.Load> {
            instapickupTime = 0L
            picking = false
        }
        on<ChatPacketEvent> {
            if (value.contains("You retrieved some of Elle's supplies from the Lava!") || value.contains("You moved and the Chest slipped out of your hands!")) {
                picking = false
                devMessage("Crate finished or dropped! Picking paused.")
            }
            if (isntapickup) {
                if (value.contains("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!")) {
                    instapickupTime = 190 // 9 seconds to spawn so we put 9.5
                }
            }
        }
        val checkCrateProgress = { msg: String ->
            pickupRegex.find(msg)?.let { match ->
                val percent = match.groupValues[1].toIntOrNull() ?: 0
                picking = percent < 100
                val statusColor = if (picking) "§a" else "§c"
                devMessage("Read: §b$percent%, clicking: $statusColor$picking") // this means we can use 100 PEAK
            }
        }

        onReceive<net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket> { checkCrateProgress(this.text.string.noControlCodes) }
        onReceive<net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket> { checkCrateProgress(this.text.string.noControlCodes) }
        onReceive<net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket> { checkCrateProgress(this.text.string.noControlCodes) }


        on<TickEvent.Server> {
            if (isntapickup && instapickupTime > 0) {
                instapickupTime--
            }
        }

        on<TickEvent.Start> {

            if (mc.player == null || mc.level == null) return@on
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1) return@on
            if (picking || mc.screen != null) return@on
            if (instapickupTime <= 1) {
                if (rightClicking && !mc.options.keyUse.isDown) return@on
            }
            val player = mc.player!!
            if (onlyOnGround && !onGround()) return@on
            if (requireRod && player.mainHandItem.item != Items.FISHING_ROD) return@on

            var closestEntity: Zombie? = null
            var closestDistanceSq = (reach * reach)

            mc.level?.entitiesForRendering()?.forEach { entity ->

                if (isValidCrate(entity) && (!fovCheck || isInFov(player, entity, fov))) {

                    val distSq = distanceSqToHitbox(player, entity)
                    if (distSq < closestDistanceSq) {
                        closestDistanceSq = distSq
                        closestEntity = entity as Zombie
                    }
                }
            }

            closestEntity?.let { target ->
                val hitVec = getClosestPointOnHitbox(player, target)

                val isSneaking = player.isShiftKeyDown

                mc.connection?.send(
                    ServerboundInteractPacket.createInteractionPacket(
                        target, isSneaking,
                        InteractionHand.MAIN_HAND, hitVec
                    )
                )
                devMessage("Clicked supply at distance: ${sqrt(closestDistanceSq)}")

                mc.connection?.send(
                    ServerboundInteractPacket.createInteractionPacket(
                        target,
                        isSneaking,
                        InteractionHand.MAIN_HAND
                    )
                )
            }
        }
    }
}

 fun distanceSqToHitbox(player: Player, entity: Entity): Double {
    val box1 = player.boundingBox
    val box2 = entity.boundingBox

    val dx = max(0.0, max(box1.minX - box2.maxX, box2.minX - box1.maxX))
    val dy = max(0.0, max(box1.minY - box2.maxY, box2.minY - box1.maxY))
    val dz = max(0.0, max(box1.minZ - box2.maxZ, box2.minZ - box1.maxZ))

    return dx * dx + dy * dy + dz * dz
}

// yes i am lazy to do remove private from supply highlighter :D
 fun isValidCrate(entity: Entity): Boolean {
    if (entity !is Zombie) return false
    if (!entity.isAlive) return false

    val y = entity.y
    if (y !in 60.0..78.0) return false

    val hasArmor = !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty ||
            !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty ||
            !entity.getItemBySlot(EquipmentSlot.LEGS).isEmpty ||
            !entity.getItemBySlot(EquipmentSlot.FEET).isEmpty
    if (hasArmor) return false
    return true
}

 fun getClosestPointOnHitbox(player: Player, entity: Entity): Vec3 {
    val pBox = player.boundingBox
    val eBox = entity.boundingBox

    val closestX = when {
        pBox.maxX < eBox.minX -> eBox.minX
        pBox.minX > eBox.maxX -> eBox.maxX
        else -> (max(pBox.minX, eBox.minX) + min(pBox.maxX, eBox.maxX)) / 2.0
    }

    val closestY = when {
        pBox.maxY < eBox.minY -> eBox.minY
        pBox.minY > eBox.maxY -> eBox.maxY
        else -> (max(pBox.minY, eBox.minY) + min(pBox.maxY, eBox.maxY)) / 2.0
    }

    val closestZ = when {
        pBox.maxZ < eBox.minZ -> eBox.minZ
        pBox.minZ > eBox.maxZ -> eBox.maxZ
        else -> (max(pBox.minZ, eBox.minZ) + min(pBox.maxZ, eBox.maxZ)) / 2.0
    }

    return Vec3(closestX - entity.x, closestY - entity.y, closestZ - entity.z)
}

fun isInFov(player: Player, entity: Entity, fovLimit: Double): Boolean {
    val lookVec = player.getViewVector(1.0f)

    val relativePoint = getClosestPointOnHitbox(player, entity)

    val absoluteClosestPoint = entity.position().add(relativePoint)
    val dirToEntity = absoluteClosestPoint.subtract(player.eyePosition).normalize()

    val dotProduct = lookVec.dot(dirToEntity)
    val angle = Math.toDegrees(kotlin.math.acos(dotProduct.coerceIn(-1.0, 1.0)))

    return angle <= (fovLimit / 2.0)
}
