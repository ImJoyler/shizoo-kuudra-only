package shizo.module.impl.kuudra.phaseone

import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.ActionSetting
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.ColorSetting
import shizo.clickgui.settings.impl.DropdownSetting
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.RenderEvent
import shizo.events.TickEvent
import shizo.events.WorldEvent
import shizo.events.core.on
import shizo.events.core.onReceive
import shizo.module.impl.Module
import shizo.module.impl.kuudra.phaseone.prio.Priority
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.RotationUtils
import shizo.utils.devMessage
import shizo.utils.handlers.schedule
import shizo.utils.isPlayerInBox
import shizo.utils.modMessage
import shizo.utils.noControlCodes
import shizo.utils.rightClick
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.renderUtils.renderUtils.drawStyledBox
import shizo.utils.renderUtils.renderUtils.drawText
import shizo.utils.renderUtils.renderUtils.getStringWidth
import shizo.utils.renderUtils.renderUtils.textDim
import shizo.utils.sendUseItemClicksSeq
import shizo.utils.toFixed
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

object PearlWaypoints : Module(
    name = "Pearl Waypoints",
    description = "Calculates and renders optimal pearl throws for Kuudra supplies.",
    subcategory = "Phase 1"
) {
    // todo pearl land spot calculaction

    private val talismanTier = SelectorSetting(
        "Talisman Tier",
        "No Tali",
        arrayListOf("No Tali", "T1 Tali", "T2 Tali", "T3 Tali"),
        "Your Kuudra Follower Artifact tier."
    )

    private val displayInTicks by BooleanSetting(
        "DisplayInTicks",
        false,
        desc = "Display the timers in ticks instead of seconds."
    )
    private val symbolDisplay by BooleanSetting("Display Symbol", true, desc = "Display s or t after the timers.")
    private val depth by BooleanSetting("Waypoints Depth", true, desc = "Depth test for waypoints.")
    private val autoThrow by BooleanSetting("Auto Throw", false, desc = "Michi thing.")
    private val snapThrow by BooleanSetting("Perfect tgriw", true, desc = "Forces perfect yaw/pitch via packets.").withDependency { autoThrow }
    private val throwDelay by NumberSetting("Throw Delay", 4.0, 0.0, 100.0, 1.0, "Ticks")

    private val DoublePearls by DropdownSetting("Double Pearl Settings")
    private val doublePearl by BooleanSetting("Double Pearl", true, "Based on Priority module").withDependency { DoublePearls }
    private val landingDelay by NumberSetting("Landing Delay", 10.0, 0.0, 100.0, 1.0, "Ticks").withDependency { DoublePearls && doublePearl }
    var doubleWpColour by ColorSetting(
        "Double Pearl Colour",
        Color(30, 144, 255),
        false,
        desc = "Color of the second pearl waypoint."
    ).withDependency { DoublePearls && doublePearl }
    private val dpWpScale by NumberSetting(
        "DP Waypoint Scale",
        0.085,
        0.01,
        2.0,
        0.05,
        desc = "Size of the DP waypoint box."
    ).withDependency { DoublePearls && doublePearl }

    private val dpShowText by BooleanSetting("DP Show Text", true, "Render the DP name and timer.").withDependency { DoublePearls && doublePearl }

    private val dpTextScale by NumberSetting("DP Text Scale", 1.0, 0.05, 10.0, 0.05, desc = "Size of the DP text.").withDependency { DoublePearls && doublePearl && dpShowText }
    private val highArcSlashToSquare by BooleanSetting(
        "Toom's Square",
        true,
        "Forces a higher trajectory when throwing to Square from Slash."
    ).withDependency { DoublePearls && doublePearl }
    private val highArcSlashToXCannon by BooleanSetting(
        "Toom's XC",
        true,
        "Forces a higher trajectory when throwing to XCannon from Slash."
    ).withDependency { DoublePearls && doublePearl }

    private val aimDpKey by KeybindSetting(
        "Aim DP Keybind",
        GLFW.GLFW_KEY_UNKNOWN,
        "Press to automatically smooth-rotate to the Double Pearl."
    ).withDependency { DoublePearls && doublePearl }.onPress {
        if (!enabled || !KuudraUtils.inKuudra || KuudraUtils.phase != 1) return@onPress
        aimAtDP()
    }
    private val min by NumberSetting("Min rotation", 250, 100, 1000, 0.05, desc = "Size of the waypoint text.").withDependency { DoublePearls && doublePearl }
    private val max by NumberSetting("Max Rotation", 500, 100, 1000, 0.05, desc = "Size of the waypoint text.").withDependency { DoublePearls && doublePearl }


    private val Waypoints by DropdownSetting("Waypoint settings")
    private val render3D by BooleanSetting("Render 3D Box", true, desc = "Norma people.").withDependency { Waypoints }
    private val renderOnPile by BooleanSetting("Render on Pile", false, desc = "idk it mnight make pearls a bit better cause waypoint size error...").withDependency { Waypoints && render3D }

    private val pileBoxScale by NumberSetting("Pile Box Scale", 0.5, 0.1, 5.0, 0.1, desc = "Size of the box rendered on the physical pile.").withDependency { Waypoints && render3D && renderOnPile }
    private val floatingBoxScale by NumberSetting("Floating Box Scale",
        0.085,
        0.01,
        2.0,
        0.05,
        desc = "Size of the floating 3D box.").withDependency { Waypoints && render3D && !renderOnPile }

    private val render2D by BooleanSetting("Render 2D Reticle", false, desc = "BEREFTS.").withDependency { Waypoints }
    private val D2Scale by NumberSetting("Size of 2d", 1.0, 0.05, 10.0, 0.05, desc = "Size of the 2d render.").withDependency { Waypoints && render2D }

    private val textScale by NumberSetting("Text Scale", 1.0, 0.05, 10.0, 0.05, desc = "Size of the waypoint text.").withDependency { Waypoints }
    private val textHeightOffset by NumberSetting(
        "Text Height Offset",
        0.0,
        -2.0,
        2.0,
        0.05,
        desc = "Shifts the text up or down."
    ).withDependency { Waypoints }

    private val wpDistance by NumberSetting(
        "Projection Distance",
        5.0,
        1.0,
        100.0,
        0.5,
        desc = "Distance of the projection  from your camera."
    ).withDependency { Waypoints }

    var wpColour by ColorSetting(
        "Waypoint Colour",
        Color(30, 30, 30),
        false,
        desc = "Color of waypoints."
    ).withDependency { Waypoints }

    private val pingOffset by NumberSetting("Average Ping", 50.0, 0.0, 500.0, 10.0, desc = "Ping compensation in ms.").withDependency { Waypoints }
    private val Dev by DropdownSetting("Developer Options")
    private val devMode by BooleanSetting(
        "Dev Mode",
        false,
        desc = "Renders area hitboxes and target dots for testing."
    ).withDependency { Dev }
    private val testPicking = ActionSetting("Test Picking", "Simulate picking for testing.") {
        picking = true
        pickTime = 0
        renderAfterPickedTicks = 100
    }.withDependency { devMode && Dev}
    // test feature
    val rollHud by HUD("Roll Prediction", "Displays predicted supply drop roll.", true) { example ->
        if (example) {
            val text = "Roll: §dINSTA"
            textDim(text, 0, 0, Colors.WHITE)
            return@HUD getStringWidth(text) to 10
        }

        if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1) return@HUD 0 to 0
        if (!picking && renderAfterPickedTicks <= 0) return@HUD 0 to 0

        val wp = currentWaypointPositions.firstOrNull() ?: return@HUD 0 to 0

        val tierIndex = (KuudraUtils.kuudraTier - 1).coerceIn(0, 4)
        val maxTime = pickTimings.getOrNull(talismanTier.value)?.getOrNull(tierIndex) ?: 120
        val timeTillThrow = maxTime - (pingOffset / 50.0) - wp.time - pickTime
        val waitTicks = timeTillThrow.roundToInt()

        val rollText = when {
            waitTicks < 0 -> "§4LATE"
            waitTicks in 0..2 -> "§dINSTA"
            waitTicks in 3..5 -> "§aFAST"
            waitTicks in 6..7 -> "§eMID"
            waitTicks in 8..10 -> "§cSLOW"
            else -> "§7WAIT..."
        }

        val text = "Roll: $rollText"
        textDim(text, 0, 0, Colors.WHITE)
        return@HUD getStringWidth(text) to 10
    }
    // cba finishing this rn
    init {
        this.registerSetting(talismanTier)
        this.registerSetting(testPicking)
    }

    private val pickTimings = arrayOf(
        intArrayOf(60, 80, 100, 120, 120),
        intArrayOf(55, 75,  90, 110, 110),
        intArrayOf(50, 65,  80, 100, 100),
        intArrayOf(45, 60,  70,  85,  85)
    )

    data class Area(val minX: Double, val minZ: Double, val maxX: Double, val maxZ: Double)
    data class LookDirAndTime(val dir: Vec3, val time: Double)
    data class WaypointData(val pos: Vec3, val name: String, val time: Double, val targetPos: Vec3)

    private val areas = mapOf(
        "x" to Area(-171.0, -183.0, -124.0, -133.0),
        "xc" to Area(-171.0, -133.0, -124.0, -103.0),
        "square" to Area(-171.0, -103.0, -124.0, -51.0),
        "slash" to Area(-124.0, -92.0, -99.0, -42.0),
        "equals" to Area(-84.0, -105.0, -33.0, -48.0),
        "tri" to Area(-78.0, -126.0, -42.0, -105.0),
        "shop" to Area(-96.0, -173.0, -42.0, -126.0)
    )

    private val pileWaypoints = mapOf(
        "x" to Pair(Vec3(-106.0, 79.0, -113.0), "X"),
        "xc" to Pair(Vec3(-110.0, 79.0, -106.0), "XC"),
        "slash" to Pair(Vec3(-106.0, 79.0, -99.0), "/"),
        "equals" to Pair(Vec3(-98.0, 79.0, -99.0), "="),
        "tri" to Pair(Vec3(-94.0, 79.0, -106.0), "Tri"),
        "shop" to Pair(Vec3(-98.0, 79.0, -113.0), "Shop")
    )

    private val doublePearlTargets = mapOf(
        "XCANNON" to Vec3(-129.5, 79.0, -113.0),
        "SQUARE" to Vec3(-140.0, 77.0, -86.5)
        //shop
    )

    private var missingPre: String? = null
    private var currentArea: String? = null
    private var picking = false
    private var pickTime = 0
    private var renderAfterPickedTicks = 0
    private var currentWaypointPositions = mutableListOf<WaypointData>()

    private var dpData: WaypointData? = null
    private var dpTimeTillThrow = 0.0
    private val preAimDpData = mutableListOf<WaypointData>()

    private var thrownPrimary = false
    private var thrownDouble = false

    private val acceleration = Vec3(0.0, -0.03, 0.0)
    private val drag = 0.01
    private val speed = 1.5
    private val squaredSpeed = speed * speed
    private val PHI = (1.0 + Math.sqrt(5.0)) / 2.0
    private val invPhi = 1.0 / PHI

    private fun aimAtDP() {
        val p = mc.player ?: return
        val eyePos = p.position().add(0.0, 1.62, 0.0)
        val targetWp = dpData ?: preAimDpData.firstOrNull()

        if (targetWp != null) {
            val targetDir = targetWp.pos.subtract(eyePos).normalize()
            val yaw = Math.toDegrees(atan2(-targetDir.x, targetDir.z)).toFloat()
            val pitch = Math.toDegrees(asin(-targetDir.y)).toFloat()
            val yawJitter = 0 + Random.Default.nextDouble(0.2, 2.5)
            val pitchJitter = 0 + Random.Default.nextDouble(0.1, 2.4)
            RotationUtils.smoothRotate(yaw + yawJitter.toFloat(), pitch + pitchJitter.toFloat(), min.toLong(), max.toLong(), true)
        } else {
            modMessage("§cNo Double Pearl target available to aim at.")
        }
    }

    init {
        on<WorldEvent.Load> {
            missingPre = null
            resetState()
        }

        onReceive<ClientboundSystemChatPacket> {
            if (!KuudraUtils.inKuudra || KuudraUtils.phase != 1) return@onReceive
            val msg = this.content.string.noControlCodes.lowercase()

            when {
                msg.contains("no x cannon") || msg.contains("no xc") -> missingPre = "xc"
                msg.contains("no x") -> missingPre = "x"
                msg.contains("no slash") -> missingPre = "slash"
                msg.contains("no equals") -> missingPre = "equals"
                msg.contains("no triangle") || msg.contains("no tri") -> missingPre = "tri"
                msg.contains("no shop") -> missingPre = "shop"
            }
        }

        val onTitleOrActionbar = { msg: String ->
            if (KuudraUtils.inKuudra && KuudraUtils.phase == 1 && msg == "[||||||||||||||||||||] 0%") {
                if (!picking) {
                    picking = true
                    pickTime = 0
                    renderAfterPickedTicks = 100
                    thrownPrimary = false
                    thrownDouble = false
                }
            }
        }

        onReceive<ClientboundSetActionBarTextPacket> { onTitleOrActionbar(this.text.string.noControlCodes) }
        onReceive<ClientboundSetTitleTextPacket> { onTitleOrActionbar(this.text.string.noControlCodes) }
        onReceive<ClientboundSetSubtitleTextPacket> { onTitleOrActionbar(this.text.string.noControlCodes) }
        on<TickEvent.Server> {
            if ((!KuudraUtils.inKuudra || KuudraUtils.phase != 1) && !devMode) {
                resetState()
                return@on
            }
            val p = mc.player ?: return@on
            val tierIndex = (KuudraUtils.kuudraTier - 1).coerceIn(0, 4)
            val maxTime = pickTimings.getOrNull(talismanTier.value)?.getOrNull(tierIndex) ?: 120

            if (picking) {
                pickTime++
                if (pickTime >= maxTime) {
                    picking = false
                }
            } else if (renderAfterPickedTicks > 0) {
                renderAfterPickedTicks--
            }

            currentArea = null
            for ((key, bounds) in areas) {
                if (p.x >= bounds.minX && p.x <= bounds.maxX && p.z >= bounds.minZ && p.z <= bounds.maxZ) {
                    currentArea = key
                    break
                }
            }

            currentWaypointPositions.clear()
            dpData = null
            preAimDpData.clear()

            if (currentArea == null && !devMode) return@on

            // ty rico
            val currentEyeHeight = if (p.isCrouching) 1.54 else 1.62
            val cameraPos = p.position().add(0.0, currentEyeHeight, 0.0)
            val pearlSpawnPos = p.position().add(0.0, 1.5, 0.0)

            val activeArea = currentArea ?: "square"
            val isActionActive = picking || renderAfterPickedTicks > 0

            if (activeArea == "square") {
                if (missingPre == null) {
                    pileWaypoints.values.forEach { (target, name) ->
                        val calc = findLookDirAndTime(pearlSpawnPos, target , false)
                        val wpPos = cameraPos.add(calc.dir.scale(wpDistance))
                        currentWaypointPositions.add(WaypointData(wpPos, name, calc.time, target))
                    }
                } else {
                    val wp = getPileWaypoint(missingPre!!)
                    if (wp != null) {
                        val calc = findLookDirAndTime(pearlSpawnPos, wp.first, false)
                        val wpPos = cameraPos.add(calc.dir.scale(wpDistance))
                        currentWaypointPositions.add(WaypointData(wpPos, wp.second, calc.time, wp.first))
                    }
                }
            } else {
                val wp = getPileWaypoint(activeArea)
                if (wp != null) {
                    val calc = findLookDirAndTime(pearlSpawnPos, wp.first, false)
                    val wpPos = cameraPos.add(calc.dir.scale(wpDistance))
                    currentWaypointPositions.add(WaypointData(wpPos, wp.second, calc.time, wpPos))
                }
            }

            if (doublePearl) {
                var dpFound = false
                if (isActionActive) {
                    val targetTask = Priority.lastInstruction?.task
                    if (targetTask != null) {
                        val targetBlock = getDoublePearlBlock(targetTask)
                        if (targetBlock != null) {
                            val useHighArc = (highArcSlashToSquare && activeArea == "slash" && targetTask.contains("SQUARE")) || (highArcSlashToXCannon && activeArea == "slash" && targetTask.contains("XCANNON"))

                            val calc2 = findLookDirAndTime(pearlSpawnPos, targetBlock, useHighArc)
                            val dpName = "DP: ${targetTask.replace("GO ", "")}"
                            val wpPos2 = cameraPos.add(calc2.dir.scale(wpDistance))

                            val targetLandingTick2 = (maxTime - (pingOffset / 50.0)) + landingDelay
                            val throwTick2 = targetLandingTick2 - calc2.time

                            dpTimeTillThrow = throwTick2 - pickTime
                            dpData = WaypointData(wpPos2, dpName, calc2.time, wpPos2)
                            dpFound = true
                        }
                    }
                }

                if (!dpFound) {
                    val preAimTargets = mutableListOf<String>()
                    if (activeArea != "square") preAimTargets.add("SQUARE")
                    if (activeArea != "xc") preAimTargets.add("XCANNON")

                    if (missingPre == "xc") preAimTargets.remove("XCANNON")
                    if (missingPre == "square") preAimTargets.remove("SQUARE")

                    for (t in preAimTargets) {
                        val targetBlock = doublePearlTargets[t] ?: continue
                        val useHighArc = (highArcSlashToSquare && activeArea == "slash" && t == "SQUARE") ||
                                (highArcSlashToXCannon && activeArea == "slash" && t == "XCANNON")
                        val calc = findLookDirAndTime(pearlSpawnPos, targetBlock, useHighArc)
                        val wpPos = cameraPos.add(calc.dir.scale(wpDistance))
                        preAimDpData.add(WaypointData(wpPos, "DP: $t", calc.time, wpPos))
                    }
                }
            }

            if (autoThrow && isActionActive) {
                if (mc.screen != null) return@on
                if (isPlayerInBox(-131, 75, -93, -145, 80, -78)) return@on

                val lookVec = p.lookAngle
                val heldItem = p.mainHandItem

                if (heldItem.item == Items.ENDER_PEARL) {
                    if (!thrownPrimary) {
                        currentWaypointPositions.forEach { wp ->
                            val timeTillThrow = maxTime - (pingOffset / 50.0) - wp.time - pickTime
                            val autoThrowTime = timeTillThrow + throwDelay
                            if (autoThrowTime <= 0 && autoThrowTime > -1.5) {
                                val mapKey = when (wp.name.lowercase()) {
                                    "/" -> "slash"
                                    else -> wp.name.lowercase()
                                }
                                val targetPilePos = pileWaypoints[mapKey]?.first ?: return@forEach
                                val optimalDir = findLookDirAndTime(pearlSpawnPos, targetPilePos, false).dir
                                if (snapThrow ) {
                                    val throwYaw = Math.toDegrees(atan2(-optimalDir.x, optimalDir.z)).toFloat()
                                    val throwPitch = Math.toDegrees(asin(-optimalDir.y)).toFloat()
// schedule works on tickevent.start so TCP is preserved
                                    schedule(0) {
                                        if (mc.screen == null && mc.player?.mainHandItem?.item == Items.ENDER_PEARL) {
                                            sendUseItemClicksSeq(mutableListOf(throwYaw, throwPitch))
                                        }
                                        else { modMessage("no pearls OR opened gui no ban pls tyu xo") }

                                    }
                                    thrownPrimary = true
                                    devMessage("§aSnap-thrown perfect pearl at ${wp.name}!")
                                } else if (lookVec.dot(optimalDir) > 0.996) {
                                    rightClick()
                                    thrownPrimary = true
                                    devMessage("§aAuto-thrown pearl at ${wp.name}!")
                                }
                            }
                        }
                    }

                    if (!thrownDouble && dpData != null) {
                        val autoDpThrowTime = dpTimeTillThrow + throwDelay
                        if (autoDpThrowTime <= 0 && autoDpThrowTime > -1.5) {
                            val targetTask = Priority.lastInstruction?.task ?: ""
                            val targetBlock = getDoublePearlBlock(targetTask)
                            if (targetBlock != null) {
                                val useHighArc = (highArcSlashToSquare && activeArea == "slash" && targetTask.contains("SQUARE")) || (highArcSlashToXCannon && activeArea == "slash" && targetTask.contains("XCANNON"))
                                val optimalDir = findLookDirAndTime(pearlSpawnPos, targetBlock, useHighArc).dir

                                if (snapThrow) {
                                    val throwYaw = Math.toDegrees(atan2(-optimalDir.x, optimalDir.z)).toFloat()
                                    val throwPitch = Math.toDegrees(asin(-optimalDir.y)).toFloat()
// same thing as before :D
                                    schedule(0) {
                                        sendUseItemClicksSeq(mutableListOf(throwYaw, throwPitch))
                                    }
                                    thrownDouble = true
                                    devMessage("§bSnap-thrown Double Pearl at ${dpData!!.name}!")
                                } else if (lookVec.dot(optimalDir) > 0.996) {
                                    rightClick()
                                    thrownDouble = true
                                    devMessage("§bAuto-thrown Double Pearl at ${dpData!!.name}!")
                                }
                            }
                        }
                    }
                }
            }
        }

        on<RenderEvent.Extract> {
            val validRender = devMode || (KuudraUtils.inKuudra && KuudraUtils.phase == 1)
            if (!validRender) return@on

            if (devMode) {
                val p = mc.player
                if (p != null) {
                    for ((_, bounds) in areas) {
                        val areaBox = AABB(bounds.minX, p.y, bounds.minZ, bounds.maxX, p.y + 1.0, bounds.maxZ)
                        this.drawStyledBox(areaBox, wpColour, 0, false)
                    }
                    for ((_, wp) in pileWaypoints) {
                        val s = 0.05
                        val targetBox = AABB(
                            wp.first.x - s, wp.first.y - s, wp.first.z - s,
                            wp.first.x + s, wp.first.y + s, wp.first.z + s
                        )
                        this.drawStyledBox(targetBox, wpColour, 1, false)
                    }
                }
            }

            if (currentWaypointPositions.isEmpty() && dpData == null && preAimDpData.isEmpty()) return@on

            val tierIndex = (KuudraUtils.kuudraTier - 1).coerceIn(0, 4)
            val maxTime = pickTimings.getOrNull(talismanTier.value)?.getOrNull(tierIndex) ?: 120
            val isActionActive = picking || renderAfterPickedTicks > 0

            val sNormal = floatingBoxScale / 2.0
            val renderOffsetNormal = floatingBoxScale / 2.0
            val yPadding = wpDistance / 25.0
            val heightOffset = textHeightOffset
            currentWaypointPositions.forEach { wp ->
                val basePos = wp.pos.add(0.0, -renderOffsetNormal, 0.0)

                if (render3D) {
                    val boxPos = if (renderOnPile) wp.targetPos else basePos
                    val size = if (renderOnPile) pileBoxScale else (floatingBoxScale / 2.0)

                    val box = AABB(
                        boxPos.x - size, boxPos.y - size, boxPos.z - size,
                        boxPos.x + size, boxPos.y + size, boxPos.z + size
                    )
                    this.drawStyledBox(box, wpColour, 2, depth)
                }

                if (render2D) {
                    val dotSize = D2Scale
                    val dotBox = AABB(
                        wp.pos.x - dotSize, wp.pos.y - dotSize, wp.pos.z - dotSize,
                        wp.pos.x + dotSize, wp.pos.y + dotSize, wp.pos.z + dotSize
                    )
                    this.drawStyledBox(dotBox, wpColour, 0, depth)
                }
                val timeTillThrow = max(maxTime - (pingOffset / 50.0) - wp.time - pickTime, 0.0)
                val text = if (!isActionActive) "§ePRE-AIM" else if (timeTillThrow > 0) formatTimer(timeTillThrow, maxTime.toDouble()) else "§aTHROW"

                this.drawText("§f${wp.name}",
                    Vec3(wp.pos.x, basePos.y + sNormal + yPadding + heightOffset, wp.pos.z), textScale.toFloat(), depth)
                this.drawText(text,
                    Vec3(wp.pos.x, basePos.y - sNormal - yPadding + heightOffset, wp.pos.z), textScale.toFloat(), depth)
            }

            preAimDpData.forEach { dp ->
                val sDP = dpWpScale / 2.0
                val renderOffsetDP = dpWpScale / 2.0
                val basePos = dp.pos.add(0.0, -renderOffsetDP, 0.0)

                if (render3D) {
                    val box = AABB(
                        basePos.x - sDP, basePos.y - sDP, basePos.z - sDP,
                        basePos.x + sDP, basePos.y + sDP, basePos.z + sDP
                    )
                    this.drawStyledBox(box, doubleWpColour, 2, depth)
                }

                if (render2D) {
                    val dotSize = D2Scale
                    val dotBox = AABB(
                        dp.pos.x - dotSize, dp.pos.y - dotSize, dp.pos.z - dotSize,
                        dp.pos.x + dotSize, dp.pos.y + dotSize, dp.pos.z + dotSize
                    )
                    this.drawStyledBox(dotBox, doubleWpColour, 0, depth)
                }
            }

            dpData?.let { dp ->
                val sDP = dpWpScale / 2.0
                val renderOffsetDP = dpWpScale / 2.0
                val basePos = dp.pos.add(0.0, -renderOffsetDP, 0.0)

                if (render3D) {
                    val box = AABB(
                        basePos.x - sDP, basePos.y - sDP, basePos.z - sDP,
                        basePos.x + sDP, basePos.y + sDP, basePos.z + sDP
                    )
                    this.drawStyledBox(box, doubleWpColour, 2, depth)
                }
                if (render2D) {
                    val dotSize = D2Scale
                    val dotBox = AABB(
                        dp.pos.x - dotSize, dp.pos.y - dotSize, dp.pos.z - dotSize,
                        dp.pos.x + dotSize, dp.pos.y + dotSize, dp.pos.z + dotSize
                    )
                    this.drawStyledBox(dotBox, doubleWpColour, 0, depth)
                }

                if (dpShowText) {
                    val timeTillThrow2 = max(dpTimeTillThrow, 0.0)
                    val text = if (timeTillThrow2 > 0) formatTimer(timeTillThrow2, maxTime.toDouble()) else "§bTHROW"

                    this.drawText("§f${dp.name}",
                        Vec3(dp.pos.x, basePos.y + sDP + yPadding + heightOffset, dp.pos.z), dpTextScale.toFloat(), depth)
                    this.drawText(text,
                        Vec3(dp.pos.x, basePos.y - sDP - yPadding + heightOffset, dp.pos.z), dpTextScale.toFloat(), depth)
                }
            }
        }
    }

    private fun getDoublePearlBlock(task: String): Vec3? {
        if (task.contains("X CANNON") || task.contains("XCANNON")) return doublePearlTargets["XCANNON"]
        if (task.contains("SQUARE")) return doublePearlTargets["SQUARE"]
        return null
    }

    private fun formatTimer(timeTicks: Double, maxTicks: Double): String {
        val color = when {
            timeTicks >= maxTicks * 0.66 -> "§a"
            timeTicks >= maxTicks * 0.33 -> "§6"
            else -> "§c"
        }
        val timeDisplay = if (displayInTicks) {
            "${timeTicks.roundToInt()}${if (symbolDisplay) "t" else ""}"
        } else {
            "${(timeTicks / 20.0).toFloat().toFixed()}${if (symbolDisplay) "s" else ""}"
        }
        return "$color$timeDisplay"
    }

    private fun resetState() {
        picking = false
        pickTime = 0
        renderAfterPickedTicks = 0
        currentArea = null
        currentWaypointPositions.clear()
        dpData = null
        preAimDpData.clear()
        thrownPrimary = false
        thrownDouble = false
    }

    private fun getPileWaypoint(area: String): Pair<Vec3, String>? {
        return when (area) {
            "equals" -> pileWaypoints["slash"]
            "slash" -> pileWaypoints["equals"]
            else -> pileWaypoints[area]
        }
    }

    private fun velocityGivenTime(t: Double, displacement: Vec3): Vec3 {
        val displacementDrag = displacement.scale(drag)
        val subAccel = displacementDrag.subtract(acceleration.scale(t))
        val expm1 = -Math.expm1(-drag * t)
        val divExpm1 = subAccel.scale(1.0 / expm1)
        val accelDivDrag = acceleration.scale(1.0 / drag)
        return divExpm1.add(accelDivDrag)
    }

    private fun minimizeScalarBounded(f: (Double) -> Double, aIn: Double, bIn: Double, tol: Double = 1e-6, maxIter: Int = 50): Double {
        var a = aIn
        var b = bIn
        var c = b - (b - a) * invPhi
        var d = a + (b - a) * invPhi
        var fc = f(c)
        var fd = f(d)
        for (i in 0 until maxIter) {
            if (Math.abs(b - a) < tol) break
            if (fc < fd) {
                b = d
                d = c
                fd = fc
                c = b - (b - a) * invPhi
                fc = f(c)
            } else {
                a = c
                c = d
                fc = fd
                d = a + (b - a) * invPhi
                fd = f(d)
            }
        }
        return 0.5 * (a + b)
    }

    private fun findLookDirAndTime(pos: Vec3, target: Vec3, highArc: Boolean = false): LookDirAndTime {
        val dpos = target.subtract(pos)
        val h = dpos.y
        val d2d = Math.sqrt(dpos.x * dpos.x + dpos.z * dpos.z)

        var lowPitch = if (highArc) 45.0 else -89.0
        var highPitch = if (highArc) 89.0 else 45.0
        var bestPitch = 0.0
        var bestTime = 0.0
        var foundValidTrajectory = false

        for (i in 0 until 50) {
            val midPitch = (lowPitch + highPitch) / 2.0
            val theta = Math.toRadians(midPitch)
            val v_x = speed * Math.cos(theta)
            val v_y = speed * Math.sin(theta)

            val dragExp = 1.0 - Math.exp(-drag * (d2d / v_x))
            val subterm = (dragExp * v_y) / drag
            val accelTerm = (acceleration.y * (d2d / v_x)) / drag
            val y_calc = subterm + accelTerm

            if (y_calc < h) {
                lowPitch = midPitch
            } else {
                highPitch = midPitch
                bestPitch = midPitch
                bestTime = d2d / v_x
                foundValidTrajectory = true
            }
        }

        val theta = Math.toRadians(if (foundValidTrajectory) bestPitch else if (highArc) 60.0 else 30.0)
        val yaw = Math.atan2(dpos.x, dpos.z)

        val dir = Vec3(
            -Math.sin(yaw) * Math.cos(theta),
            -Math.sin(theta),
            Math.cos(yaw) * Math.cos(theta)
        )

        if (!foundValidTrajectory) {
            val ricoCalcDir = velocityGivenTime(minimizeScalarBounded({ t ->
                abs(velocityGivenTime(t, dpos).lengthSqr() - squaredSpeed)
            }, if (highArc) 35.0 else 1e-6, if (highArc) 120.0 else 60.0), dpos)

            val ricoCalcTime = minimizeScalarBounded({ t ->
                abs(velocityGivenTime(t, dpos).lengthSqr() - squaredSpeed)
            }, if (highArc) 35.0 else 1e-6, if (highArc) 120.0 else 60.0)

            return LookDirAndTime(ricoCalcDir.normalize(), ricoCalcTime)
        }

        return LookDirAndTime(dir.normalize(), bestTime)
    }
}