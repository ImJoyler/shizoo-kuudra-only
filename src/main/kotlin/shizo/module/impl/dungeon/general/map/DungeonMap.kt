package shizo.module.impl.dungeon.general.map

import com.mojang.blaze3d.opengl.GlStateManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.resources.ResourceLocation
import shizo.clickgui.settings.AlwaysActive
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.ColorSetting
import shizo.clickgui.settings.impl.DropdownSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.EnterAreaEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.equalsOneOf
import shizo.utils.itemId
import shizo.utils.renderUtils.renderUtils.hollowFill
import shizo.utils.skyblock.Island.Dungeon
import shizo.utils.skyblock.LocationUtils
import shizo.utils.skyblock.dungeon.DungeonClass
import shizo.utils.skyblock.dungeon.DungeonPlayer
import shizo.utils.skyblock.dungeon.DungeonUtils
import shizo.utils.skyblock.dungeon.tiles.RoomState
import shizo.utils.skyblock.dungeon.tiles.RoomType
import kotlin.collections.iterator

@AlwaysActive
object DungeonMap : Module(
    name = "Dungeon Map",
    description = "Dungeon Map",
    subcategory = "Map"
) {
    // I DEFO NEED TO MAKE THIS MODULE LOOK A BIT MORE ORGANIZED inside the gui
    val togglePaul by SelectorSetting(
        "Paul Settings",
        "Automatic",
        arrayListOf("Automatic", "Force Disable", "Force Enable"),
        desc = "Toggle Paul's settings."
    )
    var mapCheater = true
    var before by BooleanSetting("Shows players before start", false, "It's a bit buggy")
    var backgroundColor by ColorSetting(
        "Background Color",
        Color(0, 0, 0, 0.7f),
        true,
        desc = "The background color of the map."
    )
    var backgroundSize by NumberSetting("Background Size", 5f, 0f, 20f, 1f, desc = "The size of the background border.")

    var textScaling by NumberSetting("Text Scaling", 0.45f, 0.1f, 1f, 0.05f, desc = "Scale of room names.")

    private val infoDropdown by DropdownSetting("Info Settings")
    var mapExtraInfo by BooleanSetting(
        "Show Info",
        true,
        desc = "Displays score, secrets, etc. below the map."
    ).withDependency { infoDropdown }
    var infoScale by NumberSetting("Info Scale", 0.7f, 0.5f, 1.5f, 0.1f, desc = "Scale of the info text.").withDependency { infoDropdown && mapExtraInfo }

    private val playerDropdown by DropdownSetting("Player Settings")
    var playerHeadBackgroundSize by NumberSetting(
        "Player Head BG Size",
        1,
        0,
        10,
        0.5,
        desc = "Size of player head background."
    ).withDependency { playerDropdown }

    private val showPlayerNames = SelectorSetting(
        "Show Names",
        "Holding Leap",
        arrayListOf("Leap", "Always", "Holding Leap"),
        desc = "When to display names under player heads."
    ).withDependency { playerDropdown }
    init {
        this.registerSetting(showPlayerNames)
    }
    var playerHeadColorByClass by BooleanSetting("Colour by class", false, "Colour by class")
    var playerHeadBackgroundColor by ColorSetting(
        "Player Head BG Color",
        Color(70, 70, 70),
        false,
        desc = "Color of player BG."
    ).withDependency { !playerHeadColorByClass }
    var playerNamesScaling by NumberSetting(
        "Player Names Scaling",
        0.75f,
        0.1f,
        2f,
        0.05f,
        desc = "Scale of player names."
    ).withDependency { playerDropdown }
    var playerNameColor by ColorSetting(
        "Player Name Color",
        Color(70, 70, 70),
        false,
        desc = "Color of player names."
    ).withDependency { playerDropdown }

    private val doorDropdown by DropdownSetting("Door Settings")
    var doorThickness by NumberSetting(
        "Door Thickness",
        9,
        1,
        20,
        1,
        desc = "Thickness of doors on map."
    ).withDependency { doorDropdown }
    var unopenedDoorColor by ColorSetting(
        "Unopened Door",
        Color(30, 30, 30),
        false,
        desc = "Color of unopened doors."
    ).withDependency { doorDropdown }
    var bloodDoorColor by ColorSetting(
        "Blood Door",
        Colors.MINECRAFT_RED,
        false,
        desc = "Color of blood room doors."
    ).withDependency { doorDropdown }
    var witherDoorColor by ColorSetting(
        "Wither Door",
        Colors.BLACK,
        false,
        desc = "Color of wither doors."
    ).withDependency { doorDropdown }
    var normalDoorColor by ColorSetting(
        "Normal Door",
        Color(107, 58, 17),
        false,
        desc = "Color of normal doors."
    ).withDependency { doorDropdown }
    var puzzleDoorColor by ColorSetting(
        "Puzzle Door",
        Color(117, 0, 133),
        false,
        desc = "Color of puzzle doors."
    ).withDependency { doorDropdown }
    var championDoorColor by ColorSetting(
        "Champion Door",
        Color(254, 223, 0),
        false,
        desc = "Color of champion doors."
    ).withDependency { doorDropdown }
    var trapDoorColor by ColorSetting(
        "Trap Door",
        Color(216, 127, 51),
        false,
        desc = "Color of trap doors."
    ).withDependency { doorDropdown }
    var entranceDoorColor by ColorSetting(
        "Entrance Door",
        Color(20, 133, 0),
        false,
        desc = "Color of entrance doors."
    ).withDependency { doorDropdown }
    var fairyDoorColor by ColorSetting(
        "Fairy Door",
        Color(244, 19, 139),
        false,
        desc = "Color of fairy room doors."
    ).withDependency { doorDropdown }
    var rareDoorColor by ColorSetting(
        "Rare Door",
        Color(255, 203, 89),
        false,
        desc = "Color of rare doors."
    ).withDependency { doorDropdown }

    // Room Settings
    private val roomDropdown by DropdownSetting("Room Settings")
    var highlightMimic by BooleanSetting("Highlight Mimic", true, desc = "Highlights the room containing the mimic.").withDependency { roomDropdown }
    var mimicColor by ColorSetting("Mimic Color", Color(255, 0, 0), false, desc = "Color of the mimic highlight.").withDependency { roomDropdown && highlightMimic }

    var darkenMultiplier by NumberSetting(
        "Darken Multiplier",
        0.4f,
        0f,
        1f,
        0.05f,
        desc = "Multiplier for darkening rooms."
    ).withDependency { roomDropdown }
    var unopenedRoomColor by ColorSetting(
        "Unopened Room",
        Color(30, 30, 30),
        false,
        desc = "Color of unopened rooms."
    ).withDependency { roomDropdown }
    var unopenedRoomNameColor by ColorSetting(
        "Unopened Room Name ",
        Color(30, 30, 30),
        false,
        desc = "Color of name of unopened rooms."
    ).withDependency { roomDropdown }
    var bloodRoomColor by ColorSetting(
        "Blood Room",
        Color(255, 0, 0),
        false,
        desc = "Color of blood rooms."
    ).withDependency { roomDropdown }
    var normalRoomColor by ColorSetting(
        "Normal Room",
        Color(107, 58, 17),
        false,
        desc = "Color of normal rooms."
    ).withDependency { roomDropdown }
    var puzzleRoomColor by ColorSetting(
        "Puzzle Room",
        Color(117, 0, 133),
        false,
        desc = "Color of puzzle rooms."
    ).withDependency { roomDropdown }
    var championRoomColor by ColorSetting(
        "Champion Room",
        Color(254, 223, 0),
        false,
        desc = "Color of champion rooms."
    ).withDependency { roomDropdown }
    var trapRoomColor by ColorSetting(
        "Trap Room",
        Color(216, 127, 51),
        false,
        desc = "Color of trap rooms."
    ).withDependency { roomDropdown }
    var entranceRoomColor by ColorSetting(
        "Entrance Room",
        Color(20, 133, 0),
        false,
        desc = "Color of entrance rooms."
    ).withDependency { roomDropdown }
    var fairyRoomColor by ColorSetting(
        "Fairy Room",
        Color(244, 19, 139),
        false,
        desc = "Color of fairy rooms."
    ).withDependency { roomDropdown }
    var rareRoomColor by ColorSetting(
        "Rare Room",
        Color(255, 203, 89),
        false,
        desc = "Color of rare rooms."
    ).withDependency { roomDropdown }

     val mapHud by HUD("DungeonMap", "Dungeon Map", false) { example ->
        when {
            !renderMap && !example -> 0 to 0
            example -> renderExampleMap()
            else -> renderDungeonMap()
        }
    }
    private val loadingTeammates = mutableListOf<DungeonPlayer>()
    private val runPlayersNames = mutableMapOf<String, ResourceLocation>()

    private var renderMap = false
    private var cachedScore = 0
    private var cachedSecretCount = 0
    private var cachedNeededSecrets = 0
    private var cachedTotalSecrets = 0
    private var cachedKnownSecrets = 0
    private var cachedMimicKilled = false
    private var cachedPrinceKilled = false
    private var cachedCryptCount = 0
    private var cachedDeathCount = 0

    private val classRegex = Regex("\\(([A-Za-z]+)\\)")
    private val validClasses = setOf("EMPTY", "MAGE", "BERSERK", "ARCHER", "TANK", "HEALER", "DEAD")



    private fun GuiGraphics.renderExampleMap(): Pair<Int, Int> {
        val roomsX = 116
        val roomsZ = 116
        val offset = backgroundSize.toInt()

        fill(0, 0, roomsX + offset * 2, roomsZ + offset * 2, backgroundColor.rgba)
        drawCenteredString(
            mc.font,
            "MAP",
            roomsX / 2 + offset,
            roomsZ / 2 + offset - mc.font.lineHeight,
            java.awt.Color.WHITE.rgb
        )

        return (roomsX + offset * 2) to (roomsZ + offset * 2)
    }

    private fun GuiGraphics.renderDungeonMap(): Pair<Int, Int> {
        val matrices = pose()
        val mapSize = DungMap.calculateMapSize()
        val roomsX = mapSize.x * 16 + (mapSize.x - 1) * 4
        val roomsZ = mapSize.z * 16 + (mapSize.z - 1) * 4
        val offset = backgroundSize.toInt() * 2
        val extraInfoHeight = if (mapExtraInfo) 30 else 0 // random number wohoo let's hope its enough!

        matrices.pushMatrix()

        fill(0, 0, roomsX + offset, roomsZ + offset + extraInfoHeight, backgroundColor.rgba)
        hollowFill(0, 0, roomsX + offset, roomsZ + offset + extraInfoHeight, 1, Colors.gray26)
        matrices.translate(backgroundSize, backgroundSize)

        // epic fix!
        for (door in MapScanner.doors) {
            val shouldDarken = door.rooms.any {
                it.owner.state == RoomState.UNDISCOVERED || it.owner.state == RoomState.UNOPENED
            }
            renderTile(door, shouldDarken)
        }

        for (room in MapScanner.allRooms.values) {
            val isUndiscovered = (room.state == RoomState.UNDISCOVERED || room.state == RoomState.UNOPENED)
                    && room.data.type != RoomType.ENTRANCE

            val hasMimic = highlightMimic && room.hasMimic

            for (tile in room.tiles) renderTile(tile, isUndiscovered, hasMimic)
        }

        val fontHeight = mc.font.lineHeight
        val textFactor = 1 / textScaling

        for ((name, room) in MapScanner.allRooms) {
            if (room.data.type.equalsOneOf(RoomType.FAIRY, RoomType.ENTRANCE, RoomType.BLOOD)) continue

            val isUndiscovered = room.state == RoomState.UNDISCOVERED || room.state == RoomState.UNOPENED

            val splitName = name.split(" ")
            val defaultHeight =
                8 - fontHeight / (2 * textFactor) - ((splitName.size - 1) / 2f * (fontHeight / textFactor)).toInt()
            val placement = room.textPlacement()

            val color = when {

                isUndiscovered -> unopenedRoomNameColor.rgba // can change it to whatever u want, i can also make it dynamic TBH
                room.state == RoomState.GREEN -> Colors.MINECRAFT_GREEN.rgba
                room.state == RoomState.CLEARED -> Colors.WHITE.rgba
                room.state == RoomState.DISCOVERED -> Color(100, 100, 100).rgba
                room.state == RoomState.FAILED -> Colors.MINECRAFT_RED.rgba
                else -> Colors.WHITE.rgba

            }

            for ((index, text) in splitName.withIndex()) {
                matrices.pushMatrix()
                matrices.translate(
                    placement.x + 8f,
                    placement.z + index * (fontHeight / textFactor) + defaultHeight
                )
                matrices.scale(textScaling)
                drawCenteredString(mc.font, text, 0, 0, color)
                matrices.popMatrix()
            }
        }

        if (!DungeonUtils.inBoss) {
            val renderNames = when(showPlayerNames.value) {

                1 -> true
                2 -> mc.player?.mainHandItem?.itemId?.equalsOneOf("INFINITE_SPIRIT_LEAP", "SPIRIT_LEAP") == true
                else -> false

            }

            val playersToRender = DungeonUtils.dungeonTeammates.ifEmpty { loadingTeammates }

            for (player in playersToRender) {
                if (player.isDead) continue

                val (posX, posZ) = player.mapRenderPosition()
                GlStateManager._disableDepthTest()
                matrices.pushMatrix()
                matrices.translate(posX, posZ)

                if (renderNames) {
                    matrices.pushMatrix()
                    matrices.scale(playerNamesScaling)
                    drawCenteredString(mc.font, player.name, 0, 8, playerNameColor.rgba)
                    matrices.popMatrix()
                }

                player.locationSkin?.let { skin ->
                    matrices.rotate(Math.toRadians(180.0 + player.mapRenderYaw()).toFloat())

                    if (playerHeadBackgroundSize != 0) {
                        val size = 5 + playerHeadBackgroundSize
                        var color = if (playerHeadColorByClass) {player.clazz.color.rgba} else {playerHeadBackgroundColor.rgba}
                        fill(-size, -size, size, size, color)
                    }

                    PlayerFaceRenderer.draw(this, skin, -5, -5, 10, false, false, -1)
                }

                matrices.popMatrix()
            }
        }
        if (mapExtraInfo) {
            val secretsStr = "§6Secrets: §b${cachedSecretCount}§f/§e${cachedTotalSecrets}"
            val cryptsStr = colorizeCrypts(cachedCryptCount) + "Crypts: $cachedCryptCount"
            val scoreStr = "§eScore: ${colorizeScore(cachedScore)}§r"
            val deathsStr = "§cDeaths: " + colorizeDeaths(cachedDeathCount)
            val mimicStr = "§cM: ${if (cachedMimicKilled) "§a✔§r" else "§c✘§r"}"
            val princeStr = "§eP: ${if (cachedPrinceKilled) "§a✔§r" else "§c✘§r"}"

            val line1 = "$secretsStr    $cryptsStr"
            val line2 = "$scoreStr   $deathsStr   $mimicStr $princeStr"

            val centerX = (roomsX + offset) / 2f
            val startY = (roomsZ + offset).toFloat()

            matrices.pushMatrix()
            matrices.translate(centerX, startY,)
            matrices.scale(infoScale, infoScale)

            val lineHeight = mc.font.lineHeight + 2

            drawCenteredString(mc.font, line1, 0, 2, Colors.WHITE.rgba)
            drawCenteredString(mc.font, line2, 0, 2 + lineHeight, Colors.WHITE.rgba)

            matrices.popMatrix()
        }
        matrices.popMatrix()
        GlStateManager._enableDepthTest()
        return (roomsX + offset) to (roomsZ + offset + extraInfoHeight)
    }
    private fun colorizeCrypts(count: Int): String {
        return when {
            count < 5 -> "§c"
            else -> "§a"
        }
    }

    private fun colorizeScore(score: Int): String {
        return when {
            score < 270 -> "§c${score}"
            score < 300 -> "§e${score}"
            else -> "§a${score}"
        }
    }



    private fun colorizeDeaths(count: Int): String {
        return when (count) {
            0 -> "§a0"
            1 -> "§e1"
            else -> "§c$count"
        }
    }

    private fun GuiGraphics.renderTile(tile: Tile, darken: Boolean, mimic: Boolean = false) {
        val size = tile.size()
        if (size == Vec2i(0, 0)) return

        val placement = tile.placement()
        val originalColors = tile.color()
        val finalColors = if (mimic) { originalColors.map { c -> lerpColor(c, mimicColor, 0.5f) }.toTypedArray()

        }
            else if (darken) {
            val multiplier = darkenMultiplier
            originalColors.map { c ->
                Color(
                    (c.red * multiplier).toInt(),
                    (c.green * multiplier).toInt(),
                    (c.blue * multiplier).toInt(),
                    c.alphaFloat
                )
            }.toTypedArray()
        } else {
            originalColors
        }

        pose().pushMatrix()
        pose().translate(placement.x.toFloat(), placement.z.toFloat())

        when (finalColors.size) {
            1 -> fill(0, 0, size.x, size.z, finalColors[0].rgba)
            2 -> {
                fill(0, 0, 16, 8, finalColors[0].rgba)
                fill(0, 8, 16, 16, finalColors[1].rgba)
            }

            3 -> {
                fill(0, 0, size.x, 5, finalColors[0].rgba)
                fill(0, 0, 5, 10, finalColors[0].rgba)
                fill(10, 5, 16, 16, finalColors[1].rgba)
                fill(0, 10, 16, 16, finalColors[1].rgba)
                fill(5, 5, 11, 11, finalColors[2].rgba)
            }
        }

        pose().popMatrix()
    }

    private fun lerpColor(c1: Color, c2: Color, factor: Float): Color {
        val r = (c1.red + (c2.red - c1.red) * factor).toInt()
        val g = (c1.green + (c2.green - c1.green) * factor).toInt()
        val b = (c1.blue + (c2.blue - c1.blue) * factor).toInt()
        val a = c1.alphaFloat + (c2.alphaFloat - c1.alphaFloat) * factor
        return Color(r, g, b, a)
    }


    init {
        on<WorldEvent.Load> {
            SpecialColumn.unload()
            MapScanner.unload()
            DungMap.unload()
            loadingTeammates.clear()
            runPlayersNames.clear()
        }

        on<TickEvent.End> {
            MapScanner.scan(world)
            if (DungeonUtils.inDungeons) {
                cachedScore = DungeonUtils.score
                cachedSecretCount = DungeonUtils.secretCount
                cachedNeededSecrets = DungeonUtils.neededSecretsAmount
                cachedTotalSecrets = DungeonUtils.totalSecrets
                cachedKnownSecrets = DungeonUtils.knownSecrets
                cachedMimicKilled = DungeonUtils.mimicKilled
                cachedPrinceKilled = DungeonUtils.princeKilled
                cachedCryptCount = DungeonUtils.cryptCount.coerceAtMost(5)
                cachedDeathCount = DungeonUtils.deathCount
            }
            //if (!renderMap && LocationUtils.currentArea == Dungeon ) { renderMap = true}

            if (before && LocationUtils.currentArea == Dungeon && DungeonUtils.dungeonTeammates.isEmpty()) {
                val online = mc.connection?.onlinePlayers
                if (online != null && online.size != loadingTeammates.size) {
                    loadingTeammates.clear()
                    online.forEach { info ->
                        loadingTeammates.add(
                            DungeonPlayer(
                                name = info.profile.name,
                                clazz = DungeonClass.entries.first(),
                                clazzLvl = 0,
                                locationSkin = info.skin?.body?.id(),
                                entity = mc.level?.getPlayerByUUID(info.profile.id)
                            )
                        )
                    }
                }

                loadingTeammates.forEach { player ->
                    if (player.entity == null) {
                        player.entity = mc.level?.players()?.find { it.name.string == player.name }
                    }
                }
            }  else {

                if (loadingTeammates.isNotEmpty()) loadingTeammates.clear()

            }
            // fixing this mess of a code above theoretically
//            if (before && LocationUtils.currentArea == Dungeon && DungeonUtils.dungeonTeammates.isEmpty()) {
//                val connection = mc.connection
//                if (connection != null) {
//
//                    connection.onlinePlayers.forEach { info ->
//                        val displayName = info.tabListDisplayName?.string?.noControlCodes ?: ""
//                        val match = classRegex.find(displayName)
//
//                        if (match != null && match.groupValues[1].uppercase() in validClasses) {
//                            val name = info.profile.name
//                            val skin = info.skin.body.id()
//                            val className = match.groupValues[1].uppercase()
//
//                            runPlayersNames[name] = skin
//
//                            val parsedClass = if (className == "EMPTY" || className == "DEAD") {
//                                DungeonClass.entries.first()
//                            } else {
//                                DungeonClass.entries.find { it.name.equals(className, ignoreCase = true) } ?: DungeonClass.entries.first()
//                            }
//
//                            val existingPlayer = loadingTeammates.find { it.name == name }
//                            if (existingPlayer == null) {
//                                loadingTeammates.add(
//                                    DungeonPlayer(
//                                        name = name,
//                                        clazz = parsedClass,
//                                        clazzLvl = 0,
//                                        locationSkin = skin,
//                                        entity = null
//                                    )
//                                )
//                            } else {
//                                existingPlayer.clazz = parsedClass
//                            }
//                        }
//                    }
//
//                    loadingTeammates.removeIf { it.name !in runPlayersNames }
//
//                    loadingTeammates.forEach { player ->
//                        if (player.entity == null) {
//                            player.entity = mc.level?.players()?.find { it.name.string == player.name }
//                        }
//                    }
//                }
//            }  else {
//                if (loadingTeammates.isNotEmpty()) loadingTeammates.clear()
//                if (runPlayersNames.isNotEmpty()) runPlayersNames.clear()
//            }
        }

        on<EnterAreaEvent> {
            renderMap = (LocationUtils.currentArea == Dungeon)
        }

        ClientChunkEvents.CHUNK_LOAD.register { _, _ ->
            DungMap.onChunkLoad()
        }

        onReceive<ClientboundMapItemDataPacket> {
            mc.execute { DungMap.rescanMapItem(this) }
        }
    }
}