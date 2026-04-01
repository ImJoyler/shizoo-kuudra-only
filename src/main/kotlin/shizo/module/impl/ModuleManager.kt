package shizo.module.impl


//import shizo.module.impl.floor7.general.TickTimers
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.resources.ResourceLocation.fromNamespaceAndPath
import shizo.Shizo
import shizo.Shizo.mc
import shizo.clickgui.HudManager
import shizo.clickgui.settings.impl.HUDSetting
import shizo.clickgui.settings.impl.KeybindSetting
import shizo.config.ModuleConfig
import shizo.events.InputEvent
import shizo.events.core.on
import shizo.module.impl.ModuleManager.configs
import shizo.module.impl.cheats.AutoClicker
import shizo.module.impl.cheats.CancelInteract
import shizo.module.impl.dungeon.general.map.DungeonMap
import shizo.module.impl.floor7.BlessingDisplay
import shizo.module.impl.floor7.general.BossESP
import shizo.module.impl.floor7.general.DungeonAbilities
import shizo.module.impl.floor7.general.InvincibilityTimer
import shizo.module.impl.floor7.general.TickTimers
import shizo.module.impl.floor7.phase5.Debuff
import shizo.module.impl.floor7.terminals.LeapNotification
import shizo.module.impl.floor7.terminals.MelodyMessage
import shizo.module.impl.kuudra.general.*
import shizo.module.impl.kuudra.general.partyfinder.KuudraPF
import shizo.module.impl.kuudra.general.trackers.KuudraTrackers
import shizo.module.impl.kuudra.phasefour.BackboneTimer
import shizo.module.impl.kuudra.phaseone.CrateOverlay
import shizo.module.impl.kuudra.phaseone.NOPre
import shizo.module.impl.kuudra.phaseone.PearlWaypoints
import shizo.module.impl.kuudra.phaseone.SupplyHighlighter
import shizo.module.impl.kuudra.phaseone.prio.Priority
import shizo.module.impl.kuudra.phasethree.StunWaypoints
import shizo.module.impl.kuudra.phasetwo.BuildHUD
import shizo.module.impl.kuudra.phasetwo.Vengeance
import shizo.module.impl.kuudra.qolplus.AutoDirection
import shizo.module.impl.kuudra.qolplus.CannonClose
import shizo.module.impl.kuudra.qolplus.HollowWand
import shizo.module.impl.kuudra.qolplus.autorend.AutoRend
import shizo.module.impl.kuudra.qolplus.crateaura.CrateAura
import shizo.module.impl.kuudra.qolplus.fireball.Fireball
import shizo.module.impl.kuudra.qolplus.vesuvius.Jewing
import shizo.module.impl.misc.*
import shizo.module.impl.render.*
import java.io.File


/**
 * # Module Manager
 *
 * This object stores all [Modules][Module] and provides functionality to [HUDs][Module.HUD]
 */
object ModuleManager {

    /**
     * Map containing all modules in Odin,
     * where the key is the modules name in lowercase.
     */
    val modules: HashMap<String, Module> = linkedMapOf()

    /**
     * Map containing all modules under their category.
     */
    val modulesByCategory: HashMap<Category, ArrayList<Module>> = hashMapOf()

    /**
     * List of all configurations handled by Odin.
     */
    val configs: ArrayList<ModuleConfig> = arrayListOf()

    val keybindSettingsCache: ArrayList<KeybindSetting> = arrayListOf()
    val hudSettingsCache: ArrayList<HUDSetting> = arrayListOf()

    private val HUD_LAYER: ResourceLocation = fromNamespaceAndPath(Shizo.MOD_ID, "shizo_hud")

    init {
        registerModules(config = ModuleConfig(file = File(Shizo.configFile, "shizo-config.json")),
            // cheats
            AutoClicker,  CancelInteract,
            // dqol
            BlessingDisplay,  LeapNotification,

            // dungeon
            AutoGFS, DungeonAbilities,
             DungeonMap, InvincibilityTimer,
            shizo.module.impl.dungeon.croesus.Croesus, TickTimers,

            // floor7
            BossESP, Debuff, MelodyMessage,

            // Kuudra
            AutoDirection, AutoRend, BackboneTimer, BuildHUD, ByeByeLava,
            CannonClose, CrateOverlay, HollowWand, Jewing,
            KuudraInfo, KuudraPF, KuudraTickTimers, KuudraTrackers, LagTracker,
            NOPre, PearlWaypoints, Priority, ShopHelper,
            StunWaypoints, SupplyHighlighter, Vengeance,
            Fireball, CrateAura, AutoTap,
            // render
            ClickGUIModule, Etherwarp, NoCursorReset,
            PerformanceHUD, PlayerSize, Waypoints,
            //misc
            AutoJump, CommandKeybinds, CustomShortcuts, SlotBinds, WardrobeKeybinds, ZeroPingHS, CorpseESP, WorldScanner,
            GhostBlocks, SeeThrough
        )

        // hashmap, but would need to keep track when setting values change
        on<InputEvent> {
            for (setting in keybindSettingsCache) {
                if (setting.value.value == key.value) setting.onPress?.invoke()
            }
        }

        HudElementRegistry.attachElementBefore(VanillaHudElements.SLEEP, HUD_LAYER, ModuleManager::render)
    }

    /**
     * Registers modules to the [ModuleManager] and initializes them.
     *
     * @param config the config the [Module] is saved to,
     * it is recommended that each unique mod that uses this has its own config
     */
    fun registerModules(config: ModuleConfig, vararg modules: Module) {
        for (module in modules) {
            if (module.isDevModule && !FabricLoader.getInstance().isDevelopmentEnvironment) continue

            val lowercase = module.name.lowercase()
            config.modules[lowercase] = module
            this.modules[lowercase] = module
            this.modulesByCategory.getOrPut(module.category) { arrayListOf() }.add(module)

            module.key?.let { keybind ->
                val setting = KeybindSetting("Keybind", keybind, "Toggles this module.")
                setting.onPress = module::onKeybind
                module.registerSetting(setting)
            }

            for ((_, setting) in module.settings) {
                when (setting) {
                    is KeybindSetting -> keybindSettingsCache.add(setting)
                    is HUDSetting -> hudSettingsCache.add(setting)
                }
            }
        }
        configs.add(config)
        config.load()
    }

    /**
     * Loads all [configs] from disk, into the respective modules.
     */
    fun loadConfigurations() {
        for (config in configs) {
            config.load()
        }
    }

    /**
     * Saves all [configs] to disk, from the respective modules.
     */
    fun saveConfigurations() {
        for (config in configs) {
            config.save()
        }
    }

    fun render(context: GuiGraphics, tickCounter: DeltaTracker) {
        if (mc.level == null || mc.player == null || mc.screen == HudManager || mc.options.hideGui) return
        context.pose().pushMatrix()
        val sf = mc.window.guiScale
        context.pose().scale(1f / sf, 1f / sf)
        for (hudSettings in hudSettingsCache) {
            if (hudSettings.isEnabled) hudSettings.value.draw(context, false)
        }
        context.pose().popMatrix()
    }
}