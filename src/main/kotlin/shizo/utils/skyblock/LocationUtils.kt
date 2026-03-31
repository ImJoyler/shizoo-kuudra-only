package shizo.utils.skyblock

import shizo.Shizo.mc
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.utils.equalsOneOf
import shizo.utils.startsWithOneOf
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import shizo.events.RoomEnterEvent
import shizo.events.EnterAreaEvent
import kotlin.jvm.optionals.getOrNull

object LocationUtils {
    var isInSkyblock: Boolean = false
        private set

    var currentArea: Island = Island.Unknown
        private set

    var lobbyId: String? = null
        private set

    private val lobbyRegex = Regex("\\d\\d/\\d\\d/\\d\\d (\\w{0,6}) *")

    init {
        onReceive<ClientboundPlayerInfoUpdatePacket> {
            if (!isCurrentArea(Island.Unknown) || actions().none { it.equalsOneOf(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME) }) return@onReceive
            val area = entries()?.find { it?.displayName?.string?.startsWithOneOf("Area: ", "Dungeon: ") == true }?.displayName?.string ?: return@onReceive
            currentArea = Island.entries.firstOrNull { area.contains(it.displayName, true) } ?: Island.Unknown
            EnterAreaEvent(currentArea).postAndCatch()
        }

        onReceive<ClientboundSetObjectivePacket> {
            if (!isInSkyblock) isInSkyblock = objectiveName == "SBScoreboard"
        }

        onReceive<ClientboundSetPlayerTeamPacket> {
            if (!isCurrentArea(Island.Unknown)) return@onReceive
            val text = parameters?.getOrNull()?.let { it.playerPrefix?.string?.plus(it.playerSuffix?.string) } ?: return@onReceive

            lobbyRegex.find(text)?.groupValues?.get(1)?.let { lobbyId = it }
        }

        on<WorldEvent.Load> {
            currentArea = if (mc.isSingleplayer) Island.SinglePlayer else Island.Unknown
            isInSkyblock = false
            lobbyId = null
        }

    }

    fun isCurrentArea(vararg areas: Island): Boolean =
        if (currentArea == Island.SinglePlayer) true
        else areas.any { currentArea == it }
}