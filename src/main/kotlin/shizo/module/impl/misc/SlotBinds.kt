package shizo.module.impl.misc

import shizo.mixin.accessors.AbstractContainerScreenAccessor
import shizo.clickgui.settings.impl.ColorSetting
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.clickgui.settings.impl.MapSetting
import shizo.clickgui.settings.impl.SelectorSetting
import shizo.events.GuiEvent
import shizo.events.InputEvent
import shizo.events.core.EventPriority
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.module.impl.ModuleManager
import shizo.utils.Colors
import shizo.utils.clickSlot
import shizo.utils.modMessage
import shizo.utils.renderUtils.renderUtils.drawLine
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.ClickType
import org.lwjgl.glfw.GLFW
import shizo.mixin.accessors.KeyMappingAccessor

object SlotBinds : Module(
    name = "Slot Binds",
    description = "Bind slots together and lock them to prevent drops.",
    key = null
) {
    private val setNewSlotbind by KeybindSetting("Bind set key", GLFW.GLFW_KEY_UNKNOWN, desc = "Key to set new bindings.")
    private val lockSlotBind by KeybindSetting("Lock Slot Key", GLFW.GLFW_KEY_L, desc = "Key to lock/unlock a slot to prevent moving/dropping.")

    private val lineColor by ColorSetting("Line Color", Colors.MINECRAFT_GOLD, desc = "Color of the line drawn between slots.")
    private val profileOptions = listOf("Profile 1", "Profile 2", "Profile 3", "Profile 4", "Profile 5", "Profile 6")
    private val currentProfile by SelectorSetting("Profile", "Profile 1", profileOptions, desc = "Select which profile to use.")

    private val profileData by MapSetting("ProfileData", mutableMapOf<String, MutableMap<Int, Int>>())
    private val lockedProfileData by MapSetting("LockedSlots", mutableMapOf<String, MutableSet<Int>>())

    private val currentProfileName: String
        get() = profileOptions[currentProfile]

    private val slotBinds: MutableMap<Int, Int>
        get() = profileData.getOrPut(currentProfileName) { mutableMapOf() }

    private val lockedSlots: MutableSet<Int>
        get() = lockedProfileData.getOrPut(currentProfileName) { mutableSetOf() }

    private var previousSlot: Int? = null

    // Stolen from NEU ngl (respect)
    private val BOUND_TEXTURE by lazy { ResourceLocation.fromNamespaceAndPath("shizo", "textures/gui/bound.png") }
    private val LOCK_TEXTURE by lazy { ResourceLocation.fromNamespaceAndPath("shizo", "textures/gui/lock.png") }

    init {
        on<InputEvent> {
            if (mc.player == null || mc.screen != null) return@on
            val dropKey = mc.options.keyDrop as KeyMappingAccessor

            if (key.value == dropKey.boundKey.value) {
                val selectedSlot = mc.player!!.inventory.selectedSlot + 36
                val isLocked = lockedSlots.contains(selectedSlot)
                val isBound = slotBinds.containsKey(selectedSlot) || slotBinds.containsValue(selectedSlot)

                if (isLocked || isBound) {
                    cancel()
                    val reason = if (isLocked) "locked" else "bound"
                    modMessage("§cThat slot is $reason! You cannot drop the item/s you silly goose.")
                }
            }
        }


        on<GuiEvent.SlotClick>(EventPriority.HIGHEST) {
            if (screen !is InventoryScreen) return@on
            val clickedSlot = (screen as AbstractContainerScreenAccessor).hoveredSlot?.index ?: return@on

            val boundSlot = slotBinds[clickedSlot]
            var handledAsSwap = false

            if (mc.hasShiftDown() && boundSlot != null) {
                if (clickedSlot in 36..44 || boundSlot in 36..44) {
                    val from = if (clickedSlot in 36..44) boundSlot else clickedSlot
                    val to = if (clickedSlot in 36..44) clickedSlot else boundSlot

                    mc.player?.clickSlot(screen.menu.containerId, from, to % 36, ClickType.SWAP)
                    cancel()
                    handledAsSwap = true
                }
            }

            if (handledAsSwap) return@on

            if (lockedSlots.contains(clickedSlot)) {
                cancel()
                return@on
            }
        }

        on<GuiEvent.KeyPress> {
            if (screen !is InventoryScreen) return@on
            val hoveredSlotIndex = (screen as AbstractContainerScreenAccessor).hoveredSlot?.index ?: return@on
            val dropKey = mc.options.keyDrop as KeyMappingAccessor

            if (input.key == dropKey.boundKey.value) {
                val isLocked = lockedSlots.contains(hoveredSlotIndex)
                val isBound = slotBinds.containsKey(hoveredSlotIndex) || slotBinds.containsValue(hoveredSlotIndex)

                if (isLocked || isBound) {
                    cancel()
                    val reason = if (isLocked) "locked" else "bound"
                    modMessage("§cThat slot is $reason! You cannot drop the item/s you silly goose.")
                    return@on
                }
            }

            if (hoveredSlotIndex !in 5..44) return@on

            if (input.key == lockSlotBind.value) {
                cancel()
                if (lockedSlots.contains(hoveredSlotIndex)) {
                    lockedSlots.remove(hoveredSlotIndex)
                    modMessage("§cUnlocked slot §b$hoveredSlotIndex §7(${profileOptions[currentProfile]}).")
                } else {
                    lockedSlots.add(hoveredSlotIndex)
                    modMessage("§aLocked slot §b$hoveredSlotIndex §7(${profileOptions[currentProfile]}).")
                }
                ModuleManager.saveConfigurations()
                return@on
            }

            if (input.key == setNewSlotbind.value) {
                cancel()
                previousSlot?.let { slot ->
                    if (slot == hoveredSlotIndex) return@on modMessage("§cYou can't bind a slot to itself.")
                    if (slot !in 36..44 && hoveredSlotIndex !in 36..44) return@on modMessage("§cOne of the slots must be in the hotbar (36–44).")
                    modMessage("§aAdded bind from slot §b$slot §ato §d${hoveredSlotIndex} §7(${profileOptions[currentProfile]}).")

                    slotBinds[slot] = hoveredSlotIndex
                    ModuleManager.saveConfigurations()
                    previousSlot = null
                } ?: run {
                    slotBinds.entries.firstOrNull { it.key == hoveredSlotIndex }?.let {
                        slotBinds.remove(it.key)
                        ModuleManager.saveConfigurations()
                        return@on modMessage("§cRemoved bind from slot §b${it.key} §cto §d${it.value} §7(${profileOptions[currentProfile]}).")
                    }
                    previousSlot = hoveredSlotIndex
                }
            }
        }

        on<GuiEvent.DrawTooltip> {
            val screen = screen as? InventoryScreen ?: return@on
            val containerScreen = screen as AbstractContainerScreenAccessor

            for (slot in screen.menu.slots) {
                if (lockedSlots.contains(slot.index)) {
                    val x = slot.x + screen.x
                    val y = slot.y + screen.y

                    guiGraphics.blit(
                        net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                        LOCK_TEXTURE,
                        x, y, 0f, 0f, 16, 16, 16, 16,
                        0x80FFFFFF.toInt()
                    )
                }
            }

            val hoveredSlot = containerScreen.hoveredSlot?.index?.takeIf { it in 5 until 45 } ?: return@on
            val boundSlotIndex = slotBinds[hoveredSlot]

            if (mc.hasShiftDown() && boundSlotIndex != null) {
                screen.menu.getSlot(boundSlotIndex)?.let { bSlot ->
                    val bx = bSlot.x + screen.x
                    val by = bSlot.y + screen.y

                    guiGraphics.blit(
                        net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                        BOUND_TEXTURE,
                        bx, by, 0f, 0f, 16, 16, 16, 16,
                        0xB2FFFFFF.toInt()
                    )
                }
            }

            val (startX, startY) = screen.menu.getSlot(previousSlot ?: hoveredSlot)?.let { slot ->
                slot.x + screen.x + 8 to slot.y + screen.y + 8
            } ?: return@on

            val (endX, endY) = previousSlot?.let { mouseX to mouseY } ?: boundSlotIndex?.let { slot ->
                screen.menu.getSlot(slot)?.let { it.x + screen.x + 8 to it.y + screen.y + 8 }
            } ?: return@on

            if (previousSlot == null && !(mc.hasShiftDown())) return@on

            guiGraphics.drawLine(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), lineColor, 1f)
        }
    }
}