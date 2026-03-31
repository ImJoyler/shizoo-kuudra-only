package shizo.utils.skyblock.dungeon


import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SkullBlock
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.BlockState
import shizo.utils.equalsOneOf
import shizo.utils.skyblock.Island
import shizo.utils.skyblock.LocationUtils
import shizo.Shizo.mc
import shizo.module.impl.dungeon.general.map.DungeonMap.togglePaul

import shizo.utils.romanToInt
import shizo.utils.rotateAroundNorth
import shizo.utils.rotateToNorth
import shizo.utils.skyblock.dungeon.tiles.Room
import kotlin.math.ceil
import kotlin.math.floor

//actually not sure i need it rn /* I do :D - Joy*/


object  DungeonUtils {
    val termInactiveTitles: Array<String> = arrayOf("Inactive Terminal", "Inactive Device", "Not Activated");

    inline val inDungeons: Boolean
        get() = LocationUtils.isCurrentArea(Island.Dungeon)

    inline val inClear: Boolean
        get() = inDungeons && !inBoss

    inline val inBoss: Boolean
        get() = DungeonListener.inBoss

    inline val floor: Floor?
        get() = DungeonListener.floor

    inline val puzzles: List<Puzzle>
        get() = DungeonListener.puzzles.toList()

    inline val puzzleCount: Int
        get() = DungeonListener.dungeonStats.puzzleCount

    inline val currentRoomName: String
        get() = DungeonListener.currentRoom?.data?.name ?: "Unknown"

    inline val openRoomCount: Int
        get() = DungeonListener.dungeonStats.openedRooms

    inline val currentRoom: Room?
        get() = DungeonListener.currentRoom

    inline val completedRoomCount: Int
        get() = DungeonListener.dungeonStats.completedRooms

    inline val percentCleared: Int
        get() = DungeonListener.dungeonStats.percentCleared


    inline val totalRooms: Int
        get() = if (completedRoomCount == 0 || percentCleared == 0) 0 else floor((completedRoomCount / (percentCleared * 0.01).toFloat()) + 0.4).toInt()

    inline val bloodDone: Boolean
        get() = DungeonListener.dungeonStats.bloodDone

    inline val mimicKilled: Boolean
        get() = DungeonListener.dungeonStats.mimicKilled

    inline val princeKilled: Boolean
        get() = DungeonListener.dungeonStats.princeKilled

    inline val isPaul: Boolean
        get() = DungeonListener.paul


    inline val getBonusScore: Int
        get() {
            var score = cryptCount.coerceAtMost(5)
            if (mimicKilled) score += 2
            if (princeKilled) score += 1
            if ((isPaul && togglePaul == 0) || togglePaul == 2) score += 10
            return score
        }


    inline val score: Int
        get() {
            val completed = completedRoomCount + (if (!bloodDone) 1 else 0) + (if (!inBoss) 1 else 0)
            val total = if (totalRooms != 0) totalRooms else 36

            val exploration = floor?.let {
                floor((secretPercentage / it.secretPercentage) / 100f * 40f).coerceIn(0f, 40f).toInt() +
                        floor(completed.toFloat() / total * 60f).coerceIn(0f, 60f).toInt()
            } ?: 0

            val skillRooms = floor(completed.toFloat() / total * 80f).coerceIn(0f, 80f).toInt()
            val puzzlePenalty = (puzzleCount - puzzles.count { it.status == PuzzleStatus.Completed }) * 10

            return exploration + (20 + skillRooms - puzzlePenalty - (deathCount * 2 - 1).coerceAtLeast(0)).coerceIn(
                20,
                100
            ) + getBonusScore + 100
        }

    inline val neededSecretsAmount: Int
        get() =
            DungeonListener.floor?.let {
                ceil(
                    (totalSecrets * it.secretPercentage) * (40 - getBonusScore + (deathCount * 2 - 1).coerceAtLeast(
                        0
                    )) / 40f
                ).toInt()
            } ?: 0

    inline val knownSecrets: Int
        get() = DungeonListener.dungeonStats.knownSecrets

    inline val secretPercentage: Float
        get() = DungeonListener.dungeonStats.secretsPercent

    inline val totalSecrets: Int
        get() = if (secretCount == 0 || secretPercentage == 0f) 0 else floor(100 / secretPercentage * secretCount + 0.5).toInt()

    inline val secretCount: Int
        get() = DungeonListener.dungeonStats.secretsFound

    inline val cryptCount: Int
        get() = DungeonListener.dungeonStats.crypts


    inline val deathCount: Int
        get() = DungeonListener.dungeonStats.deaths

    inline val passedRooms: Set<Room>
        get() = DungeonListener.passedRooms

    inline val dungeonTime: String
        get() = DungeonListener.dungeonStats.elapsedTime

    inline val dungeonTeammates: List<DungeonPlayer>
        get() = DungeonListener.dungeonTeammates



    inline val dungeonTeammatesNoSelf: List<DungeonPlayer>
        get() = DungeonListener.dungeonTeammatesNoSelf



    inline val leapTeammates: List<DungeonPlayer>
        get() = DungeonListener.leapTeammates

    private val tablistRegex = Regex("^\\[(\\d+)] (?:\\[\\w+] )*(\\w+) .*?\\((\\w+)(?: (\\w+))*\\)$")

    fun getDungeonTeammates(previousTeammates: ArrayList<DungeonPlayer>, tabList: List<String>): ArrayList<DungeonPlayer> {
        for (line in tabList) {
            val (_, name, clazz, clazzLevel) = tablistRegex.find(line)?.destructured ?: continue

            previousTeammates.find { it.name == name }?.let { player -> player.isDead = clazz == "DEAD" }
                ?: run {
                    val player = mc.connection?.getPlayerInfo(name) ?: continue
                    previousTeammates.add(
                        DungeonPlayer(
                            name, DungeonClass.entries.find { it.name == clazz } ?: continue,
                            romanToInt(clazzLevel), player.skin?.body?.id(),
                            entity = mc.level?.getPlayerByUUID(player.profile?.id)
                        )
                    )
                }
        }
        return previousTeammates
    }

    inline val currentDungeonPlayer: DungeonPlayer
        get() = dungeonTeammates.find { it.name == mc.player?.name?.string } ?:
        DungeonPlayer(mc.player?.name?.string ?: "Unknown", DungeonClass.Unknown, 0, null)
    inline val isDead: Boolean
        get() = currentDungeonPlayer.isDead
    /**
     * Checks if the current dungeon floor number matches any of the specified options.
     *
     * @param options The floor number options to compare with the current dungeon floor.
     * @return `true` if the current dungeon floor matches any of the specified options, otherwise `false`.
     */
    fun isFloor(vararg options: Int): Boolean {
        return floor?.floorNumber?.let { it in options } ?: false
    }
    private const val WITHER_ESSENCE_ID = "e0f3e929-869e-3dca-9504-54c666ee6f23"
    private const val REDSTONE_KEY = "fed95410-aba1-39df-9b95-1d4f361eb66e"
    /**
     * Determines whether a given block state and position represent a secret location.
     *
     * This function checks if the specified block state and position correspond to a secret location based on certain criteria.
     * It considers blocks such as chests, trapped chests, and levers as well as player skulls with a specific player profile ID.
     *
     * @param state The block state to be evaluated for secrecy.
     * @param pos The position (BlockPos) of the block in the world.
     * @return `true` if the specified block state and position indicate a secret location, otherwise `false`.
     */
    fun isSecret(state: BlockState?, pos: BlockPos): Boolean {
        return when {
            state?.block.equalsOneOf(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LEVER) -> true
            state?.block is SkullBlock ->
                (mc.level?.getBlockEntity(pos) as? SkullBlockEntity)?.ownerProfile?.partialProfile()?.id
                    ?.toString()?.equalsOneOf(WITHER_ESSENCE_ID, REDSTONE_KEY) ?: false

            else -> false
        }
    }
    /**
     * Gets the current phase of floor 7 boss.
     *
     * @return The current phase of floor 7 boss, or `null` if the player is not in the boss room.
     */
    fun getF7Phase(): M7Phases {
        if ((!DungeonUtils.isFloor(7) || !DungeonUtils.inBoss) && !LocationUtils.isCurrentArea(Island.SinglePlayer)) return M7Phases.Unknown
        // brot his is so fucking stupid???????????? why is this not using chat

        with(mc.player ?: return M7Phases.Unknown) {
            return when {
                y > 210 -> M7Phases.P1
                y > 155 -> M7Phases.P2
                y > 100 -> M7Phases.P3
                y > 45 -> M7Phases.P4
                else -> M7Phases.P5
            }
        }
    }

    fun getP3Section(player: Player? = mc.player): P3Sections {
        if (player == null) return P3Sections.Unknown
        if (getF7Phase() != M7Phases.P3 ) return P3Sections.Unknown

        return when {
            player.x in 89.0..113.0 && player.z in 30.0..122.0 -> P3Sections.S1
            player.x in 19.0..111.0 && player.z in 121.0..145.0 -> P3Sections.S2
            player.x in -6.0..19.0 && player.z in 51.0..143.0 -> P3Sections.S3
            player.x in -2.0..90.0 && player.z in 27.0..51.0 -> P3Sections.S4
            // todo cubey core
            else -> P3Sections.Unknown
        }
    }
    fun getMageCooldownMultiplier(): Double {
        return if (currentDungeonPlayer.clazz != DungeonClass.Mage) 1.0
        else 1 - 0.25 - (floor(currentDungeonPlayer.clazzLvl / 2.0) / 100) * if (dungeonTeammates.count { it.clazz == DungeonClass.Mage } == 1) 2 else 1
    }


    fun Room.getRelativeCoords(pos: BlockPos) = pos.subtract(clayPos.atY(0)).rotateToNorth(rotation)
    fun Room.getRealCoords(pos: BlockPos) = pos.rotateAroundNorth(rotation).offset(clayPos.x, 0, clayPos.z)


}