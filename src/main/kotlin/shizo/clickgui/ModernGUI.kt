package shizo.clickgui

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import shizo.Shizo.mc
import shizo.clickgui.settings.RenderableSetting
import shizo.module.impl.Category
import shizo.module.impl.ModuleManager
import shizo.module.impl.render.ClickGUIModule
import shizo.module.impl.Module
import shizo.utils.Color
import shizo.utils.Colors
import shizo.utils.clickgui.HoverHandler
import shizo.utils.clickgui.animations.EaseOutAnimation
import shizo.utils.clickgui.rendering.joyshit.GUIRenderer.drawGlowingText
import shizo.utils.clickgui.rendering.joyshit.GUIRenderer.drawNeonAnimatedGlow
import shizo.utils.clickgui.rendering.NVGRenderer
import shizo.utils.clickgui.rendering.NVGSpecialRenderer
import shizo.utils.noControlCodes
import shizo.utils.renderUtils.renderUtils.draw2DImage
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.PI
import shizo.utils.clickgui.mouseX as odinMouseX
import shizo.utils.clickgui.mouseY as odinMouseY

object ModernGUI : Screen(Component.literal("Modern Config")) {

    private val openAnim = EaseOutAnimation(350)
    private val contentSwitchAnim = EaseOutAnimation(250)

    private var catHighlightY = -1f
    private var subHighlightX = -1f
    private var subHighlightW = 0f

    private val moduleHoverHandlers = mutableMapOf<Module, HoverHandler>()
    private val expandedModules = mutableSetOf<Module>()

    private var selectedCategory: Category = Category.categories.values.firstOrNull() ?: Category.KUUDRA
    private var selectedSubcategory: String = "General"

    private var tabScrollOffset = 0f
    private var targetTabScrollOffset = 0f
    private var scrollOffset = 0f
    private var targetScrollOffset = 0f

    private val bgColor = Color(0, 0, 0, 0.40f)
    private val sidebarColor = Color(0, 0, 0, 0.50f)
    private val accentColor get() = ClickGUIModule.clickGUIColor

    private val GUI_WIDTH: Float get() = ((mc.window.width / ClickGUIModule.getStandardGuiScale()) * 0.6f).coerceAtLeast(600f)
    private val GUI_HEIGHT: Float get() = ((mc.window.height / ClickGUIModule.getStandardGuiScale()) * 0.6f).coerceAtLeast(400f)
    private val SIDEBAR_WIDTH: Float get() = (GUI_WIDTH * 0.25f).coerceIn(150f, 220f)
    //private val cat by lazy { ResourceLocation.fromNamespaceAndPath("shizo", "textures/gui/cat.png")}
    private val cat by lazy { ResourceLocation.fromNamespaceAndPath("shizo", "textures/gui/cat.gif") }
    val version = try {
        FabricLoader.getInstance().getModContainer("shizo").get().metadata.version.friendlyString
    } catch (e: Exception) {
        "Unknown"
    }

    override fun init() {
        openAnim.start()
        contentSwitchAnim.start()
        super.init()
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        scrollOffset += (targetScrollOffset - scrollOffset) * 0.2f

        NVGSpecialRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            val scale = ClickGUIModule.getStandardGuiScale()
            val mx = odinMouseX / scale
            val my = odinMouseY / scale

            NVGRenderer.scale(scale, scale)

            val screenCenterX = mc.window.width / (2f * scale)
            val screenCenterY = mc.window.height / (2f * scale)

            val startX = screenCenterX - (GUI_WIDTH / 2f)
            val startY = screenCenterY - (GUI_HEIGHT / 2f)

            val contentX = startX + SIDEBAR_WIDTH
            val contentWidth = GUI_WIDTH - SIDEBAR_WIDTH

            if (openAnim.isAnimating()) {
                val animScale = openAnim.get(0.8f, 1f)
                NVGRenderer.translate(screenCenterX, screenCenterY)
                NVGRenderer.scale(animScale, animScale)
                NVGRenderer.translate(-screenCenterX, -screenCenterY)
            }
            val time = (System.currentTimeMillis() % 2000L) / 2000f
            val globalPulse = (sin(time * PI * 2).toFloat() * 0.5f) + 0.5f

            drawNeonAnimatedGlow(startX, startY, GUI_WIDTH, GUI_HEIGHT, accentColor, 0.45f, 12f)

            NVGRenderer.dropShadow(startX, startY, GUI_WIDTH, GUI_HEIGHT, 15f, 2f, 12f)
            NVGRenderer.rect(startX, startY, GUI_WIDTH, GUI_HEIGHT, bgColor.rgba, 12f)
            NVGRenderer.customRect(startX, startY, SIDEBAR_WIDTH, GUI_HEIGHT, sidebarColor.rgba, 12f, 0f, 12f, 0f)
            drawGlowingText("SHIZO", startX + 20f, startY + 26f, 26f, accentColor, globalPulse)

            val catSize = 32f
            val catX = startX + 20f
            val catYOffset  = startY + GUI_HEIGHT - 20f - catSize

            val editHudW = SIDEBAR_WIDTH - 30f
            val editHudH = 28f
            val editHudX = startX + 15f
            val editHudY = catYOffset - 12f - 10f - editHudH

            val isHudHovered = mx in editHudX..(editHudX + editHudW) && my in editHudY..(editHudY + editHudH)

            if (isHudHovered) {
                drawNeonAnimatedGlow(editHudX, editHudY, editHudW, editHudH, accentColor, 0.4f, 5f)
                NVGRenderer.rect(editHudX, editHudY, editHudW, editHudH, Color(accentColor.red, accentColor.green, accentColor.blue, 0.25f).rgba, 5f)
                NVGRenderer.hollowRect(editHudX, editHudY, editHudW, editHudH, 1.5f, accentColor.rgba, 5f)
            } else {
                NVGRenderer.rect(editHudX, editHudY, editHudW, editHudH, Color(0, 0, 0, 0.3f).rgba, 5f)
                NVGRenderer.hollowRect(editHudX, editHudY, editHudW, editHudH, 1.5f, Color(255, 255, 255, 0.15f).rgba, 5f)
            }

            val hudText = "Edit HUD"
            val hudTextW = NVGRenderer.textWidth(hudText, 14f, NVGRenderer.defaultFont)
            val hudTextColor = if (isHudHovered) Colors.WHITE.rgba else Color(170, 170, 170).rgba
            NVGRenderer.text(hudText, editHudX + (editHudW / 2f) - (hudTextW / 2f), editHudY + 7f, 14f, hudTextColor, NVGRenderer.defaultFont)
            NVGRenderer.rect(startX + 15f, catYOffset  - 12f, SIDEBAR_WIDTH - 30f, 1f, Color(255, 255, 255, 0.15f).rgba)

            val textX = catX + catSize + 10f
            NVGRenderer.text("By Joy & Cubey", textX, catYOffset  + 8f, 14f, accentColor.rgba, NVGRenderer.defaultFont)
            NVGRenderer.text("V $version", textX, catYOffset  + 22f, 12f, Color(170, 170, 170).rgba, NVGRenderer.defaultFont)

            var catYPos = startY + 65f
            var targetCatY = catYPos

            val sortedCategories = Category.categories.values.sortedBy { it.name }

            for (category in sortedCategories) {
                if (category == selectedCategory) {
                    targetCatY = catYPos
                    break
                }
                catYPos += 35f
            }

            if (catHighlightY == -1f) catHighlightY = targetCatY
            catHighlightY += (targetCatY - catHighlightY) * 0.15f

            drawNeonAnimatedGlow(startX + 12f, catHighlightY - 6f, SIDEBAR_WIDTH - 24f, 28f, accentColor, 0.6f, 6f)
            NVGRenderer.rect(startX + 12f, catHighlightY - 6f, SIDEBAR_WIDTH - 24f, 28f, Color(accentColor.red, accentColor.green, accentColor.blue, 0.25f).rgba, 6f)
            NVGRenderer.hollowRect(startX + 12f, catHighlightY - 6f, SIDEBAR_WIDTH - 24f, 28f, 1.5f, accentColor.rgba, 6f)

            catYPos = startY + 65f
            for (category in sortedCategories) {
                val color = if (category == selectedCategory) Colors.WHITE.rgba else Color(170, 170, 170).rgba
                NVGRenderer.text(category.name.noControlCodes, startX + 24f, catYPos, 16f, color, NVGRenderer.defaultFont)
                catYPos += 35f
            }

            val isSearching = SearchBar.currentSearch.isNotEmpty()

            val searchBarY = startY + 20f
            val tabY = searchBarY + 50f

            val searchBarX = contentX + (contentWidth / 2f) - 175f
            SearchBar.draw(searchBarX, searchBarY, mx, my)

            val modulesInCategory = ModuleManager.modulesByCategory[selectedCategory] ?: emptyList()
            val subcategories = modulesInCategory.map { it.subcategory }.distinct().sorted()

            if (selectedSubcategory !in subcategories) {
                selectedSubcategory = subcategories.firstOrNull() ?: "General"
                contentSwitchAnim.start()
            }

            val tabsMaxWidth = contentWidth - 50f
            var totalTabsWidth = 0f
            val tabWidths = FloatArray(subcategories.size)

            for (i in subcategories.indices) {
                val tw = NVGRenderer.textWidth(subcategories[i].noControlCodes, 16f, NVGRenderer.defaultFont) + 20f
                tabWidths[i] = tw
                totalTabsWidth += tw + 10f
            }

            totalTabsWidth -= 10f

            val maxTabScroll = (totalTabsWidth - tabsMaxWidth).coerceAtLeast(0f)
            targetTabScrollOffset = targetTabScrollOffset.coerceIn(-maxTabScroll, 0f)
            tabScrollOffset += (targetTabScrollOffset - tabScrollOffset) * 0.2f

            var tempX = contentX + 25f + tabScrollOffset
            var targetSubX = tempX
            var targetSubW = 50f

            for (i in subcategories.indices) {
                val sub = subcategories[i]
                val tabWidth = tabWidths[i]
                if (sub == selectedSubcategory) {
                    targetSubX = tempX
                    targetSubW = tabWidth
                    break
                }
                tempX += tabWidth + 10f
            }

            if (subHighlightX == -1f) { subHighlightX = targetSubX; subHighlightW = targetSubW }
            subHighlightX += (targetSubX - subHighlightX) * 0.15f
            subHighlightW += (targetSubW - subHighlightW) * 0.15f

            if (!isSearching) {
                NVGRenderer.pushScissor(contentX, startY, tabsMaxWidth + 25f, 110f)

                drawNeonAnimatedGlow(subHighlightX, tabY, subHighlightW, 26f, accentColor, 0.6f, 5f)
                NVGRenderer.rect(subHighlightX, tabY, subHighlightW, 26f, Color(accentColor.red, accentColor.green, accentColor.blue, 0.25f).rgba, 5f)
                NVGRenderer.hollowRect(subHighlightX, tabY, subHighlightW, 26f, 1.5f, accentColor.rgba, 5f)

                var tabX = contentX + 25f + tabScrollOffset
                for (i in subcategories.indices) {
                    val sub = subcategories[i]
                    val tabWidth = tabWidths[i]
                    if (sub != selectedSubcategory) {
                        NVGRenderer.rect(tabX, tabY, tabWidth, 26f, Color(0, 0, 0, 0.4f).rgba, 5f)
                    }
                    val textColor = if (sub == selectedSubcategory) Colors.WHITE.rgba else Color(170, 170, 170).rgba
                    NVGRenderer.text(sub.noControlCodes, tabX + 10f, tabY + 6f, 16f, textColor, NVGRenderer.defaultFont)
                    tabX += tabWidth + 10f
                }

                NVGRenderer.rect(contentX + 25f, tabY + 35f, contentWidth - 50f, 2f, Color(60, 60, 60).rgba, 1f)
                NVGRenderer.popScissor()
            }

            NVGRenderer.pushScissor(contentX, startY + 110f, contentWidth, GUI_HEIGHT - 110f)

            val contentAlpha = if (contentSwitchAnim.isAnimating()) contentSwitchAnim.get(0f, 1f) else 1f
            val contentSlideY = if (contentSwitchAnim.isAnimating()) contentSwitchAnim.get(15f, 0f) else 0f
            NVGRenderer.globalAlpha(contentAlpha)

            var contentY = startY + 120f + scrollOffset + contentSlideY

            val activeModules = if (isSearching) {
                ModuleManager.modulesByCategory.values.flatten().filter {
                    it.name.contains(SearchBar.currentSearch, ignoreCase = true) ||
                            it.description.contains(SearchBar.currentSearch, ignoreCase = true)
                }
            } else {
                modulesInCategory.filter { it.subcategory == selectedSubcategory }
            }

            var hoveredTooltip = ""

            for (module in activeModules) {
                val allSettings = module.settings.values.filterIsInstance<RenderableSetting<*>>()
                val isExpanded = expandedModules.contains(module) || module == ClickGUIModule
                val visibleSettings = if (isExpanded) allSettings.filter { it.isVisible } else emptyList()

                var settingsHeight = 0f
                visibleSettings.forEach { settingsHeight += it.getHeight() }

                val headerH = 50f
                val moduleBoxHeight = if (isExpanded && visibleSettings.isNotEmpty()) headerH + 10f + settingsHeight else headerH

                val boxX = contentX + 25f
                val boxW = contentWidth - 50f

                val hover = moduleHoverHandlers.getOrPut(module) { HoverHandler(200) }
                hover.handle(boxX, contentY, boxW, headerH, true)

                val hoverPercent = hover.percent() / 100f

                val glowIntensity = if (module.enabled) 0.6f + (0.3f * hoverPercent) else 0.5f * hoverPercent
                drawNeonAnimatedGlow(boxX, contentY, boxW, moduleBoxHeight, accentColor, glowIntensity, 5f)

                val bgAlpha = 0.35f + (0.1f * hoverPercent)

                if (module.enabled) {
                    val accentBgAlpha = 0.15f + (0.1f * hoverPercent)
                    val borderAlpha = 0.7f + (0.3f * hoverPercent)

                    NVGRenderer.rect(boxX, contentY, boxW, moduleBoxHeight, Color(0, 0, 0, bgAlpha).rgba, 5f)
                    NVGRenderer.rect(boxX, contentY, boxW, moduleBoxHeight, Color(accentColor.red, accentColor.green, accentColor.blue, accentBgAlpha).rgba, 5f)
                    NVGRenderer.hollowRect(boxX, contentY, boxW, moduleBoxHeight, 1.5f, Color(accentColor.red, accentColor.green, accentColor.blue, borderAlpha).rgba, 5f)

                    val r = (accentColor.red + (255 - accentColor.red) * (globalPulse * 0.3f)).toInt()
                    val g = (accentColor.green + (255 - accentColor.green) * (globalPulse * 0.3f)).toInt()
                    val b = (accentColor.blue + (255 - accentColor.blue) * (globalPulse * 0.3f)).toInt()

                    NVGRenderer.text(module.name.noControlCodes, boxX + 15f, contentY + 14f, 18f, Color(r, g, b).rgba, NVGRenderer.defaultFont)
                } else {
                    val borderAlpha = 0.15f + (0.2f * hoverPercent)

                    NVGRenderer.rect(boxX, contentY, boxW, moduleBoxHeight, Color(0, 0, 0, bgAlpha).rgba, 5f)
                    NVGRenderer.hollowRect(boxX, contentY, boxW, moduleBoxHeight, 1.5f, Color(255, 255, 255, borderAlpha).rgba, 5f)

                    NVGRenderer.text(module.name.noControlCodes, boxX + 15f, contentY + 14f, 18f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
                }

                NVGRenderer.text(module.description.noControlCodes, boxX + 15f, contentY + 32f, 13f, Color(160, 160, 160).rgba, NVGRenderer.defaultFont)

                if (isExpanded && visibleSettings.isNotEmpty()) {
                    val divColor = if (module.enabled) Color(accentColor.red, accentColor.green, accentColor.blue, 0.4f).rgba else Color(255, 255, 255, 0.15f).rgba
                    NVGRenderer.rect(boxX + 10f, contentY + headerH, boxW - 20f, 1f, divColor, 0.5f)

                    var settingY = contentY + headerH + 5f
                    for (setting in visibleSettings) {
                        val indent = if (setting.hasDependency) 15f else 0f
                        setting.width = boxW - 30f - indent
                        val addedHeight = setting.render(boxX + 15f + indent, settingY, mx, my)

                        if (setting.isHovered && setting.description.isNotEmpty()) {
                            hoveredTooltip = setting.description.noControlCodes
                        }

                        settingY += addedHeight
                    }
                }
                contentY += moduleBoxHeight + 15f
            }
            NVGRenderer.globalAlpha(1f)
            NVGRenderer.popScissor()

            if (hoveredTooltip.isNotEmpty()) {
                val area = NVGRenderer.wrappedTextBounds(hoveredTooltip, 300f, 16f, NVGRenderer.defaultFont)
                val w = area[2] - area[0] + 16f
                val h = area[3] - area[1] + 16f

                var tX = mx + 12f
                var tY = my + 12f

                val scaledWidth = mc.window.width / scale
                val scaledHeight = mc.window.height / scale

                if (tX + w > scaledWidth) tX = mx - w - 8f
                if (tY + h > scaledHeight) tY = scaledHeight - h - 8f

                NVGRenderer.dropShadow(tX, tY, w, h, 15f, 2f, 6f)
                NVGRenderer.rect(tX, tY, w, h, Color(20, 20, 20, 0.95f).rgba, 6f)
                NVGRenderer.hollowRect(tX, tY, w, h, 1f, accentColor.rgba, 6f)

                NVGRenderer.drawWrappedString(hoveredTooltip, tX + 8f, tY + 8f, 300f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
            }
        }

        val customScale = ClickGUIModule.getStandardGuiScale()
        val vanillaScale = mc.window.guiScale.toFloat()

        context.pose().pushMatrix()
        val factor = customScale / vanillaScale

        context.pose().scale(factor, factor)

        val screenCenterX = mc.window.width / (2f * customScale)
        val screenCenterY = mc.window.height / (2f * customScale)

        if (openAnim.isAnimating()) {
            val animScale = openAnim.get(0.8f, 1f)
            context.pose().translate(screenCenterX, screenCenterY)
            context.pose().scale(animScale, animScale)
            context.pose().translate(-screenCenterX, -screenCenterY)
        }

        val startX = screenCenterX - (GUI_WIDTH / 2f)
        val startY = screenCenterY - (GUI_HEIGHT / 2f)

        val catSizeInt = 32
        val catXInt = (startX + 20f).toInt()
        val catYInt = (startY + GUI_HEIGHT - 20f - 32f).toInt()

        val currentFrame = shizo.utils.clickgui.rendering.joyshit.GifLoader.getFrame(cat)
        if (currentFrame != null) {
            draw2DImage(context, currentFrame, catXInt, catYInt, catSizeInt, catSizeInt)
        }
        context.pose().popMatrix()

        super.render(context, mouseX, mouseY, deltaTicks)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val scale = ClickGUIModule.getStandardGuiScale()
        val mx = odinMouseX / scale
        val my = odinMouseY / scale

        val startX = (mc.window.width / (2f * scale)) - (GUI_WIDTH / 2f)
        val startY = (mc.window.height / (2f * scale)) - (GUI_HEIGHT / 2f)
        val contentX = startX + SIDEBAR_WIDTH
        val contentWidth = GUI_WIDTH - SIDEBAR_WIDTH
        val isSearching = SearchBar.currentSearch.isNotEmpty()

        if (!isSearching && my in (startY + 70f)..(startY + 110f) && mx > contentX && mx < contentX + contentWidth) {
            val scrollAmount = if (horizontalAmount != 0.0) horizontalAmount else verticalAmount
            targetTabScrollOffset += (scrollAmount.sign * 50f).toFloat()
        } else {
            targetScrollOffset += (verticalAmount.sign * 45f).toFloat()
            if (targetScrollOffset > 0f) targetScrollOffset = 0f
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        val scale = ClickGUIModule.getStandardGuiScale()
        val mx = odinMouseX / scale
        val my = odinMouseY / scale

        val startX = (mc.window.width / (2f * scale)) - (GUI_WIDTH / 2f)
        val startY = (mc.window.height / (2f * scale)) - (GUI_HEIGHT / 2f)
        val contentX = startX + SIDEBAR_WIDTH
        val contentWidth = GUI_WIDTH - SIDEBAR_WIDTH

        if (SearchBar.mouseClicked(mx, my, mouseButtonEvent)) return true

        if (mx in startX..(startX + SIDEBAR_WIDTH)) {
            var catY = startY + 65f
            val catSize = 32f
            val catYOffset  = startY + GUI_HEIGHT - 20f - catSize
            val editHudW = SIDEBAR_WIDTH - 30f
            val editHudH = 28f
            val editHudX = startX + 15f
            val editHudY = catYOffset - 12f - 10f - editHudH

            if (mx in editHudX..(editHudX + editHudW) && my in editHudY..(editHudY + editHudH) && mouseButtonEvent.button() == 0) {
                mc.setScreen(HudManager)
                return true
            }
            val sortedCategories = Category.categories.values.sortedBy { it.name }
            for (category in sortedCategories) {
                if (my in (catY - 8f)..(catY + 25f)) {
                    if (selectedCategory != category) {
                        selectedCategory = category
                        targetScrollOffset = 0f
                        scrollOffset = 0f
                        targetTabScrollOffset = 0f
                        tabScrollOffset = 0f
                        contentSwitchAnim.start()
                    }
                    return true
                }
                catY += 35f
            }
        }

        if (mx > contentX && mx < contentX + contentWidth) {
            val isSearching = SearchBar.currentSearch.isNotEmpty()

            if (!isSearching) {
                val modulesInCategory = ModuleManager.modulesByCategory[selectedCategory] ?: emptyList()
                val subcategories = modulesInCategory.map { it.subcategory }.distinct().sorted()

                var tabClickX = contentX + 25f + tabScrollOffset
                val tabY = startY + 70f
                for (sub in subcategories) {
                    val tabWidth = NVGRenderer.textWidth(sub.noControlCodes, 16f, NVGRenderer.defaultFont) + 20f
                    if (mx in tabClickX..(tabClickX + tabWidth) && my in tabY..(tabY + 26f) && mouseButtonEvent.button() == 0) {
                        if (selectedSubcategory != sub) {
                            selectedSubcategory = sub
                            targetScrollOffset = 0f
                            scrollOffset = 0f
                            contentSwitchAnim.start()
                        }
                        return true
                    }
                    tabClickX += tabWidth + 10f
                }
            }

            if (my > startY + 110f && my < startY + GUI_HEIGHT) {
                var contentY = startY + 120f + scrollOffset

                val activeModules = if (isSearching) {
                    ModuleManager.modulesByCategory.values.flatten().filter {
                        it.name.contains(SearchBar.currentSearch, ignoreCase = true) ||
                                it.description.contains(SearchBar.currentSearch, ignoreCase = true)
                    }
                } else {
                    val modulesInCategory = ModuleManager.modulesByCategory[selectedCategory] ?: emptyList()
                    modulesInCategory.filter { it.subcategory == selectedSubcategory }
                }

                for (module in activeModules) {
                    val allSettings = module.settings.values.filterIsInstance<RenderableSetting<*>>()
                    val isExpanded = expandedModules.contains(module) || module == ClickGUIModule
                    val visibleSettings = if (isExpanded) allSettings.filter { it.isVisible } else emptyList()

                    var settingsHeight = 0f
                    visibleSettings.forEach { settingsHeight += it.getHeight() }
                    val headerH = 50f
                    val moduleBoxHeight = if (isExpanded && visibleSettings.isNotEmpty()) headerH + 10f + settingsHeight else headerH

                    val boxX = contentX + 25f
                    val boxW = contentWidth - 50f

                    if (mx in boxX..(boxX + boxW) && my in contentY..(contentY + headerH)) {
                        if (mouseButtonEvent.button() == 0) {
                            module.toggle()
                            return true
                        } else if (mouseButtonEvent.button() == 1) {
                            if (expandedModules.contains(module)) expandedModules.remove(module)
                            else expandedModules.add(module)
                            return true
                        }
                    }

                    if (isExpanded && visibleSettings.isNotEmpty()) {
                        var settingY = contentY + headerH + 5f
                        for (setting in visibleSettings) {
                            if (setting.mouseClicked(mx, my, mouseButtonEvent)) return true
                            settingY += setting.getHeight()
                        }
                    }
                    contentY += moduleBoxHeight + 15f
                }
            }
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        SearchBar.mouseReleased()

        val isSearching = SearchBar.currentSearch.isNotEmpty()
        val activeModules = if (isSearching) {
            ModuleManager.modulesByCategory.values.flatten().filter {
                it.name.contains(SearchBar.currentSearch, ignoreCase = true) ||
                        it.description.contains(SearchBar.currentSearch, ignoreCase = true)
            }
        } else {
            val modules = ModuleManager.modulesByCategory[selectedCategory] ?: emptyList()
            modules.filter { it.subcategory == selectedSubcategory }
        }

        activeModules.forEach { mod ->
            if (expandedModules.contains(mod) || mod == ClickGUIModule) {
                mod.settings.values.filterIsInstance<RenderableSetting<*>>().filter { it.isVisible }.forEach { it.mouseReleased(mouseButtonEvent) }
            }
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        if (SearchBar.keyTyped(characterEvent)) {
            targetScrollOffset = 0f
            scrollOffset = 0f
            return true
        }

        val isSearching = SearchBar.currentSearch.isNotEmpty()
        val activeModules = if (isSearching) {
            ModuleManager.modulesByCategory.values.flatten().filter {
                it.name.contains(SearchBar.currentSearch, ignoreCase = true) ||
                        it.description.contains(SearchBar.currentSearch, ignoreCase = true)
            }
        } else {
            val modules = ModuleManager.modulesByCategory[selectedCategory] ?: emptyList()
            modules.filter { it.subcategory == selectedSubcategory }
        }

        for (module in activeModules) {
            if (expandedModules.contains(module) || module == ClickGUIModule) {
                for (setting in module.settings.values.filterIsInstance<RenderableSetting<*>>().filter { it.isVisible }) {
                    if (setting.keyTyped(characterEvent)) return true
                }
            }
        }
        return super.charTyped(characterEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (keyEvent.key == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose()
            return true
        }
        if (SearchBar.keyPressed(keyEvent)) {
            targetScrollOffset = 0f
            scrollOffset = 0f
            return true
        }

        val isSearching = SearchBar.currentSearch.isNotEmpty()
        val activeModules = if (isSearching) {
            ModuleManager.modulesByCategory.values.flatten().filter {
                it.name.contains(SearchBar.currentSearch, ignoreCase = true) ||
                        it.description.contains(SearchBar.currentSearch, ignoreCase = true)
            }
        } else {
            val modules = ModuleManager.modulesByCategory[selectedCategory] ?: emptyList()
            modules.filter { it.subcategory == selectedSubcategory }
        }

        for (module in activeModules) {
            if (expandedModules.contains(module) || module == ClickGUIModule) {
                for (setting in module.settings.values.filterIsInstance<RenderableSetting<*>>().filter { it.isVisible }) {
                    if (setting.keyPressed(keyEvent)) return true
                }
            }
        }
        return super.keyPressed(keyEvent)
    }

    override fun isPauseScreen(): Boolean = false
    override fun onClose() {
        ModuleManager.saveConfigurations()
        super.onClose()
    }
}