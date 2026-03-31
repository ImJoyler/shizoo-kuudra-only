package shizo.utils.clickgui.rendering.joyshit

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import shizo.Shizo.mc
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.clickgui.rendering.NVGRenderer
import shizo.utils.clickgui.rendering.NVGSpecialRenderer
import shizo.utils.clickgui.rendering.joyshit.GUIRenderer.drawNeonAnimatedGlow
import kotlin.math.max
import kotlin.math.pow


class Notification(
    val title: String,
    val description: String,
    val duration: Double,
    val color: Color,
    val gifIcon: net.minecraft.resources.ResourceLocation? = null
    // iconPath: String? = null
) {
    val startTime = System.currentTimeMillis()
    val endTime = startTime + duration.toLong()

    var currentY = -1f
    var currentX = -1f
    var currentHeight = -1f
//    val image by lazy { iconPath?.let { NVGRenderer.createImage(it) }}
}


object Notifications {
    private val notifications = mutableListOf<Notification>()

    fun send(title: String, description: String = "", duration: Double = 2000.0, color: Color = Colors.WHITE, gifIcon: net.minecraft.resources.ResourceLocation? = null) {
        notifications.add(Notification(title, description, duration, color, gifIcon))
    }

    init {
        HudElementRegistry.addLast(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("shizo", "notifications")
        ) { context, _ ->

            if (notifications.isEmpty()) return@addLast

            NVGSpecialRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
                render()
            }

            for (notif in notifications) {
                if (notif.gifIcon != null && notif.currentX != -1f) {
                    val currentFrame = GifLoader.getFrame(notif.gifIcon)

                    if (currentFrame != null) {
                        val iconSize = 20
                        val iconX = (notif.currentX + 12f).toInt()
                        val iconY = (notif.currentY + (notif.currentHeight / 2f) - (iconSize / 2f)).toInt()

                        shizo.utils.renderUtils.renderUtils.draw2DImage(context, currentFrame, iconX, iconY, iconSize, iconSize)
                    }
                }
            }
        }
    }

    private fun render() {
        notifications.removeIf { it.endTime <= System.currentTimeMillis() }

        val scale = mc.window.guiScale.toFloat()
        val screenWidth = mc.window.guiScaledWidth.toFloat()
        val screenHeight = mc.window.guiScaledHeight.toFloat()
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(true) // For smooth animations!

        var targetY = screenHeight - 20f

        for (notif in notifications) {
            val titleW = NVGRenderer.textWidth(notif.title, 16f * scale, NVGRenderer.defaultFont) / scale
            val descW = if (notif.description.isNotEmpty()) NVGRenderer.textWidth(notif.description, 14f * scale, NVGRenderer.defaultFont) / scale else 0f

            val iconOffset = if (notif.gifIcon != null) 26f else 0f
            val width = kotlin.math.max(150f, kotlin.math.max(titleW, descW) + 25f + iconOffset)
            val height = if (notif.description.isNotEmpty()) 45f else 30f

            val timeAlive = System.currentTimeMillis() - notif.startTime
            val timeLeft = notif.endTime - System.currentTimeMillis()

            var xOffset = 0f
            if (timeAlive < 250L) {
                val progress = 1.0 - (timeAlive / 250.0)
                xOffset = (width + 20f) * Easing.easeInQuad(progress).toFloat()
            } else if (timeLeft < 250L) {
                val progress = 1.0 - (timeLeft / 250.0)
                xOffset = (width + 20f) * Easing.easeInQuad(progress).toFloat()
            }

            val x = screenWidth - width - 10f + xOffset

            if (notif.currentY == -1f) notif.currentY = targetY

            notif.currentY += (targetY - height - notif.currentY) * 0.2f * delta

            notif.currentX = x
            notif.currentHeight = height
            val drawY = notif.currentY
            val accent = notif.color

            drawNeonAnimatedGlow(x * scale, drawY * scale, width * scale, height * scale, accent, 0.4f, 5f * scale)
            NVGRenderer.rect(x * scale, drawY * scale, width * scale, height * scale, Color(0, 0, 0, 0.7f).rgba, 5f * scale)
            NVGRenderer.customRect(x * scale, drawY * scale, 4f * scale, height * scale, accent.rgba, 5f * scale, 0f, 0f, 5f * scale)
            NVGRenderer.hollowRect(x * scale, drawY * scale, width * scale, height * scale, 1.5f * scale, Color(accent.red, accent.green, accent.blue, 0.35f).rgba, 5f * scale)

            NVGRenderer.text(notif.title, (x + 12f + iconOffset) * scale, (drawY + 8f) * scale, 16f * scale, Colors.WHITE.rgba, NVGRenderer.defaultFont)
            if (notif.description.isNotEmpty()) {
                NVGRenderer.text(notif.description, (x + 12f + iconOffset) * scale, (drawY + 24f) * scale, 14f * scale, Color(170, 170, 170).rgba, NVGRenderer.defaultFont)
            }

            targetY -= (height + 10f)
        }
    }

    object Easing {
        fun easeInQuad(t: Double): Double = t * t
        fun easeOutBack(t: Double): Double {
            val c1 = 1.70158
            val c3 = c1 + 1
            return 1 + c3 * (t - 1).pow(3.0) + c1 * (t - 1).pow(2.0)
        }
    }
}