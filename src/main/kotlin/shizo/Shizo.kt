    package shizo

    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.SupervisorJob
    import kotlinx.coroutines.launch
    import net.fabricmc.api.ClientModInitializer
    import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
    import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry
    import net.minecraft.client.Minecraft
    import org.slf4j.LoggerFactory
    import shizo.commands.autoClickCommand
    import shizo.commands.cataCommand
    import shizo.commands.crateCommand
    import shizo.commands.croesusCommand
    import shizo.commands.dungeonHub
    import shizo.commands.floorJoinCommand
    import shizo.commands.jewCommand
    import shizo.commands.kuudraJoinCommand
    import shizo.commands.masterJoinCommand
    import shizo.commands.mainCommand
    import shizo.commands.rotationCommand
    import shizo.commands.soopyCommand
    import shizo.commands.termSimCommand
    import shizo.config.ModuleConfig
    import shizo.events.EventDispatcher
    import shizo.events.core.EventBus
    import shizo.module.impl.ModuleManager

    import shizo.module.impl.kuudra.general.ShopHelper.shopHelperCommands
    import shizo.module.impl.kuudra.general.partyfinder.KuudraPF.pfCommand
    import shizo.module.impl.kuudra.general.trackers.KuudraTrackers.trackerCommand
    import shizo.module.impl.kuudra.qolplus.autorend.AutoRend.autoRendCommand
    import shizo.module.impl.kuudra.qolplus.fireball.FireballUtils
    import shizo.utils.ItemSwapTacker
    import shizo.module.impl.kuudra.qolplus.vesuvius.NPCUtils
    import shizo.utils.RotationUtils
    import shizo.utils.clickgui.rendering.NVGSpecialRenderer
    import shizo.utils.clock.Executor
    import shizo.utils.handlers.CameraHandler
    import shizo.utils.handlers.EtherUtils
    import shizo.utils.handlers.SbStatTracker
    import shizo.utils.handlers.TickTasks
    import shizo.utils.network.WebUtils.postData
    import shizo.utils.renderUtils.renderUtils.CustomRenderLayer
    import shizo.utils.renderUtils.renderUtils.CustomRenderPipelines
    import shizo.utils.renderUtils.renderUtils.IrisCompatability
    import shizo.utils.renderUtils.renderUtils.ItemStateRenderer
    import shizo.utils.renderUtils.renderUtils.RenderBatchManager
    import shizo.utils.skyblock.kuudra.KuudraUtils
    import shizo.utils.skyblock.LocationUtils
    import shizo.utils.skyblock.dungeon.DungeonUtils
    import shizo.utils.skyblock.dungeon.LeapUtils
    import shizo.utils.skyblock.dungeon.ScanUtils
    import shizo.utils.skyblock.dungeon.DungeonListener
    import shizo.utils.skyblock.dungeon.terminals.TerminalUtils
    import java.io.File
    import kotlin.coroutines.EmptyCoroutineContext

    object Shizo : ClientModInitializer {
        val logger = LoggerFactory.getLogger("shizo")

        @JvmStatic
        val mc: Minecraft = Minecraft.getInstance()
        val player = Minecraft.getInstance().player
        val level = Minecraft.getInstance().level
        val version = "1.0.0"
        val moduleConfig: ModuleConfig = ModuleConfig("config.json")

        /**
         * Main config file location.
         * @see shizo.config.ModuleConfig
         */
        val configFile: File = File(mc.gameDirectory, "config/shizo/").apply {
            try {
                if (!exists()) mkdirs()
            } catch (e: Exception) {
                println("Could not create config folder $e")
                logger.error("Could not create config folder $e")
            }
        }
        const val MOD_ID = "shizo"
        val scope = CoroutineScope(SupervisorJob() + EmptyCoroutineContext)

        override fun onInitializeClient() {
            logger.info("Shizo client init!")
            ModuleManager.registerModules(moduleConfig)
            moduleConfig.load()
            ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
                arrayOf(
                    mainCommand,
                    autoClickCommand,
                    *floorJoinCommand,
                    *masterJoinCommand,
                    dungeonHub,
                    rotationCommand,
                    cataCommand,
                    soopyCommand,
                    termSimCommand,
                    crateCommand,
                    jewCommand,
                    shopHelperCommands,
                    autoRendCommand,
                    *kuudraJoinCommand,
                    croesusCommand,
                    pfCommand,
                    trackerCommand,
                ).forEach { commodore -> commodore.register(dispatcher) }
            }

            listOf(
                this, ModuleManager,
                TickTasks, LocationUtils, ScanUtils,
                DungeonListener, DungeonUtils, EventDispatcher,
                RenderBatchManager, TerminalUtils,
                LeapUtils, Executor, RotationUtils, KuudraUtils, NPCUtils, ItemSwapTacker, EtherUtils, CameraHandler,
                SbStatTracker, FireballUtils, CustomRenderLayer, CustomRenderPipelines, RenderBatchManager,
                IrisCompatability

            ).forEach { EventBus.subscribe(it) }

            SpecialGuiElementRegistry.register { context ->
                NVGSpecialRenderer(context.vertexConsumers())
            }

            SpecialGuiElementRegistry.register { context ->
                ItemStateRenderer(context.vertexConsumers())
            }
            // cba checking if this is needed for using the api so might as well no!
            val name = mc.user?.name?.takeIf { !it.matches(Regex("Player\\d{2,3}")) } ?: return
            scope.launch {
                postData("https://api.odtheking.com/tele/", """{"username": "$name", "version": "Fabric 0.1.5"}""")
            }
        }

    }