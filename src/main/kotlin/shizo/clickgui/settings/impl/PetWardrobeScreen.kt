package shizo.clickgui.settings.impl

import net.minecraft.client.CameraType
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import shizo.Shizo.mc
import shizo.module.impl.render.ClickGUIModule
import shizo.module.impl.render.PlayerSize
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.clickgui.isAreaHovered
import shizo.utils.clickgui.rendering.NVGRenderer
import shizo.utils.clickgui.rendering.NVGSpecialRenderer
import shizo.utils.clickgui.rendering.joyshit.GUIRenderer.drawNeonAnimatedGlow
import shizo.utils.handlers.CameraHandler
import shizo.utils.handlers.CameraProvider
import kotlin.math.sign
import shizo.utils.clickgui.mouseX as odinMouseX
import shizo.utils.clickgui.mouseY as odinMouseY

class PetWardrobeScreen(private val parentScreen: Screen?) : Screen(Component.literal("Pet Wardrobe")) {

    private var previousCameraType: CameraType = CameraType.FIRST_PERSON
    private var isDragging = false
    private var customZoom: Double = 4.0

    private val wardrobeCamera = object : CameraProvider {
        override fun isActive(): Boolean = mc.screen is PetWardrobeScreen
        override fun getPriority(): Int = 999

        override fun shouldOverridePosition(): Boolean = !mc.options.cameraType.isFirstPerson

        override fun getCameraPosition(): Vec3? {
            val player = mc.player ?: return null
            if (mc.options.cameraType.isFirstPerson) return null

            val look = Vec3.directionFromRotation(player.xRot, player.yRot)

            return if (mc.options.cameraType == CameraType.THIRD_PERSON_FRONT) {
                player.eyePosition.add(look.scale(customZoom))
            } else {
                player.eyePosition.subtract(look.scale(customZoom))
            }
        }
    }

    override fun init() {
        super.init()
        previousCameraType = mc.options.cameraType
        if (previousCameraType.isFirstPerson) mc.options.cameraType = CameraType.THIRD_PERSON_BACK
        if (PlayerSize.devMob.value == 0) PlayerSize.devMob.value = 1

        CameraHandler.registerProvider(wardrobeCamera)
    }

    override fun onClose() {
        mc.options.cameraType = previousCameraType
        super.onClose()
    }

    private fun swapCamera() {
        mc.options.cameraType = when(mc.options.cameraType) {
            CameraType.FIRST_PERSON -> CameraType.THIRD_PERSON_BACK
            CameraType.THIRD_PERSON_BACK -> CameraType.THIRD_PERSON_FRONT
            CameraType.THIRD_PERSON_FRONT -> CameraType.FIRST_PERSON
        }
        previousCameraType = mc.options.cameraType
    }

    private fun drawPanel(x: Float, y: Float, w: Float, h: Float, title: String, accent: Color) {
        drawNeonAnimatedGlow(x, y, w, h, accent, 0.5f, 10f)
        NVGRenderer.rect(x, y, w, h, Color(15, 15, 15, 0.9f).rgba, 8f)
        NVGRenderer.hollowRect(x, y, w, h, 1.5f, accent.rgba, 8f)
        val tW = NVGRenderer.textWidth(title, 18f, NVGRenderer.defaultFont)
        NVGRenderer.text(title, x + (w / 2f) - (tW / 2f), y + 20f, 18f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        NVGRenderer.rect(x + 15f, y + 35f, w - 30f, 1f, Color(255, 255, 255, 0.2f).rgba)
    }

    private fun drawCheckbox(x: Float, y: Float, text: String, state: Boolean, hovered: Boolean, accent: Color) {
        val boxColor = if (state) accent else if (hovered) Color(60, 60, 60) else Color(40, 40, 40)
        NVGRenderer.rect(x, y, 16f, 16f, boxColor.rgba, 4f)
        NVGRenderer.hollowRect(x, y, 16f, 16f, 1f, Color(100, 100, 100).rgba, 4f)
        NVGRenderer.text(text, x + 24f, y + 12f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val scale = ClickGUIModule.getStandardGuiScale()
        val accent = ClickGUIModule.clickGUIColor
        val data = PlayerSize.getActivePetConfig()?.value

        NVGSpecialRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            NVGRenderer.scale(scale, scale)
            val scaledW = mc.window.width / scale
            val scaledH = mc.window.height / scale

            val boxW = 260f
            val boxH = 240f
            val leftX = 20f
            val rightX = scaledW - boxW - 20f
            val boxY = scaledH / 2f - (boxH / 2f)

            drawPanel(leftX, boxY, boxW, boxH, "Global Settings", accent)

            var cy = boxY + 50f
            drawCheckbox(leftX + 20f, cy, "Zoo Mode", PlayerSize.zooMode, isAreaHovered(leftX + 20f, cy, 200f, 16f, true), accent); cy += 25f
            drawCheckbox(leftX + 20f, cy, "Ride Pet", PlayerSize.devRide, isAreaHovered(leftX + 20f, cy, 200f, 16f, true), accent); cy += 25f
            drawCheckbox(leftX + 20f, cy, "Hide Player", PlayerSize.hidePlayer, isAreaHovered(leftX + 20f, cy, 200f, 16f, true), accent); cy += 25f
            drawCheckbox(leftX + 20f, cy, "Schizo Mode", PlayerSize.schizoMode, isAreaHovered(leftX + 20f, cy, 200f, 16f, true), accent); cy += 25f

            val camBtnX = leftX + 15f
            val camBtnY = boxY + boxH - 45f
            val camBtnW = boxW - 30f
            val camBtnH = 24f

            val camHover = isAreaHovered(camBtnX, camBtnY, camBtnW, camBtnH, true)
            NVGRenderer.rect(camBtnX, camBtnY, camBtnW, camBtnH, Color(accent.red, accent.green, accent.blue, if(camHover) 0.3f else 0.15f).rgba, 4f)
            NVGRenderer.hollowRect(camBtnX, camBtnY, camBtnW, camBtnH, 1f, accent.rgba, 4f)
            val cW = NVGRenderer.textWidth("Swap Camera [F5]", 14f, NVGRenderer.defaultFont)
            NVGRenderer.text("Swap Camera [F5]", camBtnX + (camBtnW / 2f) - (cW / 2f), camBtnY + 16f, 14f, Colors.WHITE.rgba, NVGRenderer.defaultFont)

            drawPanel(rightX, boxY, boxW, boxH, "Pet Editor", accent)

            val currentPetName = PlayerSize.devMob.options.getOrNull(PlayerSize.devMob.value) ?: "Unknown"
            val lh = isAreaHovered(rightX + 15f, boxY + 45f, 20f, 20f, true)
            NVGRenderer.text("<", rightX + 15f, boxY + 50f, 22f, if (lh) accent.rgba else Colors.WHITE.rgba, NVGRenderer.defaultFont)
            val nW = NVGRenderer.textWidth(currentPetName, 20f, NVGRenderer.defaultFont)
            NVGRenderer.text(currentPetName, rightX + (boxW / 2f) - (nW / 2f), boxY + 52f, 20f, accent.rgba, NVGRenderer.defaultFont)
            val rh = isAreaHovered(rightX + boxW - 35f, boxY + 45f, 20f, 20f, true)
            NVGRenderer.text(">", rightX + boxW - 35f, boxY + 50f, 22f, if (rh) accent.rgba else Colors.WHITE.rgba, NVGRenderer.defaultFont)

            var txtY = boxY + 80f

            if (PlayerSize.zooMode) {
                drawCheckbox(rightX + 20f, txtY, "Render This Pet", PlayerSize.isCurrentPetZooEnabled(), isAreaHovered(rightX + 20f, txtY, 200f, 16f, true), accent)
                txtY += 25f
            }

            if (data != null) {
                NVGRenderer.text("Scale (Scroll): ${String.format("%.2f", data.size)}", rightX + 15f, txtY, 15f, Colors.WHITE.rgba, NVGRenderer.defaultFont); txtY += 20f
                NVGRenderer.text("Dist (Up/Down): ${String.format("%.2f", data.dist)}", rightX + 15f, txtY, 15f, Colors.WHITE.rgba, NVGRenderer.defaultFont); txtY += 20f
                NVGRenderer.text("Side (Left/Right): ${String.format("%.2f", data.side)}", rightX + 15f, txtY, 15f, Colors.WHITE.rgba, NVGRenderer.defaultFont); txtY += 20f
                NVGRenderer.text("Height (W/S): ${String.format("%.2f", data.height)}", rightX + 15f, txtY, 15f, Colors.WHITE.rgba, NVGRenderer.defaultFont); txtY += 20f
                NVGRenderer.text("Pitch/Yaw (Drag!): ${String.format("%.1f", data.pitch)}", rightX + 15f, txtY, 15f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
            }

            val infoW = NVGRenderer.textWidth("Ctrl+Scroll to Zoom Camera", 13f, NVGRenderer.defaultFont)
            NVGRenderer.text("Ctrl+Scroll to Zoom Camera", rightX + (boxW/2f) - (infoW/2f), boxY + boxH - 15f, 13f, Color(150, 150, 150).rgba, NVGRenderer.defaultFont)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val scale = ClickGUIModule.getStandardGuiScale()
        val boxW = 260f
        val boxH = 240f
        val leftX = 20f
        val rightX = (mc.window.width / scale) - boxW - 20f
        val boxY = (mc.window.height / scale) / 2f - (boxH / 2f)

        if (event.button() == 0) {
            var cy = boxY + 50f
            if (isAreaHovered(leftX + 20f, cy, 200f, 16f, true)) { PlayerSize.zooMode = !PlayerSize.zooMode; return true }; cy += 25f
            if (isAreaHovered(leftX + 20f, cy, 200f, 16f, true)) { PlayerSize.devRide = !PlayerSize.devRide; return true }; cy += 25f
            if (isAreaHovered(leftX + 20f, cy, 200f, 16f, true)) { PlayerSize.hidePlayer = !PlayerSize.hidePlayer; return true }; cy += 25f
            if (isAreaHovered(leftX + 20f, cy, 200f, 16f, true)) { PlayerSize.schizoMode = !PlayerSize.schizoMode; return true }

            val camBtnX = leftX + 15f
            val camBtnY = boxY + boxH - 45f
            val camBtnW = boxW - 30f
            val camBtnH = 24f
            if (isAreaHovered(camBtnX, camBtnY, camBtnW, camBtnH, true)) { swapCamera(); return true }

            if (isAreaHovered(rightX + 15f, boxY + 45f, 20f, 20f, true)) { PlayerSize.cyclePet(false); return true }
            if (isAreaHovered(rightX + boxW - 35f, boxY + 45f, 20f, 20f, true)) { PlayerSize.cyclePet(true); return true }

            if (PlayerSize.zooMode && isAreaHovered(rightX + 20f, boxY + 80f, 200f, 16f, true)) {
                PlayerSize.toggleCurrentPetZoo()
                return true
            }

            if (!isAreaHovered(leftX, boxY, boxW, boxH, true) && !isAreaHovered(rightX, boxY, boxW, boxH, true)) {
                isDragging = true
                return true
            }
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 0) {
            isDragging = false
            return true
        }
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (isDragging) {
            val data = PlayerSize.getActivePetConfig()?.value ?: return false

            data.yaw -= (dragX * 2.0).toFloat()
            data.pitch += (dragY * 2.0).toFloat()

            data.pitch = data.pitch.coerceIn(-180f, 180f)
            return true
        }
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (keyEvent.key == GLFW.GLFW_KEY_ESCAPE) {
            mc.setScreen(parentScreen)
            return true
        }
        if (keyEvent.key == GLFW.GLFW_KEY_F5) {
            swapCamera()
            return true
        }

        val data = PlayerSize.getActivePetConfig()?.value ?: return super.keyPressed(keyEvent)
        val speed = 0.5f
        val speed2 = 0.1f

        when (keyEvent.key) {
            GLFW.GLFW_KEY_UP -> data.dist += speed2
            GLFW.GLFW_KEY_DOWN -> data.dist -= speed2
            GLFW.GLFW_KEY_LEFT -> data.side += speed2
            GLFW.GLFW_KEY_RIGHT -> data.side -= speed2
            GLFW.GLFW_KEY_W -> data.height += speed2
            GLFW.GLFW_KEY_S -> data.height -= speed2
            GLFW.GLFW_KEY_A -> data.yaw -= 5f
            GLFW.GLFW_KEY_D -> data.yaw += 5f
        }

        return super.keyPressed(keyEvent)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val isCtrl = com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
                com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_RIGHT_CONTROL)

        if (isCtrl) {
            customZoom = (customZoom - (scrollY.sign * 0.5)).coerceIn(1.0, 50.0)
            return true
        }

        val data = PlayerSize.getActivePetConfig()?.value ?: return false
        data.size = (data.size + (scrollY.sign * 0.1f).toFloat()).coerceIn(0.1f, 13.0f)
        return true
    }

    override fun isPauseScreen(): Boolean = false
    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {}
}