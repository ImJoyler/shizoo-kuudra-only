package shizo.utils.handlers

import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import shizo.events.ActionBarMessageEvent
import shizo.events.PacketEvent
import shizo.events.core.EventBus
import shizo.events.core.on
import shizo.utils.skyblock.dungeon.DungeonUtils

object SbStatTracker {

    private val MANA_PATTERN = Regex("(\\d+)/(\\d+)✎")
    private val HEALTH_PATTERN = Regex("(\\d+)/(\\d+)❤")
    private val OVERFLOW_MANA_PATTERN = Regex("(\\d+)ʬ")
    private val DEFENSE_PATTERN = Regex("([+-]\\d+)❈ Defense")
    private val SECRETS_PATTERN = Regex("([0-9]+)/([0-9]+) Secrets")

    val stats = SbStats()

    init {
        EventBus.subscribe(this)

        on<PacketEvent.Receive> {
            val packet = this.packet

            if (packet is ClientboundSetActionBarTextPacket) {
                ActionBarMessageEvent(packet.text).postAndCatch()
            }
            else if (packet is ClientboundSystemChatPacket && packet.overlay) {
                ActionBarMessageEvent(packet.content).postAndCatch()
            }
        }

        on<ActionBarMessageEvent> {
            val text = this.unformattedText.replace(",", "")

            MANA_PATTERN.find(text)?.let {
                stats.mana.current = it.groupValues[1].toIntOrNull() ?: 0
                stats.mana.max = it.groupValues[2].toIntOrNull() ?: 0
            }

            HEALTH_PATTERN.find(text)?.let {
                stats.hp.current = it.groupValues[1].toIntOrNull() ?: 0
                stats.hp.max = it.groupValues[2].toIntOrNull() ?: 0
            }

            DEFENSE_PATTERN.find(text)?.let {
                stats.defense = it.groupValues[1].toIntOrNull() ?: 0
            }

            OVERFLOW_MANA_PATTERN.find(text)?.let {
                stats.overflowMana = it.groupValues[1].toIntOrNull() ?: 0
            }

            if (DungeonUtils.inDungeons) {
                SECRETS_PATTERN.find(text)?.let {
                    stats.secrets.current = it.groupValues[1].toIntOrNull() ?: -1
                    stats.secrets.max = it.groupValues[2].toIntOrNull() ?: -1
                }
            }
        }
    }
    class SbStats {
        val hp = Stat()
        val mana = Stat()
        val secrets = Stat()
        var defense: Int = 0
        var overflowMana: Int = 0
    }

    data class Stat(var current: Int = 0, var max: Int = 0)
}