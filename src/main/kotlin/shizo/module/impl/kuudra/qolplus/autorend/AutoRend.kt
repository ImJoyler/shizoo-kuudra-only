package shizo.module.impl.kuudra.qolplus.autorend

import com.github.stivais.commodore.Commodore
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.lwjgl.glfw.GLFW
import shizo.clickgui.settings.impl.BooleanSetting
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.clickgui.settings.impl.NumberSetting
import shizo.events.GuiEvent
import shizo.events.InputEvent
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.mixin.accessors.KeyMappingAccessor
import shizo.module.impl.Module
import shizo.module.impl.misc.CommandKeybinds.openWardrobe
import shizo.utils.SwapState
import shizo.utils.clickSlot
import shizo.utils.devMessage
import shizo.utils.handlers.schedule
import shizo.utils.leftClick
import shizo.utils.modMessage
import shizo.utils.renderUtils.renderUtils.textDim
import shizo.utils.rightClick
import shizo.utils.skyblock.kuudra.KuudraUtils
import shizo.utils.swapFromNameAR
import shizo.utils.toFixed

object AutoRend : Module(
    name = "Auto Rend",
    description = "stop being skill issued",
    subcategory = "QOL"
) {
    private val autoTrigger by BooleanSetting("Auto Trigger", false, "starts rendo automatically when within 15 blocks of kuudra (WIP).")
    private val wardrobeSlot by NumberSetting(
        "Tux  Slot",
        4.0,
        1.0,
        9.0,
        1.0,
        desc = "where you keep tux bucko."
    )
    private val kuudraonly by BooleanSetting("P4 Only", true, "Only works in p4 ion kuudra")
    private val boneRend by BooleanSetting("Pull with bone", true, "")
    private val termRend by BooleanSetting("Pull with term", true, "your teminator must be called Spiritual Terminator")
    private val Keybind by KeybindSetting("Start Rend", GLFW.GLFW_KEY_UNKNOWN, "Press to start rend sequence").onPress {
        if (!enabled ||  (kuudraonly && (!KuudraUtils.inKuudra || KuudraUtils.phase != 4))) return@onPress
        if (rending) {modMessage("Arleady rending BUCKO")
            return@onPress}

        startRend()
    }
    // mainly used for debug ngl
    val priorityHud by HUD("Bone throw HUD", "mainly used for me for debug xoxo.") { example ->
        if (example) return@HUD textDim("§a§lTHROW NOW §7(14.50m)", 0, 0)
        if (!KuudraUtils.inKuudra || KuudraUtils.phase != 4) return@HUD 0 to 0

        val distStr = currentDistance.toFixed(2)

        when {
            !isAiming -> textDim("§4✖ Not aiming at Kuudra!", 0, 0) // <-- NEW CHECK
            throwNow -> textDim("§a§lTHROW NOW §7(${distStr}m)", 0, 0)
            currentDistance in 12.0..14.9 -> textDim("§e§lADJUST DISTANCE! §7(${distStr}m)", 0, 0)
            else -> textDim("§cWait... §7(${distStr}m)", 0, 0)
        }
    }
    private var throwNow = false
    private var hasAutoRended = false
    private var currentDistance = 0.0

    private var currentState = RendState.IDLE
    private var stateTimer = 0
    private var rending = false
    private var boneTimer = -1
    private var isAiming = false

    val autoRendCommand = Commodore("autorend") {
        literal("start").runs { startRend()
        }
    }
    private fun resetMacro(reason: String = "Finished") {
        if (reason != "Finished") modMessage("macro aborted: $reason")
        currentState = RendState.IDLE
        rending = false
        boneTimer = -1
        stateTimer = 0
    }
    var blockTime = 0L

    init {
        // surely there is a better way to do this than this lol
        on<InputEvent> {
            if (!rending) return@on

            val opts = mc.options

            val allowedKeys = setOf(
                (opts.keyUp as KeyMappingAccessor).boundKey.value,
                (opts.keyDown as KeyMappingAccessor).boundKey.value,
                (opts.keyLeft as KeyMappingAccessor).boundKey.value,
                (opts.keyRight as KeyMappingAccessor).boundKey.value,
                (opts.keyJump as KeyMappingAccessor).boundKey.value,
                (opts.keyShift as KeyMappingAccessor).boundKey.value,
                GLFW.GLFW_KEY_ESCAPE
            )

            if (key.value !in allowedKeys) {
                val currentTime = System.currentTimeMillis()

                if (currentTime - blockTime > 1500) {
                    modMessage("§cKey blocked! Only movement is allowed during Rend.")
                    blockTime = currentTime
                }

                cancel()
            }
        }
        on<GuiEvent.MouseClick> {
            if (rending && (currentState == RendState.WAITING_GUI || currentState == RendState.GUI_OPENED)) {
                modMessage("blopcked u clicking and banning :D")
                cancel()
            }
        }
        on<GuiEvent.ContainerKeyPress> {
            if (rending && (currentState == RendState.WAITING_GUI || currentState == RendState.GUI_OPENED)) {
                devMessage("blocked accidental key press! we don't want to pull a kairo...")
                cancel()
            }
        }

        on<TickEvent.Start> {
            checkKuudraDistance()
        }

        on<TickEvent.Start> {
            if (currentState == RendState.IDLE) return@on

            if (mc.screen != null && currentState != RendState.WAITING_GUI && currentState != RendState.GUI_OPENED) {
                resetMacro("Unexpected GUI opened")
                return@on
            }
            val player = mc.player ?: return@on
            stateTimer++
            if (boneTimer >= 0) boneTimer++

            when (currentState) {
                RendState.SWAPPED_BONE -> {
                    if (stateTimer >= 3) { // to check if we mgiht have to do 3
                        devMessage("throwing bone.")
                        // fix this
                    rightClick()
                        boneTimer = 0

                        currentState = RendState.SWAPPED_ATOM
                        stateTimer = 0
                    }
                }

                RendState.SWAPPED_ATOM -> {
                    if (stateTimer == 2) {
                        devMessage("swapping to atom.")
                        val swapResult = swapFromNameAR("Atomsplit")
                        if (swapResult == SwapState.UNKNOWN || swapResult == SwapState.TOO_FAST) {
                            resetMacro("Missing Atomsplit")
                            return@on
                        }
                    }
                    if (stateTimer >= 4){
                        devMessage("openinig wd.")

                        openWardrobe()
                        currentState = RendState.WAITING_GUI
                        stateTimer = 0
                    }
                }

                RendState.WAITING_GUI -> {
                    if (mc.screen != null) {
                        val container = mc.screen as? AbstractContainerScreen<*>

                        if (container != null && container.title?.string?.contains("Wardrobe") == true) {
                            currentState = RendState.GUI_OPENED
                            stateTimer = 0
                        } else {
                            resetMacro("Unexpected GUI opened")
                        }
                    }
                    else if (stateTimer > 40) {
                        resetMacro("Wardrobe Timeout")
                    }
                }

                RendState.GUI_OPENED -> {
                    if (stateTimer == 3) {
                        val container = mc.screen as? AbstractContainerScreen<*> ?: return@on

                        val slotToClick = 35 + wardrobeSlot.toInt()

                        mc.player?.clickSlot(container.menu.containerId, slotToClick)
                    }
                    if (stateTimer >= 4) {

                        schedule(0) {
                            //better safe than sorry!
                            val player = mc.player ?: return@schedule
                            val currentScreen = mc.screen as? AbstractContainerScreen<*>
                            val isWardrobe = currentScreen != null && currentScreen.title?.string?.contains("Wardrobe") == true
                            val serverThinksOpen = player.containerMenu != player.inventoryMenu
                            if (isWardrobe && serverThinksOpen) {
                                player.closeContainer()
                            }
                        //mc.screen = null
                        }
                            //mc.player?.closeContainer()
                        // WHY THE FUCK WOULD THIS BLOCK MY MOUSE INPUT
                        //mc.setScreen(null)
                        //mc.mouseHandler.grabMouse()

                        currentState = RendState.SWAP_TO_ENDSTONE
                        stateTimer = 0
                    }
                }

                RendState.SWAP_TO_ENDSTONE -> {
                    if (boneTimer >= 22) { // 1.1 seconds !
                        if (mc.screen != null) return@on
                        devMessage("swapping to end stone.")
                        val swapResult = swapFromNameAR("End Stone")
                        if (swapResult == SwapState.UNKNOWN || swapResult == SwapState.TOO_FAST) {
                            resetMacro("Missing End Stone")
                            return@on
                        }
                        currentState = RendState.ENDSTONE
                        stateTimer = 0
                    }
                }
                RendState.ENDSTONE -> {
                    if (stateTimer >= 2) {
                        if (mc.screen != null) return@on
                        devMessage("casting to end stone.")
                        rightClick()
                        currentState = RendState.REND
                        stateTimer = 0
                    }
                }
                RendState.REND -> {
                    if (stateTimer >= 1) {
                        if (!boneRend && !termRend) {
                            modMessage("turn on either Bone rend or Term Rend")
                            resetMacro("Settings Invalid")
                            return@on
                        }
                        if (boneRend && termRend) {
                            modMessage("You can only use bone or term rend")
                            resetMacro("Settings Cobnflict")
                            return@on
                        }
                        val swapResult = if (boneRend) {
                            devMessage("swapping to bone for pull.")
                            swapFromNameAR("Bonemerang")
                        } else {
                            devMessage("swapping to term for pull.")
                            swapFromNameAR("Spiritual Terminator")
                        }
                        if (swapResult == SwapState.UNKNOWN || swapResult == SwapState.TOO_FAST) {
                            resetMacro("Missing pull weapon")
                            return@on
                        }
                        currentState = RendState.PULL
                        stateTimer = 0
                    }
                }
                RendState.PULL -> {
                    if (stateTimer >= 2) {
                        devMessage("pulling")
                        if (mc.screen != null) return@on
                        leftClick()
                        resetMacro()
                    }
                }
                else -> {}
            }
        }
    }
    private fun checkKuudraDistance() {
        val player = mc.player ?: return
        val y = player.y

        if (!KuudraUtils.inKuudra || KuudraUtils.phase != 4 || (y !in 5.9..6.5)) {
            throwNow = false
            isAiming = false
            hasAutoRended = false
            currentDistance = 0.0
            return
        }

        val kuudra = KuudraUtils.kuudraEntity ?: return
        val eyePos = player.eyePosition
        val box = kuudra.boundingBox
        val lookVec = player.lookAngle

        val endPos = eyePos.add(lookVec.x * 30.0, lookVec.y * 30.0, lookVec.z * 30.0)
        val hitResult = box.clip(eyePos, endPos)

        if (hitResult.isPresent) {
            isAiming = true
            val hitPoint = hitResult.get()
            currentDistance = eyePos.distanceTo(hitPoint)

            if (currentDistance in 13.0..14.9) {
                throwNow = true

                if (autoTrigger && !hasAutoRended && !rending) {
                    startRend()
                    hasAutoRended = true
                }
            } else {
                throwNow = false
            }
        } else {
            isAiming = false
            throwNow = false
            currentDistance = 0.0
        }
    }

        fun startRend() {
            if (rending || mc.screen != null || currentState != RendState.IDLE) return
            val player = mc.player ?: return
            val heldItem = player.mainHandItem.displayName.string

            rending = true
            boneTimer = -1

            if (!heldItem.contains("Bonemerang")) {
                if (mc.screen != null) {
                    devMessage("Aborted cuz guis  open.")
                    return
                }

                devMessage("START swapping to bone.")
                val swapResult = swapFromNameAR("Bonemerang")
                if (swapResult == SwapState.UNKNOWN || swapResult == SwapState.TOO_FAST) {
                    resetMacro("Missing Bonemerang")
                    return
                }
                currentState = RendState.SWAPPED_BONE
                stateTimer= 0
            } else {
                devMessage("arleady holding bone.")
                currentState = RendState.SWAPPED_BONE
                stateTimer = 2 // bypass first bit chill like THAT IM SO FUCKING SMART
            }
        }
    }

