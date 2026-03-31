package shizo.events

import shizo.Shizo.mc
import shizo.events.core.onReceive
import shizo.events.core.onSend

import shizo.utils.equalsOneOf
import shizo.utils.containsOneOf
import shizo.utils.ChatManager
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.RenderBatchManager
import shizo.utils.skyblock.dungeon.DungeonUtils
import shizo.utils.skyblock.dungeon.DungeonUtils.isSecret
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.network.protocol.game.*
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.item.ItemEntity

object EventDispatcher {

    init {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            WorldEvent.Load().postAndCatch()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            WorldEvent.Unload().postAndCatch()
        }

        ClientTickEvents.START_WORLD_TICK.register { world ->
            mc.level?.let { TickEvent.Start(world).postAndCatch() }
        }

        ClientTickEvents.START_WORLD_TICK.register { world ->
            if (!DungeonUtils.inDungeons) return@register
            val player = mc.player ?: return@register

            val bat = world.entitiesForRendering()
                .filterIsInstance<Bat>()
                .any { it.distanceTo(player) < 10f }

            if (bat) BatSpawnEvent().postAndCatch()
        }

        ClientTickEvents.END_WORLD_TICK.register { world ->
            mc.level?.let { TickEvent.End(world).postAndCatch() }
        }

        WorldRenderEvents.END_EXTRACTION.register { handler ->
            mc.level?.let { RenderEvent.Extract(handler, RenderBatchManager.renderConsumer).postAndCatch() }
        }

        WorldRenderEvents.END_MAIN.register { context ->
            mc.level?.let { RenderEvent.Last(context).postAndCatch() }
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register { text, overlay ->
            if (overlay) return@register true
            !ChatManager.shouldCancelMessage(text)
        }

        onReceive<ClientboundTakeItemEntityPacket> {
            if (mc.player == null || !DungeonUtils.inClear) return@onReceive
            val itemEntity = mc.level?.getEntity(itemId) as? ItemEntity ?: return@onReceive
            if (itemEntity.item?.hoverName?.string?.containsOneOf(dungeonItemDrops, true) == true && itemEntity.distanceTo(mc.player ?: return@onReceive) <= 6)
                SecretPickupEvent.Item(itemEntity).postAndCatch()
        }

        onReceive<ClientboundRemoveEntitiesPacket> {
            if (mc.player == null || !DungeonUtils.inClear) return@onReceive
            entityIds.forEach { id ->
                val entity = mc.level?.getEntity(id) as? ItemEntity ?: return@forEach
                if (entity.item?.hoverName?.string?.containsOneOf(dungeonItemDrops, true) == true && entity.distanceTo(mc.player ?: return@onReceive) <= 6)
                    SecretPickupEvent.Item(entity).postAndCatch()
            }
        }

        onReceive<ClientboundSoundPacket> {
            if (!DungeonUtils.inClear) return@onReceive
            if (sound.value().equalsOneOf(SoundEvents.BAT_HURT, SoundEvents.BAT_DEATH) && volume == 0.1f)
                SecretPickupEvent.Bat(this).postAndCatch()
        }

        onSend<ServerboundUseItemOnPacket> {
            if (!DungeonUtils.inDungeons || hand == InteractionHand.OFF_HAND) return@onSend
            SecretPickupEvent.Interact(
                hitResult.blockPos,
                mc.level?.getBlockState(hitResult.blockPos)?.takeIf { isSecret(it, hitResult.blockPos) } ?: return@onSend
            ).postAndCatch()
        }

        onReceive<ClientboundSystemChatPacket> {
            if (!overlay) content?.string?.noControlCodes?.let { ChatPacketEvent(it, content).postAndCatch() }
        }

    }

    private val dungeonItemDrops = listOf(
        "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion", "Healing Potion VIII Splash Potion", "Healing VIII Splash Potion", "Healing 8 Splash Potion",
        "Decoy", "Inflatable Jerry", "Spirit Leap", "Trap", "Training Weights", "Defuse Kit", "Dungeon Chest Key", "Treasure Talisman", "Revive Stone", "Architect's First Draft",
        "Secret Dye", "Candycomb"
    )
}