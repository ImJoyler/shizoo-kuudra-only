package shizo.module.impl.kuudra.phaseone

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.ColorSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.module.impl.kuudra.qolplus.crateaura.getClosestPointOnHitbox
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.devMessage
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.drawLine
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.renderUtils.renderUtils.drawStyledBox
import shizo.utils.renderUtils.renderUtils.getStringWidth
import shizo.utils.renderUtils.renderUtils.textDim
import java.lang.Math.cos
import kotlin.math.hypot
import kotlin.math.sin

object SupplyHighlighter : Module(
    name = "Supply Highlighter",
    description = "Highlights supplies and hittable hitboxes in Kuudra",
    subcategory = "Phase 1"
) {

    private val highlightPreSpawns by BooleanSetting("Pre Spawn", true, desc = "Draws a circle around supply armor stands.")
    private val preSpawnColor by ColorSetting("Pre-Spawn Color", Color(255, 0, 0, 150f), true, desc = "Color for the empty pre-spawn circle.").withDependency { highlightPreSpawns }
    private val hookedColor by ColorSetting("Drag Color", Color(0, 255, 0, 150f), true, desc = "Color when your bobber lands inside.").withDependency { highlightPreSpawns }
    private val preSpawnRadius by NumberSetting("Pre-Spawn Radius", 4.0, 1.0, 10.0, 0.5, "Radius of the collection circle.").withDependency { highlightPreSpawns }


    //private val clickThroughGiants by BooleanSetting("Bye Bye giants", false, desc = "Moves Giants away so you can click and pearl through them.")
    private val highlightGiants by BooleanSetting("Highlight Giants", true, desc = "Highlights the Giant part")
    private val giantColor by ColorSetting(
        "Giant Color",
        Color(255, 0, 0, 150f),
        true,
        desc = "Color for Giants."
    ).withDependency { highlightGiants }
    private val compactGiants by BooleanSetting(
        "Compact Giant Box",
        true,
        desc = "Only highlights the top part of the Giant."
    ).withDependency { highlightGiants }

    private val doublePearlWarning by BooleanSetting(
        "Double Pearl Warning",
        true,
        desc = "Warns when you are stuck and need to double pearl."
    )

    private val highlightZombies by BooleanSetting("Highlight Zombies", true, desc = "Highlights Zombies (hittable).")
    private val zombieColor by ColorSetting("Zombie Color", Color(0, 255, 0, 150f), true, desc = "Color for Zombies.").withDependency { highlightZombies }

    private val style by SelectorSetting(
        "Style",
        "Outline",
        arrayListOf("Filled", "Outline", "Both"),
        desc = "Render style."
    )
    private val depth by BooleanSetting("Depth", true, "Depth check")
    // michi's thing pt 2
  private val showReachableBox by BooleanSetting("Show Reachable Box", true, desc = "Highlights the exact portion of the crate you can reach.")
    private val reachableColor by ColorSetting("Hittable Color", Color(0, 255, 255, 150f), true, desc = "Color for the reachable part.").withDependency { showReachableBox }
    private val reachDistance by NumberSetting("Hit Distance", 3.0, 3.0, 6.0, 0.1, "THIS IS JUST FOR TESTING normal mc is 3.").withDependency { showReachableBox }

    private val zombies = mutableSetOf<Zombie>()
    private val giants = mutableSetOf<Giant>()
    private val preSpawns = mutableSetOf<ArmorStand>()
    // ty germ peolpe
    private var doublePearlZombie = false
    private var doublePearlGiant = false

    val warningHud by HUD("Double Pearl Warning", "Displays when you need to double pearl.", false) { example ->
        if (example) {
            val txt = "§cDouble Pearl Needed!"
            textDim(txt, 0, 0, Colors.WHITE)
            return@HUD getStringWidth(txt) to 9
        }

        if (doublePearlWarning && (doublePearlZombie || doublePearlGiant)) {
            val txt = "§cDouble Pearl Needed!"
            textDim(txt, 0, 0, Colors.WHITE)
            return@HUD getStringWidth(txt) to 9
        }

        0 to 0
    }

    init {
        on<TickEvent.Start> {
            if (!KuudraUtils.inKuudra) {
                if (zombies.isNotEmpty()) zombies.clear()
                if (giants.isNotEmpty()) giants.clear()
                if (preSpawns.isNotEmpty()) preSpawns.clear()
                doublePearlZombie = false
                doublePearlGiant = false
                return@on
            }

            if (doublePearlWarning) {
                doublePearlZombie = false
                doublePearlGiant = false
                val player = mc.player

                if (player != null) {
                    val px = player.x
                    val pz = player.z

                    mc.level?.entitiesForRendering()?.forEach { entity ->
                        if (entity is Giant) {
                            if ((entity.y > 30.0 && entity.y < 67.0)) {
                                if (intersectsPearlSpawn(px, player.y, pz, entity)) {
                                    doublePearlGiant = true
                                }
                            }
                        }
//                        } else if (entity is Zombie && entity !is ZombifiedPiglin) {
//                            if (entity.isInvisible && entity.y > 30.0) {
//                                if (intersects2D(px, pz, entity.x, entity.z, entity.bbWidth.toDouble())) {
//                                    doublePearlZombie = true
//                                }
//                            }
//                        }
                    }
                }
            }
//            if (clickThroughGiants) {
//                mc.level?.entitiesForRendering()?.forEach { entity ->
//                    if (entity is Giant && entity.y < 1000.0) {
//                        entity.setPos(entity.x, 9999999.0, entity.z)
//                    }
//                }
//            }

            if ((mc.player?.tickCount ?: 0) % 5 != 0) return@on

            zombies.removeIf { !isValidKuudraEntity(it) }
            giants.removeIf { !isValidKuudraEntity(it) }
            preSpawns.removeIf { !isValidPreSpawn(it) }

            mc.level?.entitiesForRendering()?.forEach { entity ->
                if (entity is Giant && isValidKuudraEntity(entity)) {
                    giants.add(entity)
                } else if (entity is Zombie && isValidKuudraEntity(entity)) {
                    zombies.add(entity)
                } else if (highlightPreSpawns && entity is ArmorStand) {
                    val name = entity.name.string.noControlCodes
                    if (name.contains("SUPPLY", ignoreCase = true) || name.contains("SUPPLIES", ignoreCase = true)) {
                        if (isValidPreSpawn(entity)) {
                            if (preSpawns.add(entity)) {
                                devMessage(" Found armoru stand")
                            }
                        }
                    }
                }

            }
        }

        on<RenderEvent.Extract> {
            if (!KuudraUtils.inKuudra) return@on

            val styleInt = when (style) {
                0 -> 0
                1 -> 1
                else -> 2
            }
            if (KuudraUtils.phase == 1) {
                if (highlightGiants) {
                    giants.forEach { drawGiantBox(it, giantColor, styleInt) }
                }
                if (highlightZombies) {
                    zombies.forEach { drawZombieBox(it, zombieColor, styleInt) }
                }
            }

            if (highlightPreSpawns) {
                val bobber = mc.player?.fishing
                preSpawns.forEach { stand ->
                    val center = Vec3(stand.x, 75.1, stand.z)
                    val r = preSpawnRadius

                    val isHooked = bobber != null && hypot(bobber.x - stand.x, bobber.z - stand.z) <= r
                    val activeColor = if (isHooked) hookedColor else preSpawnColor
                    val points = mutableListOf<Vec3>()
                    val segments = 40

                    for (i in 0..segments) {
                        val angle = (i.toDouble() / segments) * 2 * Math.PI
                        points.add(Vec3(center.x + cos(angle) * r, center.y, center.z + sin(angle) * r))
                    }

                    drawLine(points, activeColor, depth = false, thickness = 3f)
                }
            }
        }
    }

    private fun isValidPreSpawn(entity: ArmorStand): Boolean {
        if (!entity.isAlive) return false

        if (entity.y !in 65.0..85.0) return false

        val name = entity.name.string.noControlCodes
        return name.equals("SUPPLIES", ignoreCase = true)
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
    private fun RenderEvent.Extract.drawZombieBox(entity: Entity, color: Color, styleInt: Int) {
        this.drawStyledBox(entity.boundingBox, color, styleInt, depth)

        if (showReachableBox && mc.player != null) {
            val player = mc.player!!
            // TODO CHANGE WHEN UPDATE
            val STAND_EYE_HEIGHT = 1.6200000047683716
            val SNEAK_EYE_HEIGHT = 1.5399999618530273

            val serverEyeHeight = if (player.isCrouching) SNEAK_EYE_HEIGHT else STAND_EYE_HEIGHT
            val serverEyePos = net.minecraft.world.phys.Vec3(player.x, player.y + serverEyeHeight, player.z)

            val r = reachDistance

//            val closestPoint = getClosestPointOnHitbox(player, entity).add(entity.position())
//            val trueDistance = serverEyePos.distanceTo(closestPoint)
            val eBox = entity.boundingBox

            val closestX = serverEyePos.x.coerceIn(eBox.minX, eBox.maxX)
            val closestY = serverEyePos.y.coerceIn(eBox.minY, eBox.maxY)
            val closestZ = serverEyePos.z.coerceIn(eBox.minZ, eBox.maxZ)

            val closestPointToEyes = net.minecraft.world.phys.Vec3(closestX, closestY, closestZ)
            val trueDistance = serverEyePos.distanceTo(closestPointToEyes)

            if (trueDistance <= r) {
                val reachAABB = AABB(serverEyePos.x - r, serverEyePos.y - r, serverEyePos.z - r, serverEyePos.x + r, serverEyePos.y + r, serverEyePos.z + r)
                val eBox = entity.boundingBox

                val minX = kotlin.math.max(eBox.minX, reachAABB.minX)
                val minY = kotlin.math.max(eBox.minY, reachAABB.minY)
                val minZ = kotlin.math.max(eBox.minZ, reachAABB.minZ)
                val maxX = kotlin.math.min(eBox.maxX, reachAABB.maxX)
                val maxY = kotlin.math.min(eBox.maxY, reachAABB.maxY)
                val maxZ = kotlin.math.min(eBox.maxZ, reachAABB.maxZ)

                if (minX < maxX && minY < maxY && minZ < maxZ) {
                    val reachableBox = AABB(minX, minY, minZ, maxX, maxY, maxZ)
                    this.drawStyledBox(reachableBox, reachableColor, styleInt, depth)
                }
            }
        }
    }

    private fun RenderEvent.Extract.drawGiantBox(entity: Entity, color: Color, styleInt: Int) {
        var aabb = entity.boundingBox
        if (compactGiants) {
            val height = aabb.maxY - aabb.minY
            val newMinY = aabb.maxY - (height * 0.2)

            aabb = AABB(aabb.minX, newMinY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ)
        }
        this.drawStyledBox(aabb, color, styleInt, depth)
    }

    private fun intersectsPearlSpawn(px: Double, py: Double, pz: Double, entity: Entity): Boolean {
        val eyeY = py + 1.62
        val offset = 0.3 // PEARL_SIZE (0.6 hitbox) / 2 // ty germans !

        val pMinX = px - offset
        val pMaxX = px + offset
        val pMinZ = pz - offset
        val pMaxZ = pz + offset

        val isMoved = entity.y > 9000000.0
        val boxMinY = if (isMoved) 61.0 else entity.boundingBox.minY
        val boxMaxY = if (isMoved) 73.0 else entity.boundingBox.maxY

        val boxMinX = if (isMoved) entity.x - 1.8 else entity.boundingBox.minX
        val boxMaxX = if (isMoved) entity.x + 1.8 else entity.boundingBox.maxX
        val boxMinZ = if (isMoved) entity.z - 1.8 else entity.boundingBox.minZ
        val boxMaxZ = if (isMoved) entity.z + 1.8 else entity.boundingBox.maxZ

        val overlaps2D = pMinX < boxMaxX && pMaxX > boxMinX && pMinZ < boxMaxZ && pMaxZ > boxMinZ
        val blocksPearl = eyeY in boxMinY..boxMaxY

        return overlaps2D && blocksPearl
    }

}