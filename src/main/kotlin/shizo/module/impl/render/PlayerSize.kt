package shizo.module.impl.render

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import kotlinx.serialization.cbor.CborTag.URI
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.*
import shizo.events.TickEvent
import shizo.events.core.on
import shizo.module.impl.Module
import shizo.utils.Colors
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.model.SnifferModel
import net.minecraft.client.model.WardenModel
import net.minecraft.client.model.CopperGolemModel
import net.minecraft.client.model.CreakingModel
import net.minecraft.client.model.RavagerModel
import net.minecraft.client.model.GuardianModel
import net.minecraft.client.model.dragon.EnderDragonModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.CopperGolemRenderState
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState
import net.minecraft.client.renderer.entity.state.SnifferRenderState
import net.minecraft.client.renderer.entity.state.WardenRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.coppergolem.CopperGolem
import net.minecraft.world.entity.animal.sniffer.Sniffer
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.monster.ElderGuardian
import net.minecraft.world.entity.monster.Ravager
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.monster.creaking.Creaking
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import shizo.mixin.accessors.EntityRendererAccessor
import shizo.utils.renderUtils.CustomModelRenderer
import java.net.URI
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

object PlayerSize : Module(
    name = "Player Size",
    description = "Changes the size of players and can spawn personal dragons."
) {

    private const val RANDOMS_URL = "https://gist.githubusercontent.com/ImJoyler/9d54e9b658d8390a9ea79e37fd54c61c/raw"

    private val dev by BooleanSetting("Use Custom Settings", true, desc = "If enabled, uses your sliders below instead of hardcoded values.")

    private val devSize by BooleanSetting("Dev Size", true, desc = "Toggles client side dev size for your own player.")
    private val devSizeX by NumberSetting("Size X", 1f, -1f, 10f, 0.1f, desc = "X scale.").withDependency { devSize }
    private val devSizeY by NumberSetting("Size Y", 1f, -1f, 30f, 0.1f, desc = "Y scale.").withDependency { devSize }
    private val devSizeZ by NumberSetting("Size Z", 1f, -1f, 10f, 0.1f, desc = "Z scale.").withDependency { devSize }

    private val ttsSwingMap = HashMap<UUID, Int>()

    val wardrobeLauncher = WardrobeLauncherSetting()

    var zooMode by BooleanSetting("Render Multiple Mobs", false, "").withDependency { false }
    val devMob = SelectorSetting("Pet Mob", "None", arrayListOf("None", "Dragon", "Sniffer", "Warden", "Copper Golem", "Ravager", "Elder Guardian", "Creaking", "Breeze", "Frog", "Panda", "Llama", "Goat", "Parrot", "Glow Squid", "TTS"),"").withDependency { false }

    var hidePlayer by BooleanSetting("Hide Player", false,"").withDependency { false }
    var schizoMode by BooleanSetting("Schizo Mode", false, "").withDependency { false }
    var devRide by BooleanSetting("Ride Pet", false, "").withDependency { false }

    private val devRideHeight by NumberSetting("Ride Height", -2.2f, -15.0f, 15.0f, 0.1f, "").withDependency { devRide && devMob.value != 0 }

    var dragonToggle by BooleanSetting("Enable Dragon", false, "").withDependency { false }
    val dragonConfig = PetConfigSetting("Dragon Settings", 1).withDependency { false }

    var snifferToggle by BooleanSetting("Enable Sniffer", false, "").withDependency { false }
    var devSnifferBaby by BooleanSetting("Baby Sniffer", false, "").withDependency { false }
    val snifferConfig = PetConfigSetting("Sniffer Settings", 2).withDependency { false }

    var wardenToggle by BooleanSetting("Enable Warden", false, "").withDependency { false }
    val wardenConfig = PetConfigSetting("Warden Settings", 3).withDependency { false }

    var copperToggle by BooleanSetting("Enable Copper", false, "").withDependency { false }
    val copperConfig = PetConfigSetting("Copper Settings", 4).withDependency { false }

    var ravagerToggle by BooleanSetting("Enable Ravager", false, "").withDependency { false }
    val ravagerConfig = PetConfigSetting("Ravager Settings", 5).withDependency { false }

    var egToggle by BooleanSetting("Enable Elder Guardian", false, "").withDependency { false }
    val egConfig = PetConfigSetting("Guardian Settings", 6).withDependency { false }

    var creakingToggle by BooleanSetting("Enable Creaking", false, "").withDependency { false }
    val creakingConfig = PetConfigSetting("Creaking Settings", 7).withDependency { false }

    var breezeToggle by BooleanSetting("Enable Breeze", false, "").withDependency { false }
    val breezeConfig = PetConfigSetting("Breeze Settings", 8).withDependency { false }

    var frogToggle by BooleanSetting("Enable Frog", false, "").withDependency { false }
    val frogConfig = PetConfigSetting("Frog Settings", 9).withDependency { false }

    var pandaToggle by BooleanSetting("Enable Panda", false, "").withDependency { false }
    val pandaConfig = PetConfigSetting("Panda Settings", 10).withDependency { false }

    var llamaToggle by BooleanSetting("Enable Llama", false, "").withDependency { false }
    val llamaConfig = PetConfigSetting("Llama Settings", 11).withDependency { false }

    var goatToggle by BooleanSetting("Enable Goat", false, "").withDependency { false }
    val goatConfig = PetConfigSetting("Goat Settings", 12).withDependency { false }

    var parrotToggle by BooleanSetting("Enable Parrot", false, "").withDependency { false }
    val parrotConfig = PetConfigSetting("Parrot Settings", 13).withDependency { false }

    var glowSquidToggle by BooleanSetting("Enable Glow Squid", false, "").withDependency { false }
    val glowSquidConfig = PetConfigSetting("Glow Squid Settings", 14).withDependency { false }

    var ttsToggle by BooleanSetting("Enable TTS", false, "").withDependency { false }
    val ttsConfig = PetConfigSetting("TTS Settings", 15).withDependency { false }

    var randoms: HashMap<String, RandomPlayer> = HashMap()
    private val copperSwingMap = HashMap<java.util.UUID, Int>()

    private var dummyDragon: EnderDragon? = null
    private var dragonModel: EnderDragonModel? = null
    private val DRAGON_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png")

    private var dummySniffer: Sniffer? = null
    private var snifferModel: SnifferModel? = null
    private var snifferBabyModel: SnifferModel? = null
    private val SNIFFER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/sniffer/sniffer.png")

    private var dummyWarden: Warden? = null
    private var wardenModel: WardenModel? = null
    private val WARDEN_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/warden/warden.png")

    private var dummyCopper: CopperGolem? = null
    private var copperModel: CopperGolemModel? = null
    private val COPPER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/copper_golem/copper_golem.png")
    private val COPPER_EYES_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/copper_golem/copper_golem_eyes.png")

    private var dummyRavager: Ravager? = null
    private var ravagerModel: RavagerModel? = null
    private val RAVAGER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/illager/ravager.png")

    private var dummyEG: ElderGuardian? = null
    private var egModel: GuardianModel? = null
    private val EG_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/guardian_elder.png")

    private var dummyC: Creaking? = null
    private var cModel: CreakingModel? = null
    private val CREAKING_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/creaking/creaking.png")

    private var dummyBreeze: net.minecraft.world.entity.monster.breeze.Breeze? = null
    private var breezeModel: net.minecraft.client.model.BreezeModel? = null
    private var breezeWindModel: net.minecraft.client.model.BreezeModel? = null
    private val BREEZE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze.png")
    private val BREEZE_WIND_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_wind.png")

    private var dummyFrog: net.minecraft.world.entity.animal.frog.Frog? = null
    private var frogModel: net.minecraft.client.model.FrogModel? = null
    private val FROG_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/frog/temperate_frog.png")

    private var dummyPanda: net.minecraft.world.entity.animal.Panda? = null
    private var pandaModel: net.minecraft.client.model.PandaModel? = null
    private val PANDA_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/panda/panda.png")

    private var dummyLlama: net.minecraft.world.entity.animal.horse.Llama? = null
    private var llamaModel: net.minecraft.client.model.LlamaModel? = null
    private val LLAMA_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/llama/creamy.png")

    private var dummyGoat: net.minecraft.world.entity.animal.goat.Goat? = null
    private var goatModel: net.minecraft.client.model.GoatModel? = null
    private val GOAT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/goat/goat.png")

    private var dummyParrot: net.minecraft.world.entity.animal.Parrot? = null
    private var parrotModel: net.minecraft.client.model.ParrotModel? = null
    private val PARROT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/parrot/parrot_red_blue.png")

    private var dummyGlowSquid: net.minecraft.world.entity.GlowSquid? = null
    private var glowSquidModel: net.minecraft.client.model.SquidModel? = null
    private val GLOW_SQUID_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/squid/glow_squid.png")


    data class RandomPlayer(
        @SerializedName("uuid") val uuid: String,
        @SerializedName("CustomName") val customName: String? = null,
        @SerializedName("DevName") val name: String? = null,
        @SerializedName("IsDev") val isDev: Boolean? = false,
        @SerializedName("WingsColor") val wingsColor: List<Int> = listOf(255, 255, 255),
        @SerializedName("Size") val scale: List<Float> = listOf(1f, 1f, 1f),
        @SerializedName("Wings") val wings: Boolean = false,
        @SerializedName("Ride") val ride: Boolean = false,
        @SerializedName("RideHeight") val rideHeight: Float = -2.2f,

        @SerializedName("Dragon") val dragon: Boolean = false,
        @SerializedName("DragonSize") val dragonSize: Float = 0.4f,
        @SerializedName("DragonPitch") val dragonPitch: Float = 20f,
        @SerializedName("DragonDist") val dragonDist: Float = 1.5f,
        @SerializedName("DragonSide") val dragonSide: Float = 0.0f,
        @SerializedName("DragonHeight") val dragonHeight: Float = 0.0f,

        @SerializedName("Sniffer") val sniffer: Boolean = false,
        @SerializedName("SnifferBaby") val snifferBaby: Boolean = false,
        @SerializedName("SnifferSize") val snifferSize: Float = 0.4f,
        @SerializedName("SnifferPitch") val snifferPitch: Float = 0f,
        @SerializedName("SnifferDist") val snifferDist: Float = 1.5f,
        @SerializedName("SnifferSide") val snifferSide: Float = 0.0f,
        @SerializedName("SnifferHeight") val snifferHeight: Float = 0.0f,

        @SerializedName("Warden") val warden: Boolean = false,
        @SerializedName("WardenSize") val wardenSize: Float = 0.4f,
        @SerializedName("WardenSide") val wardenSide: Float = 0.0f,
        @SerializedName("WardenPitch") val wardenPitch: Float = 0f,
        @SerializedName("WardenDist") val wardenDist: Float = 1.5f,
        @SerializedName("WardenHeight") val wardenHeight: Float = 0.0f,

        @SerializedName("Copper") val copper: Boolean = false,
        @SerializedName("CopperSize") val copperSize: Float = 0.4f,
        @SerializedName("CopperSide") val copperSide: Float = 0.0f,
        @SerializedName("CopperPitch") val copperPitch: Float = 0f,
        @SerializedName("CopperDist") val copperDist: Float = 1.5f,
        @SerializedName("CopperHeight") val copperHeight: Float = 0.0f,

        @SerializedName("Ravager") val ravager: Boolean = false,
        @SerializedName("RavagerSize") val ravagerSize: Float = 1.0f,
        @SerializedName("RavagerSide") val ravagerSide: Float = 0.0f,
        @SerializedName("RavagerPitch") val ravagerPitch: Float = 0f,
        @SerializedName("RavagerDist") val ravagerDist: Float = 0.0f,
        @SerializedName("RavagerHeight") val ravagerHeight: Float = 0.0f,

        @SerializedName("EG") val eg: Boolean = false,
        @SerializedName("EGSize") val egSize: Float = 1.0f,
        @SerializedName("EGSide") val egSide: Float = 0.0f,
        @SerializedName("EGPitch") val egPitch: Float = 0f,
        @SerializedName("EGDist") val egDist: Float = 0.0f,
        @SerializedName("EGHeight") val egHeight: Float = 0.0f,

        @SerializedName("Creaking") val creaking: Boolean = false,
        @SerializedName("CreakingSize") val creakingSize: Float = 1.0f,
        @SerializedName("CreakingSide") val creakingSide: Float = 0.0f,
        @SerializedName("CreakingPitch") val creakingPitch: Float = 0f,
        @SerializedName("CreakingDist") val creakingDist: Float = 0.0f,
        @SerializedName("CreakingHeight") val creakingHeight: Float = 0.0f,

        @SerializedName("Breeze") val breeze: Boolean = false,
        @SerializedName("BreezeSize") val breezeSize: Float = 1.0f,
        @SerializedName("BreezeSide") val breezeSide: Float = 0.0f,
        @SerializedName("BreezePitch") val breezePitch: Float = 0f,
        @SerializedName("BreezeDist") val breezeDist: Float = 0.0f,
        @SerializedName("BreezeHeight") val breezeHeight: Float = 0.0f,

        @SerializedName("Frog") val frog: Boolean = false,
        @SerializedName("FrogSize") val frogSize: Float = 1.0f,
        @SerializedName("FrogSide") val frogSide: Float = 0.0f,
        @SerializedName("FrogPitch") val frogPitch: Float = 0f,
        @SerializedName("FrogDist") val frogDist: Float = 0.0f,
        @SerializedName("FrogHeight") val frogHeight: Float = 0.0f,

        @SerializedName("Panda") val panda: Boolean = false,
        @SerializedName("PandaSize") val pandaSize: Float = 1.0f,
        @SerializedName("PandaSide") val pandaSide: Float = 0.0f,
        @SerializedName("PandaPitch") val pandaPitch: Float = 0f,
        @SerializedName("PandaDist") val pandaDist: Float = 0.0f,
        @SerializedName("PandaHeight") val pandaHeight: Float = 0.0f,

        @SerializedName("Llama") val llama: Boolean = false,
        @SerializedName("LlamaSize") val llamaSize: Float = 1.0f,
        @SerializedName("LlamaSide") val llamaSide: Float = 0.0f,
        @SerializedName("LlamaPitch") val llamaPitch: Float = 0f,
        @SerializedName("LlamaDist") val llamaDist: Float = 0.0f,
        @SerializedName("LlamaHeight") val llamaHeight: Float = 0.0f,

        @SerializedName("Goat") val goat: Boolean = false,
        @SerializedName("GoatSize") val goatSize: Float = 1.0f,
        @SerializedName("GoatSide") val goatSide: Float = 0.0f,
        @SerializedName("GoatPitch") val goatPitch: Float = 0f,
        @SerializedName("GoatDist") val goatDist: Float = 0.0f,
        @SerializedName("GoatHeight") val goatHeight: Float = 0.0f,

        @SerializedName("Parrot") val parrot: Boolean = false,
        @SerializedName("ParrotSize") val parrotSize: Float = 1.0f,
        @SerializedName("ParrotSide") val parrotSide: Float = 0.0f,
        @SerializedName("ParrotPitch") val parrotPitch: Float = 0f,
        @SerializedName("ParrotDist") val parrotDist: Float = 0.0f,
        @SerializedName("ParrotHeight") val parrotHeight: Float = 0.0f,

        @SerializedName("GlowSquid") val glowSquid: Boolean = false,
        @SerializedName("GlowSquidSize") val glowSquidSize: Float = 1.0f,
        @SerializedName("GlowSquidSide") val glowSquidSide: Float = 0.0f,
        @SerializedName("GlowSquidPitch") val glowSquidPitch: Float = 0f,
        @SerializedName("GlowSquidDist") val glowSquidDist: Float = 0.0f,
        @SerializedName("GlowSquidHeight") val glowSquidHeight: Float = 0.0f,

        @SerializedName("TTS") val tts: Boolean = false,
        @SerializedName("TTSSize") val ttsSize: Float = 1.0f,
        @SerializedName("TTSSide") val ttsSide: Float = 0.0f,
        @SerializedName("TTSPitch") val ttsPitch: Float = 0f,
        @SerializedName("TTSYaw") val ttsYaw: Float = 0f,
        @SerializedName("TTSDist") val ttsDist: Float = 0.0f,
        @SerializedName("TTSHeight") val ttsHeight: Float = 0.0f
    )

    init {
        this.registerSetting(wardrobeLauncher)
        this.registerSetting(devMob)
        this.registerSetting(dragonConfig)
        this.registerSetting(snifferConfig)
        this.registerSetting(wardenConfig)
        this.registerSetting(copperConfig)
        this.registerSetting(ravagerConfig)
        this.registerSetting(egConfig)
        this.registerSetting(creakingConfig)
        this.registerSetting(breezeConfig)
        this.registerSetting(frogConfig)
        this.registerSetting(pandaConfig)
        this.registerSetting(llamaConfig)
        this.registerSetting(goatConfig)
        this.registerSetting(parrotConfig)
        this.registerSetting(glowSquidConfig)
        this.registerSetting(ttsConfig)

        thread {
            try {
                val bustUrl = "$RANDOMS_URL?t=${System.currentTimeMillis()}"
                val connection = URI(bustUrl).toURL().openConnection()
                connection.setRequestProperty("Cache-Control", "no-cache")
                val response = connection.getInputStream().bufferedReader().readText()
                val parsedArray = Gson().fromJson(response, Array<RandomPlayer>::class.java)
                parsedArray.forEach { player ->
                    randoms[player.uuid.lowercase()] = player
                }
                shizo.Shizo.logger.info("Loaded ${parsedArray.size} custom player cosmetics via UUID!")
            } catch (e: Exception) {
                shizo.Shizo.logger.error("Failed to load randoms JSON from Gist", e)
            }
        }
//
//        randoms["__cby"] = goats("__cby", 0.02f, sniffer = true, sDist = -1.5f, sSize = 1.0f, sBaby=true)
//        randoms["___cby"] = goats("___cby", 0.02f, wings = false,dragon = false)
//        randoms["ImJoyless"] = goats("ImJoyless", 0.02f, dragon = true, dSize = 0.2f, dPitch = 45f, dDist = 1.0f, dHeight = 0.5f)
//        randoms["NKairo"] = goats("NKairo", 0.01f, wings = false, creaking = true, crSize = 1f)
//        randoms["Major_TooM"] = goats("Major_TooM", 0.02f, wings = false, copper = true, cSize = 1.5f, cDist = -0.5f)
//        randoms["TactHaelStrom"] = goats("TactHaelStrom", 0.02f, warden = true, wSize = 0.75f, wDist = -0.5f )
//        randoms["Berefts"] = goats("Berefts", 0.02f, eg = true, egSize = 0.5f )
//        randoms["Syntocx"] = goats("Syntocx", 0.02f,sniffer = true, sBaby = true, sPitch = -180f, sSize = 0.5f, sHeight = 1f)
//        randoms["pathwalker25"] = goats("pathwalker25", 0.02f,breeze = true, brSize = 1f, sHeight = 1f)
//        randoms["JulienLovesMort"] = goats("JulienLovesMort", 0.02f, frog = true, fSize = 2f, fHeight = 1f)
//        randoms["ATobii"] = goats("ATobii", 0.02f, ravager = true, rSize = 0.6f, rHeight = 1f)

        ClientTickEvents.START_CLIENT_TICK.register {
            if (mc.level == null) return@register
            try {
                if (dummyDragon == null) dummyDragon = EnderDragon(EntityType.ENDER_DRAGON, mc.level!!)
                if (dragonModel == null) dragonModel = EnderDragonModel(mc.entityModels.bakeLayer(ModelLayers.ENDER_DRAGON))
                dummyDragon?.let { it.oFlapTime = it.flapTime; it.flapTime += 0.1f; if (it.flapTime < 0f) it.flapTime = 0f; it.flightHistory.record(it.y, it.yRot) }

                if (dummySniffer == null) dummySniffer = Sniffer(EntityType.SNIFFER, mc.level!!)
                if (snifferModel == null) {
                    snifferModel = SnifferModel(mc.entityModels.bakeLayer(ModelLayers.SNIFFER))
                    snifferBabyModel = SnifferModel(mc.entityModels.bakeLayer(ModelLayers.SNIFFER_BABY))
                }
                dummySniffer?.tickCount = (dummySniffer?.tickCount ?: 0) + 1

                if (dummyWarden == null) dummyWarden = Warden(EntityType.WARDEN, mc.level!!)
                if (wardenModel == null) wardenModel = WardenModel(mc.entityModels.bakeLayer(ModelLayers.WARDEN))
                dummyWarden?.tickCount = (dummyWarden?.tickCount ?: 0) + 1

                if (dummyCopper == null) dummyCopper = CopperGolem(EntityType.COPPER_GOLEM, mc.level!!)
                if (copperModel == null) copperModel = CopperGolemModel(mc.entityModels.bakeLayer(ModelLayers.COPPER_GOLEM))
                dummyCopper?.tickCount = (dummyCopper?.tickCount ?: 0) + 1

                if (dummyRavager == null) dummyRavager = Ravager(EntityType.RAVAGER, mc.level!!)
                if (ravagerModel == null) ravagerModel = RavagerModel(mc.entityModels.bakeLayer(ModelLayers.RAVAGER))
                dummyRavager?.tickCount = (dummyRavager?.tickCount ?: 0) + 1


                if (dummyEG == null) dummyEG = ElderGuardian(EntityType.ELDER_GUARDIAN, mc.level!!)
                if (egModel == null) egModel = GuardianModel(mc.entityModels.bakeLayer(ModelLayers.ELDER_GUARDIAN))
                dummyEG?.tickCount = (dummyEG?.tickCount ?: 0) + 1

                if (dummyC == null) dummyC = Creaking(EntityType.CREAKING, mc.level!!)
                if (cModel == null) cModel = CreakingModel(mc.entityModels.bakeLayer(ModelLayers.CREAKING))
                dummyC?.tickCount = (dummyC?.tickCount ?: 0) + 1

                if (dummyBreeze == null) dummyBreeze = net.minecraft.world.entity.monster.breeze.Breeze(EntityType.BREEZE, mc.level!!)
                if (breezeModel == null) {
                    breezeModel = net.minecraft.client.model.BreezeModel(mc.entityModels.bakeLayer(ModelLayers.BREEZE))
                    breezeWindModel = net.minecraft.client.model.BreezeModel(mc.entityModels.bakeLayer(ModelLayers.BREEZE_WIND))
                }
                dummyBreeze?.tickCount = (dummyBreeze?.tickCount ?: 0) + 1

                if (dummyFrog == null) dummyFrog = net.minecraft.world.entity.animal.frog.Frog(EntityType.FROG, mc.level!!)
                if (frogModel == null) frogModel = net.minecraft.client.model.FrogModel(mc.entityModels.bakeLayer(ModelLayers.FROG))
                dummyFrog?.tickCount = (dummyFrog?.tickCount ?: 0) + 1

                if (dummyPanda == null) dummyPanda = net.minecraft.world.entity.animal.Panda(EntityType.PANDA, mc.level!!)
                if (pandaModel == null) pandaModel = net.minecraft.client.model.PandaModel(mc.entityModels.bakeLayer(ModelLayers.PANDA))
                dummyPanda?.tickCount = (dummyPanda?.tickCount ?: 0) + 1

                if (dummyLlama == null) dummyLlama = net.minecraft.world.entity.animal.horse.Llama(EntityType.LLAMA, mc.level!!)
                if (llamaModel == null) llamaModel = net.minecraft.client.model.LlamaModel(mc.entityModels.bakeLayer(ModelLayers.LLAMA))
                dummyLlama?.tickCount = (dummyLlama?.tickCount ?: 0) + 1

                if (dummyGoat == null) dummyGoat = net.minecraft.world.entity.animal.goat.Goat(EntityType.GOAT, mc.level!!)
                if (goatModel == null) goatModel = net.minecraft.client.model.GoatModel(mc.entityModels.bakeLayer(ModelLayers.GOAT))
                dummyGoat?.tickCount = (dummyGoat?.tickCount ?: 0) + 1

                if (dummyParrot == null) dummyParrot = net.minecraft.world.entity.animal.Parrot(EntityType.PARROT, mc.level!!)
                if (parrotModel == null) parrotModel = net.minecraft.client.model.ParrotModel(mc.entityModels.bakeLayer(ModelLayers.PARROT))
                dummyParrot?.tickCount = (dummyParrot?.tickCount ?: 0) + 1

                if (dummyGlowSquid == null) dummyGlowSquid = net.minecraft.world.entity.GlowSquid(EntityType.GLOW_SQUID, mc.level!!)
                if (glowSquidModel == null) glowSquidModel = net.minecraft.client.model.SquidModel(mc.entityModels.bakeLayer(ModelLayers.GLOW_SQUID))
                dummyGlowSquid?.tickCount = (dummyGlowSquid?.tickCount ?: 0) + 1

//                if (dummyTts == null) dummyTts = net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE, mc.level!!)
//                if (ttsModel == null) {
//                    ttsModel = TtsModel(TtsModel.createBodyLayer().bakeRoot())
//                }
//                dummyTts?.tickCount = (dummyTts?.tickCount ?: 0) + 1

            } catch (_: Exception) {}
        }

        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            if (mc.level == null) return@register
            for (player in mc.level!!.players()) {
                val isMe = player == mc.player
//                val hd = randoms[player.gameProfile.name]
                val hd = randoms[player.uuid.toString().lowercase()]

                if (isMe && enabled && dev) {
                    if ((!zooMode && devMob.value == 1) || (zooMode && dragonToggle)) {
                        val d = dragonConfig.value
                        renderDragon(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 2) || (zooMode && snifferToggle)) {
                        val d = snifferConfig.value
                        renderSniffer(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side, devSnifferBaby)
                    }
                    if ((!zooMode && devMob.value == 3) || (zooMode && wardenToggle)) {
                        val d = wardenConfig.value
                        renderWarden(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 4) || (zooMode && copperToggle)) {
                        val d = copperConfig.value
                        renderCopper(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 5) || (zooMode && ravagerToggle)) {
                        val d = ravagerConfig.value
                        renderRavager(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 6) || (zooMode && egToggle)) {
                        val d = egConfig.value
                        renderEG(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 7) || (zooMode && creakingToggle)) {
                        val d = creakingConfig.value
                        renderCreaking(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 8) || (zooMode && breezeToggle)) {
                        val d = breezeConfig.value
                        renderBreeze(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 9) || (zooMode && frogToggle)) {
                        val d = frogConfig.value
                        renderFrog(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 10) || (zooMode && pandaToggle)) {
                        val d = pandaConfig.value
                        renderPanda(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 11) || (zooMode && llamaToggle)) {
                        val d = llamaConfig.value
                        renderLlama(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 12) || (zooMode && goatToggle)) {
                        val d = goatConfig.value
                        renderGoat(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 13) || (zooMode && parrotToggle)) {
                        val d = parrotConfig.value
                        renderParrot(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 14) || (zooMode && glowSquidToggle)) {
                        val d = glowSquidConfig.value
                        renderGlowSquid(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                    if ((!zooMode && devMob.value == 15) || (zooMode && ttsToggle)) {
                        val d = ttsConfig.value
                        renderTts(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side)
                    }
                } else if (hd != null) {
                    if (hd.dragon) renderDragon(player, context, hd.dragonSize, hd.dragonPitch, 0f, hd.dragonDist, hd.dragonHeight, hd.dragonSide)
                    if (hd.sniffer) renderSniffer(player, context, hd.snifferSize, hd.snifferPitch, 0f, hd.snifferDist, hd.snifferHeight, hd.snifferSide, hd.snifferBaby)
                    if (hd.warden) renderWarden(player, context, hd.wardenSize, hd.wardenPitch, 0f, hd.wardenDist, hd.wardenHeight, hd.wardenSide)
                    if (hd.copper) renderCopper(player, context, hd.copperSize, hd.copperPitch, 0f, hd.copperDist, hd.copperHeight, hd.copperSide)
                    if (hd.ravager) renderRavager(player, context, hd.ravagerSize, hd.ravagerPitch, 0f, hd.ravagerDist, hd.ravagerHeight, hd.ravagerSide)
                    if (hd.eg) renderEG(player, context, hd.egSize, hd.egPitch, 0f, hd.egDist, hd.egHeight, hd.egSide)
                    if (hd.creaking) renderCreaking(player, context, hd.creakingSize, hd.creakingPitch, 0f, hd.creakingDist, hd.creakingHeight, hd.creakingSide)
                    if (hd.breeze) renderBreeze(player, context, hd.breezeSize, hd.breezePitch, 0f, hd.breezeDist, hd.breezeHeight, hd.breezeSide)
                    if (hd.frog) renderFrog(player, context, hd.frogSize, hd.frogPitch, 0f, hd.frogDist, hd.frogHeight, hd.frogSide)
                    if (hd.panda) renderPanda(player, context, hd.pandaSize, hd.pandaPitch, 0f, hd.pandaDist, hd.pandaHeight, hd.pandaSide)
                    if (hd.llama) renderLlama(player, context, hd.llamaSize, hd.llamaPitch, 0f, hd.llamaDist, hd.llamaHeight, hd.llamaSide)
                    if (hd.goat) renderGoat(player, context, hd.goatSize, hd.goatPitch, 0f, hd.goatDist, hd.goatHeight, hd.goatSide)
                    if (hd.parrot) renderParrot(player, context, hd.parrotSize, hd.parrotPitch, 0f, hd.parrotDist, hd.parrotHeight, hd.parrotSide)
                    if (hd.glowSquid) renderGlowSquid(player, context, hd.glowSquidSize, hd.glowSquidPitch, 0f, hd.glowSquidDist, hd.glowSquidHeight, hd.glowSquidSide)
                    if (hd.tts) renderTts(player, context, hd.ttsSize, hd.ttsPitch, hd.ttsYaw, hd.ttsDist, hd.ttsHeight, hd.ttsSide)
                }
            }
        }
    }
    private fun renderTts(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val path = CustomModelRenderer.ALL_MODEL_PATHS["Tung Tung Sahur"] ?: return
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val isMe = player == mc.player
        if (isMe && mc.options.cameraType.isFirstPerson) return
        val uuid = player.uuid
        if (player.swingTime == 1) {
            val lastStart = ttsSwingMap[uuid] ?: -100
            if (player.tickCount - lastStart > 20) ttsSwingMap[uuid] = player.tickCount
        }

        val animStartTick = ttsSwingMap[uuid]
        var hitProgress = 0f

        if (animStartTick != null && player.tickCount - animStartTick <= 20) {
            val elapsedTicks = (player.tickCount - animStartTick) + partialTick
            hitProgress = (elapsedTicks / 8f).coerceIn(0f, 1f)
        }

        val bodyRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)
        val headRot = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot)
        val yawDiff = Mth.wrapDegrees(headRot - bodyRot)
        val smoothedYaw = bodyRot + (yawDiff * 0.6f)

        val bufferSource = ctx.consumers() as? net.minecraft.client.renderer.MultiBufferSource ?: return

        val matrix = ctx.matrices()
        matrix.pushPose()

        val yawRad = Math.toRadians(bodyRot.toDouble())
        val distH = size * dist
        val distSide = size * side
        val rX = Mth.lerp(partialTick.toDouble(), player.xo, player.x) + (-sin(yawRad) * distH) + (-cos(yawRad) * distSide)
        val rY = Mth.lerp(partialTick.toDouble(), player.yo, player.y) + height
        val rZ = Mth.lerp(partialTick.toDouble(), player.zo, player.z) + (cos(yawRad) * distH) + (-sin(yawRad) * distSide)

        val camPos = mc.gameRenderer.mainCamera.position
        matrix.translate(rX - camPos.x, rY - camPos.y, rZ - camPos.z)

        CustomModelRenderer.submit(
            modelPath = path,
            poseStack = matrix,
            collector = bufferSource,
            bodyRot = smoothedYaw,
            lightCoords = 15728880,
            walkAnimSpeed = player.walkAnimation.speed(partialTick),
            walkAnimPos = player.walkAnimation.position(partialTick) * 0.001f,

            hitAnimProgress = hitProgress,

            scale = size,
            offsetX = side,
            offsetY = height,
            offsetZ = dist,
            rotationX = 0f,
            rotationY = yaw,
            rotationZ = 0f
        )

        matrix.popPose()
    }    private inline fun renderPetWrapper(
        player: Player, context: WorldRenderContext,
        dummyEntity: net.minecraft.world.entity.Entity,
        scale: Float, pitch: Float, yaw: Float, distMult: Float, heightOff: Float, sideOff: Float,
        renderBlock: (PoseStack, Float) -> Unit
    ) {
        val isMe = player == mc.player
        if (isMe && mc.options.cameraType.isFirstPerson) return

        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val lerpBodyRot = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot)
        val yawRad = Math.toRadians(lerpBodyRot.toDouble())

        val distH = scale * distMult
        val distSide = scale * sideOff
        val rX = Mth.lerp(partialTick.toDouble(), player.xo, player.x) + (-sin(yawRad) * distH) + (-cos(yawRad) * distSide)
        val rY = Mth.lerp(partialTick.toDouble(), player.yo, player.y) + heightOff
        val rZ = Mth.lerp(partialTick.toDouble(), player.zo, player.z) + (cos(yawRad) * distH) + (-sin(yawRad) * distSide)

        val desiredHeadRot = Mth.lerp(partialTick, player.yHeadRotO, player.yHeadRot)
        val desiredPitch = Mth.lerp(partialTick, player.xRotO, player.xRot)

        dummyEntity.setPos(rX, rY, rZ)
        dummyEntity.yRot = lerpBodyRot; dummyEntity.yRotO = lerpBodyRot

        if (dummyEntity is net.minecraft.world.entity.LivingEntity) {
            dummyEntity.yBodyRot = lerpBodyRot; dummyEntity.yBodyRotO = lerpBodyRot
            dummyEntity.yHeadRotO = if (schizoMode) 0f else desiredHeadRot
            dummyEntity.xRotO = if (schizoMode) 0f else desiredPitch
            dummyEntity.yHeadRot = desiredHeadRot; dummyEntity.xRot = desiredPitch
        }

        val matrix = context.matrices()
        matrix.pushPose()
        val camPos = mc.gameRenderer.mainCamera.position
        matrix.translate(rX - camPos.x, rY - camPos.y, rZ - camPos.z)

        matrix.mulPose(Axis.YP.rotationDegrees(-lerpBodyRot + 180f))
        matrix.mulPose(Axis.YP.rotationDegrees(yaw))
        matrix.mulPose(Axis.XP.rotationDegrees(pitch))

        matrix.scale(scale, scale, scale)
        matrix.scale(-1.0F, -1.0F, 1.0F)
        matrix.translate(0.0F, -1.501F, 0.0F)

        try { renderBlock(matrix, partialTick) } catch (_: Exception) {}
        matrix.popPose()
    }

    private inline fun <E : net.minecraft.world.entity.Entity, S : net.minecraft.client.renderer.entity.state.EntityRenderState, M : net.minecraft.client.model.EntityModel<S>> renderPetState(
        player: Player, dummyEntity: E, model: M, texture: ResourceLocation, matrix: PoseStack, partialTick: Float, stateModifier: (S) -> Unit = {}
    ) {
        val renderer = mc.entityRenderDispatcher.getRenderer(dummyEntity)
        @Suppress("UNCHECKED_CAST")
        val accessor = renderer as? EntityRendererAccessor<E, S> ?: return
        val state = accessor.callCreateRenderState()
        accessor.callExtractRenderState(dummyEntity, state, partialTick)

        if (state is net.minecraft.client.renderer.entity.state.LivingEntityRenderState) {
            state.walkAnimationPos = player.walkAnimation.position(partialTick)
            state.walkAnimationSpeed = player.walkAnimation.speed(partialTick)
        }

        stateModifier(state)
        model.setupAnim(state)

        val buffers = mc.renderBuffers().bufferSource()
        val vertexConsumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture))
        model.renderToBuffer(matrix, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY, -1)
        buffers.endBatch()
    }

    private fun renderWarden(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val warden = dummyWarden ?: return; val model = wardenModel ?: return
        renderPetWrapper(player, ctx, warden, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            if (player.swingTime > 0) warden.attackAnimationState.startIfStopped(warden.tickCount) else warden.attackAnimationState.stop()
            renderPetState<Warden, WardenRenderState, WardenModel>(player, warden, model, WARDEN_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderCreaking(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val creaking = dummyC ?: return; val model = cModel ?: return
        renderPetWrapper(player, ctx, creaking, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            if (player.swingTime > 0) creaking.attackAnimationState.startIfStopped(creaking.tickCount) else creaking.attackAnimationState.stop()
            renderPetState<Creaking, net.minecraft.client.renderer.entity.state.CreakingRenderState, CreakingModel>(player, creaking, model, CREAKING_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderRavager(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val ravager = dummyRavager ?: return; val model = ravagerModel ?: return
        renderPetWrapper(player, ctx, ravager, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<Ravager, net.minecraft.client.renderer.entity.state.RavagerRenderState, RavagerModel>(player, ravager, model, RAVAGER_TEXTURE, matrix, partialTick) { state ->
                state.attackTicksRemaining = if (player.swingTime > 0) 10f - player.swingTime.toFloat() else 0f
            }
        }
    }

    private fun renderEG(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val eg = dummyEG ?: return; val model = egModel ?: return
        renderPetWrapper(player, ctx, eg, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<ElderGuardian, net.minecraft.client.renderer.entity.state.GuardianRenderState, GuardianModel>(player, eg, model, EG_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderDragon(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val dragon = dummyDragon ?: return; val model = dragonModel ?: return
        renderPetWrapper(player, ctx, dragon, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<EnderDragon, EnderDragonRenderState, EnderDragonModel>(player, dragon, model, DRAGON_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderSniffer(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float, isBaby: Boolean) {
        val sniffer = dummySniffer ?: return; val model = if (isBaby) snifferBabyModel ?: return else snifferModel ?: return
        sniffer.walkAnimation.setSpeed(player.walkAnimation.speed())
        sniffer.age = if (isBaby) -100 else 0
        renderPetWrapper(player, ctx, sniffer, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<Sniffer, SnifferRenderState, SnifferModel>(player, sniffer, model, SNIFFER_TEXTURE, matrix, partialTick) { state -> state.isBaby = isBaby }
        }
    }

    private fun renderCopper(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val copper = dummyCopper ?: return; val model = copperModel ?: return
        renderPetWrapper(player, ctx, copper, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            val renderer = mc.entityRenderDispatcher.getRenderer(copper)
            @Suppress("UNCHECKED_CAST")
            val accessor = renderer as? EntityRendererAccessor<CopperGolem, CopperGolemRenderState> ?: return@renderPetWrapper
            val state = accessor.callCreateRenderState()
            accessor.callExtractRenderState(copper, state, partialTick)

            state.walkAnimationPos = player.walkAnimation.position(partialTick)
            state.walkAnimationSpeed = player.walkAnimation.speed(partialTick)

            val uuid = player.uuid
            if (player.swingTime == 1) {
                val lastStart = copperSwingMap[uuid] ?: -100
                if (player.tickCount - lastStart > 20) copperSwingMap[uuid] = player.tickCount
            }
            val animStartTick = copperSwingMap[uuid]
            if (animStartTick != null && player.tickCount - animStartTick <= 20) state.interactionGetItem.start(animStartTick)
            else state.interactionGetItem.stop()

            model.setupAnim(state)

            val buffers = mc.renderBuffers().bufferSource()
            model.renderToBuffer(matrix, buffers.getBuffer(RenderType.entityCutoutNoCull(COPPER_TEXTURE)), 15728880, OverlayTexture.NO_OVERLAY, -1)
            model.renderToBuffer(matrix, buffers.getBuffer(RenderType.eyes(COPPER_EYES_TEXTURE)), 15728880, OverlayTexture.NO_OVERLAY, -1)
            buffers.endBatch()
        }
    }

    private fun renderBreeze(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val breeze = dummyBreeze ?: return; val model = breezeModel ?: return; val windModel = breezeWindModel ?: return
        renderPetWrapper(player, ctx, breeze, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            val renderer = mc.entityRenderDispatcher.getRenderer(breeze)
            @Suppress("UNCHECKED_CAST")
            val accessor = renderer as? EntityRendererAccessor<net.minecraft.world.entity.monster.breeze.Breeze, net.minecraft.client.renderer.entity.state.BreezeRenderState> ?: return@renderPetWrapper
            val state = accessor.callCreateRenderState()
            accessor.callExtractRenderState(breeze, state, partialTick)

            state.walkAnimationPos = player.walkAnimation.position(partialTick)
            state.walkAnimationSpeed = player.walkAnimation.speed(partialTick)

            model.setupAnim(state)
            windModel.setupAnim(state)

            val buffers = mc.renderBuffers().bufferSource()

            model.renderToBuffer(matrix, buffers.getBuffer(RenderType.entityCutoutNoCull(BREEZE_TEXTURE)), 15728880, OverlayTexture.NO_OVERLAY, -1)

            val time = breeze.tickCount + partialTick
            val xOff = time * 0.02f
            val yOff = time * 0.01f

            val windBuffer = try {
                buffers.getBuffer(RenderType.breezeWind(BREEZE_WIND_TEXTURE, xOff, yOff))
            } catch (e: NoSuchMethodError) {
                buffers.getBuffer(RenderType.energySwirl(BREEZE_WIND_TEXTURE, xOff, yOff))
            }

            windModel.renderToBuffer(matrix, windBuffer, 15728880, OverlayTexture.NO_OVERLAY, -1)
            buffers.endBatch()
        }
    }

    private fun renderFrog(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val frog = dummyFrog ?: return; val model = frogModel ?: return
        renderPetWrapper(player, ctx, frog, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<net.minecraft.world.entity.animal.frog.Frog, net.minecraft.client.renderer.entity.state.FrogRenderState, net.minecraft.client.model.FrogModel>(player, frog, model, FROG_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderPanda(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val panda = dummyPanda ?: return; val model = pandaModel ?: return
        renderPetWrapper(player, ctx, panda, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<net.minecraft.world.entity.animal.Panda, net.minecraft.client.renderer.entity.state.PandaRenderState, net.minecraft.client.model.PandaModel>(player, panda, model, PANDA_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderLlama(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val llama = dummyLlama ?: return; val model = llamaModel ?: return
        renderPetWrapper(player, ctx, llama, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<net.minecraft.world.entity.animal.horse.Llama, net.minecraft.client.renderer.entity.state.LlamaRenderState, net.minecraft.client.model.LlamaModel>(player, llama, model, LLAMA_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderGoat(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val goat = dummyGoat ?: return; val model = goatModel ?: return
        renderPetWrapper(player, ctx, goat, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<net.minecraft.world.entity.animal.goat.Goat, net.minecraft.client.renderer.entity.state.GoatRenderState, net.minecraft.client.model.GoatModel>(player, goat, model, GOAT_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderParrot(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val parrot = dummyParrot ?: return; val model = parrotModel ?: return
        renderPetWrapper(player, ctx, parrot, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<net.minecraft.world.entity.animal.Parrot, net.minecraft.client.renderer.entity.state.ParrotRenderState, net.minecraft.client.model.ParrotModel>(player, parrot, model, PARROT_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderGlowSquid(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val squid = dummyGlowSquid ?: return; val model = glowSquidModel ?: return
        renderPetWrapper(player, ctx, squid, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<net.minecraft.world.entity.GlowSquid, net.minecraft.client.renderer.entity.state.SquidRenderState, net.minecraft.client.model.SquidModel>(player, squid, model, GLOW_SQUID_TEXTURE, matrix, partialTick)
        }
    }

    @JvmStatic
    fun preRenderCallbackScaleHook(entityRenderer: AvatarRenderState, matrix: PoseStack) {
        val gameProfile = entityRenderer.getData(GAME_PROFILE_KEY) ?: return

        val uuidString = gameProfile.id?.toString()?.lowercase() ?: return
        val isMe = uuidString == mc.player?.uuid?.toString()?.lowercase()
        val hardcoded = randoms[uuidString]

        if (isMe) {
            if (enabled && dev) {
                val isAnyActive = (!zooMode && devMob.value != 0) || (zooMode && (dragonToggle || snifferToggle || wardenToggle || copperToggle || ravagerToggle || egToggle || creakingToggle || breezeToggle || frogToggle || pandaToggle || llamaToggle || goatToggle || parrotToggle || glowSquidToggle))

                if (isAnyActive && devRide) {
                    matrix.translate(0f, devRideHeight, 0f)
                    entityRenderer.isPassenger = true
                }

                if (hidePlayer && isAnyActive) {
                    matrix.scale(0.01f, 0.01f, 0.01f)
                } else if (devSize) {
                    if (devSizeY < 0) matrix.translate(0f, devSizeY * 2, 0f)
                    matrix.scale(devSizeX, devSizeY, devSizeZ)

                    entityRenderer.nameTagAttachment?.let { pos ->
                        val adjustedY = (pos.y + 0.15) * kotlin.math.abs(devSizeY)
                        entityRenderer.nameTagAttachment = net.minecraft.world.phys.Vec3(pos.x, adjustedY, pos.z)
                    }
                }
            } else if (hardcoded != null) {
                if (hardcoded.ride) {
                    matrix.translate(0f, hardcoded.rideHeight, 0f)
                    entityRenderer.isPassenger = true
                }
                applyHardcodedScale(matrix, hardcoded, entityRenderer)
            }
        } else {
            if (hardcoded != null) {
                if (hardcoded.ride) {
                    matrix.translate(0f, hardcoded.rideHeight, 0f)
                    entityRenderer.isPassenger = true
                }
                applyHardcodedScale(matrix, hardcoded, entityRenderer)
            }
        }
    }

    private fun applyHardcodedScale(matrix: PoseStack, data: RandomPlayer, entityRenderer: AvatarRenderState) {
        if (data.scale[1] < 0) matrix.translate(0f, data.scale[1] * 2, 0f)
        matrix.scale(data.scale[0], data.scale[1], data.scale[2])

        entityRenderer.nameTagAttachment?.let { pos ->
            val adjustedY = (pos.y + 0.15) * kotlin.math.abs(data.scale[1])
            entityRenderer.nameTagAttachment = net.minecraft.world.phys.Vec3(pos.x, adjustedY, pos.z)
        }
    }

    private fun applyHardcodedScale(matrix: PoseStack, data: RandomPlayer) {
        if (data.scale[1] < 0) matrix.translate(0f, data.scale[1] * 2, 0f)
        matrix.scale(data.scale[0], data.scale[1], data.scale[2])
    }

    fun getActivePetConfig(): PetConfigSetting? {
        return when (devMob.value) {
            1 -> dragonConfig
            2 -> snifferConfig
            3 -> wardenConfig
            4 -> copperConfig
            5 -> ravagerConfig
            6 -> egConfig
            7 -> creakingConfig
            8 -> breezeConfig
            9 -> frogConfig
            10 -> pandaConfig
            11 -> llamaConfig
            12 -> goatConfig
            13 -> parrotConfig
            14 -> glowSquidConfig
            15 -> ttsConfig
            else -> null
        }
    }

    fun cyclePet(forward: Boolean) {
        var next = devMob.value + (if (forward) 1 else -1)
        if (next > 15) next = 0
        if (next < 0) next = 15
        devMob.value = next
    }

    fun isCurrentPetZooEnabled(): Boolean {
        return when (devMob.value) {
            1 -> dragonToggle; 2 -> snifferToggle; 3 -> wardenToggle; 4 -> copperToggle; 5 -> ravagerToggle; 6 -> egToggle; 7 -> creakingToggle; 8 -> breezeToggle; 9 -> frogToggle; 10 -> pandaToggle; 11 -> llamaToggle; 12 -> goatToggle; 13 -> parrotToggle; 14 -> glowSquidToggle; 15 -> ttsToggle else -> false
        }
    }
    fun toggleCurrentPetZoo() {
        when (devMob.value) {
            1 -> dragonToggle = !dragonToggle
            2 -> snifferToggle = !snifferToggle
            3 -> wardenToggle = !wardenToggle
            4 -> copperToggle = !copperToggle
            5 -> ravagerToggle = !ravagerToggle
            6 -> egToggle = !egToggle
            7 -> creakingToggle = !creakingToggle
            8 -> breezeToggle = !breezeToggle
            9 -> frogToggle = !frogToggle
            10 -> pandaToggle = !pandaToggle
            11 -> llamaToggle = !llamaToggle
            12 -> goatToggle = !goatToggle
            13 -> parrotToggle = !parrotToggle
            14 -> glowSquidToggle = !glowSquidToggle
            15 -> ttsToggle = !ttsToggle
        }
    }
    @JvmStatic
    val GAME_PROFILE_KEY: RenderStateDataKey<GameProfile> = RenderStateDataKey.create { "shizo:game_profile" }
}