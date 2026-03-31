package shizo.module.impl.render

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.ColorSetting
import shizo.clickgui.settings.impl.DropdownSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.RenderEvent
import shizo.events.core.EventPriority
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.events.core.onSend
import shizo.module.impl.Module
import shizo.utils.Color.Companion.withAlpha
import shizo.utils.Colors
import shizo.utils.createSoundSettings
import shizo.utils.getBlockBounds
import shizo.utils.handlers.EtherwarpHelper
import shizo.utils.isEtherwarpItem
import shizo.utils.playSoundAtPlayer
import shizo.utils.playSoundSettings
import shizo.utils.renderUtils.renderUtils.drawStyledBox
import shizo.utils.skyblock.Island
import shizo.utils.skyblock.LocationUtils

object Etherwarp : Module(
    name = "Etherwarp",
    description = "Provides configurable visual feedback for etherwarp."
) {
    private val render by BooleanSetting("Show Guess", true, desc = "Shows where etherwarp will take you.")
    private val color by ColorSetting(
        "Color",
        Colors.MINECRAFT_GOLD.withAlpha(.5f),
        true,
        desc = "Color of the box."
    ).withDependency { render }
    private val renderFail by BooleanSetting(
        "Show when failed",
        true,
        desc = "Shows the box even when the guess failed."
    ).withDependency { render }
    private val failColor by ColorSetting(
        "Fail Color",
        Colors.MINECRAFT_RED.withAlpha(.5f),
        true,
        desc = "Color of the box if guess failed."
    ).withDependency { renderFail }
    private val renderStyle by SelectorSetting(
        "Render Style",
        "Outline",
        listOf("Filled", "Outline", "Filled Outline"),
        desc = "Style of the box."
    ).withDependency { render }
    private val useServerPosition by BooleanSetting(
        "Use Server Position",
        false,
        desc = "Uses the server position for etherwarp instead of the client position."
    ).withDependency { render }
    private val fullBlock by BooleanSetting(
        "Full Block",
        false,
        desc = "Renders the the 1x1x1 block instead of it's actual size."
    ).withDependency { render }
    private val depth by BooleanSetting("Depth", false, desc = "Renders the box through walls.").withDependency { render }

    private val dropdown by DropdownSetting("Sounds", false)
    private val sounds by BooleanSetting(
        "Custom Sounds",
        false,
        desc = "Plays the selected custom sound when you etherwarp."
    ).withDependency { dropdown }
    private val soundSettings = createSoundSettings("Etherwarp Sound", "entity.experience_orb.pickup") { sounds && dropdown }

    //private val zeroPing by BooleanSetting("Zero Ping", true, desc = "Instantly handles teleportation to remove latency.")


    private var etherPos: EtherwarpHelper.EtherPos? = null

    private var cachedMainHandItem: ItemStack? = null
    private var cachedEtherData: CompoundTag? = null

    private var SentPosition = false
    private var isWarping = false
        init {
        onReceive<ClientboundSoundPacket> {
            if (!sounds || sound.value() != SoundEvents.ENDER_DRAGON_HURT || volume != 1f || pitch != 0.53968257f) return@onReceive
            playSoundSettings(soundSettings())
            it.cancel()
       }

        on<RenderEvent.Extract> (EventPriority.LOW) {
            if (mc.player?.isShiftKeyDown == false || mc.screen != null || !render) return@on

            val mainHandItem = mc.player?.mainHandItem ?: return@on

            if (cachedMainHandItem !== mainHandItem) {
                cachedMainHandItem = mainHandItem
                cachedEtherData = mainHandItem.isEtherwarpItem()
            }

            if (cachedEtherData == null) return@on

            etherPos = EtherwarpHelper.getEtherPos(
                if (useServerPosition) mc.player?.oldPosition() else mc.player?.position(),
                57.0 + (cachedEtherData?.getInt("tuned_transmission")?.orElse(0) ?: 0),
                etherWarp = true
            )
            if (etherPos?.succeeded != true && !renderFail) return@on
            val color = if (etherPos?.succeeded == true) color else failColor
            etherPos?.pos?.let { pos ->
                val box = if (fullBlock) AABB(pos) else pos.getBlockBounds()?.move(pos) ?: AABB(pos)

                drawStyledBox(box, color, renderStyle, depth)
            }
        }

        onSend<ServerboundUseItemPacket> {
            if (!LocationUtils.isCurrentArea(Island.SinglePlayer) || mc.player?.isShiftKeyDown == false || cachedEtherData == null) return@onSend

            etherPos?.pos?.let {
                if (etherPos?.succeeded == false) return@onSend
                mc.player?.connection?.send(
                    ServerboundMovePlayerPacket.PosRot(
                        it.x + 0.5, it.y + 1.05, it.z + 0.5, mc.player?.yRot ?: 0f,
                        mc.player?.xRot ?: 0f, false, false
                    )
                )
                mc.player?.setPos(it.x + 0.5, it.y + 1.05, it.z + 0.5)
                mc.player?.setDeltaMovement(0.0, 0.0, 0.0)
                if (sounds) playSoundSettings(soundSettings())
                else playSoundAtPlayer(SoundEvents.ENDER_DRAGON_HURT, pitch = 0.53968257f)
            }
        }
    }
}