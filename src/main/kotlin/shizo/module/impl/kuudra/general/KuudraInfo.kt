package shizo.module.impl.kuudra.general

import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.MagmaCube
import net.minecraft.world.entity.monster.Zombie
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.ColorSetting
import shizo.events.ChatPacketEvent
import shizo.events.ClickEvent
import shizo.events.EntityCheckRenderEvent
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.module.impl.kuudra.qolplus.crateaura.distanceSqToHitbox
import shizo.module.impl.kuudra.qolplus.crateaura.isValidCrate
import shizo.utils.Colors
import shizo.utils.alert
import shizo.utils.createSoundSettings
import shizo.utils.devMessage
import shizo.utils.isEtherwarpItem
import shizo.utils.isHoldingByName
import shizo.utils.playSoundSettings
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.renderUtils.renderUtils.drawWireFrameBox
import kotlin.math.sqrt

object KuudraInfo : Module(
    name = "Kuudra Qol",
    description = "Kairo Asked."
) {
    private val highlightKuudra by BooleanSetting("Highlight Kuudra", true, desc = "Highlights the kuudra.")
    private val blockMenu by BooleanSetting("Block Menu", true, desc = "Blocks opening menu in p1.")
    private val kuudraColor by ColorSetting(
        "Kuudra Color",
        Colors.MINECRAFT_RED,
        true,
        desc = "Color of the tentacles highlight."
    ).withDependency { highlightKuudra }

    private val dpsHelper by BooleanSetting(
        "DPS helper",
        true,
        desc = "Hides Kuudra in Phase 3 when holding Aspect/Etherwarp."
    )
    private val p4Kuudra by BooleanSetting(
        "Tentacles Hider",
        true,
        desc = "Hides Tentacles and draws a frame around them"
    )
    private val p4EspColor by ColorSetting(
        "Tentacles Colour",
        Colors.WHITE,
        true,
        desc = "Color of the tentacles."
    ).withDependency { p4Kuudra }

    private val clickThrough by BooleanSetting(
        "Click Through",
        true,
        desc = "Clicks through Players (P1/P4) and Zombies (P2)."
    )
    private val clickThroughCannon by BooleanSetting(
        "Click Through Cannon",
        true,
        desc = "Clicks through the cannon hitbox to prevent mounting before needed"
    )
    var cannonballPurchased = false
    private val kuudraSpawnAlert by BooleanSetting(
        "Kuudra Spawn Alert",
        true,
        desc = "Alerts you where Kuudra spawns in Phase 4."
    )
    private val showCannon by BooleanSetting("Cannon Hitbox", true, "Shows cannnon hitbox")
    private val chat by BooleanSetting("Distance", true, "Thing")

    private var lastDirection = ""

    private val supplysound by BooleanSetting("Supply sound", false, "plays a sound when arleady picking a supply")
    private val soundSettings = createSoundSettings("Sound Type", "entity.experience_orb.pickup") { supplysound }

    @JvmStatic
    fun clickThroughEnabled(): Boolean {
        return clickThrough
    }
    @JvmStatic
    fun shouldClickThroughCannon(): Boolean {
        return clickThroughCannon && !cannonballPurchased
    }
    init {
        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (!chat) return@register net.minecraft.world.InteractionResult.PASS
            if (world.isClientSide && entity is Zombie) {
                if (isValidCrate(entity) ) {
                    val distSq = distanceSqToHitbox(player, entity)
                    devMessage(" Manual click distance: §f${sqrt(distSq)}")
                }
            }
            InteractionResult.PASS
        }
        on<WorldEvent.Load> {
            lastDirection = ""
            cannonballPurchased = false
        }
        on<TickEvent.Server> {
            if (!KuudraUtils.inKuudra) return@on

            val kuudra = KuudraUtils.kuudraEntity ?: return@on
            val hp = kuudra.health

            if (kuudraSpawnAlert && hp in 24900f..25000f) {
                val x = kuudra.x
                val z = kuudra.z

                val alertText = when {
                    x < -128.0 -> "§c§lRIGHT!"
                    z > -84.0 -> "§2§lFRONT!"
                    x > -72.0 -> "§a§lLEFT!"
                    z < -132.0 -> "§4§lBACK!"
                    else -> null
                }

                if (alertText != null) {
                    val shouldPling = (lastDirection != alertText)

                    alert(alertText, playSound = shouldPling, stay = 25)

                    lastDirection = alertText
                }
            } else {
                lastDirection = ""
            }
        }
        on<ClickEvent> {
            if (button != 1 || action != 1) return@on
            if (!blockMenu || KuudraUtils.phase != 1) return@on

            val player = mc.player ?: return@on
            if (player.inventory.selectedSlot != 8) return@on
            if (isHoldingByName("Skyblock Menu", "Elle")) {
                cancel()
                devMessage("Prevented ")
            }
        }

        on<EntityCheckRenderEvent> {
            if (!KuudraUtils.inKuudra) return@on
            val entity = entity as? MagmaCube ?: return@on
            // kuudra
            if (dpsHelper && KuudraUtils.phase == 3) {
                val player = mc.player ?: return@on
                val heldItem = player.mainHandItem

                if (isHoldingByName("aspect") || heldItem.isEtherwarpItem() != null) {
                    cancel()
                    return@on
                }
            }

            // tentacles
            if (p4Kuudra && KuudraUtils.phase == 6) {
                if (entity.bbWidth > 5f) {
                    cancel()
                }
            }
        }

        on<RenderEvent.Extract> {
            if (!KuudraUtils.inKuudra) return@on
            val player = mc.player ?: return@on

            if (highlightKuudra) {
                KuudraUtils.kuudraEntity?.let {
                    drawWireFrameBox(it.boundingBox, kuudraColor, depth = false)
                }
            }

            // tentacles
            if (p4Kuudra && KuudraUtils.phase == 6) {
                val nearbyCubes =
                    mc.level?.getEntitiesOfClass(MagmaCube::class.java, player.boundingBox.inflate(40.0)) ?: return@on

                for (cube in nearbyCubes) {
                    if (cube.bbWidth > 10f) {
                        drawWireFrameBox(cube.boundingBox, p4EspColor, depth = false)
                    }
                }
            }

            if (showCannon) {
                if (KuudraUtils.phase == 3 || KuudraUtils.phase == 2) {

                    // might move this to kuudrautils if i ever need it for naything else
                    val cannons = mc.level?.entitiesForRendering()?.filterIsInstance<ArmorStand>()?.filter {
                        it.name.string.contains("LEFT-CLICK", ignoreCase = true)
                    } ?: emptyList()

                    for (cannon in cannons) {
                        drawWireFrameBox(cannon.boundingBox.inflate(0.5), Colors.WHITE, depth = true)
                    }
                }

            }

        }
        on<ChatPacketEvent> {
            if (value.contains("You are arleady currently picking up some supplies!") || value.contains("Someone else is currently trying to pick up these supplies!")) {
                if (supplysound) {
                    playSoundSettings(soundSettings())
                }
            }
            if (value.contains("You purchased Human Cannonball!", ignoreCase = true)) {
                cannonballPurchased = true
                devMessage("Cannonball purchased! Cannon hitboxes are now solid.")
            }
        }
    }
}