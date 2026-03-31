package shizo.module.impl.render

import com.google.gson.annotations.SerializedName
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
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
import net.minecraft.world.entity.monster.creaking.Creaking
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import shizo.mixin.accessors.EntityRendererAccessor
import kotlin.math.cos
import kotlin.math.sin

object PlayerSize : Module(
    name = "Player Size",
    description = "Changes the size of players and can spawn personal dragons."
) {
    private val dev by BooleanSetting("Use Custom Settings", true, desc = "If enabled, uses your sliders below instead of hardcoded values.")

    private val devSize by BooleanSetting("Dev Size", true, desc = "Toggles client side dev size for your own player.")
    private val devSizeX by NumberSetting("Size X", 1f, -1f, 10f, 0.1f, desc = "X scale.").withDependency { devSize }
    private val devSizeY by NumberSetting("Size Y", 1f, -1f, 30f, 0.1f, desc = "Y scale.").withDependency { devSize }
    private val devSizeZ by NumberSetting("Size Z", 1f, -1f, 10f, 0.1f, desc = "Z scale.").withDependency { devSize }


    val wardrobeLauncher = WardrobeLauncherSetting()

    var zooMode by BooleanSetting("Render Multiple Mobs", false, "").withDependency { false }
    val devMob = SelectorSetting("Pet Mob", "None", arrayListOf("None", "Dragon", "Sniffer", "Warden", "Copper Golem", "Ravager", "Elder Guardian", "Creaking", "Breeze", "Frog"),"").withDependency { false }

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
    private val BREEZE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze.png")

    private var dummyFrog: net.minecraft.world.entity.animal.frog.Frog? = null
    private var frogModel: net.minecraft.client.model.FrogModel? = null
    private val FROG_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/frog/temperate_frog.png")

    data class RandomPlayer(
        @SerializedName("CustomName") val customName: String?,
        @SerializedName("DevName") val name: String,
        @SerializedName("IsDev") val isDev: Boolean?,
        @SerializedName("WingsColor") val wingsColor: List<Int>,
        @SerializedName("Size") val scale: List<Float>,
        @SerializedName("Wings") val wings: Boolean,
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
        @SerializedName("FrogHeight") val frogHeight: Float = 0.0f
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


        randoms["__cby"] = goats("__cby", 0.02f, sniffer = true, sDist = -1.5f, sSize = 1.0f, sBaby=true)
        randoms["___cby"] = goats("___cby", 0.02f, wings = false,dragon = false)
        randoms["ImJoyless"] = goats("ImJoyless", 0.02f, dragon = true, dSize = 0.2f, dPitch = 45f, dDist = 1.0f, dHeight = 0.5f)
        randoms["NKairo"] = goats("NKairo", 0.01f, wings = false, creaking = true, crSize = 1f)
        randoms["Major_TooM"] = goats("Major_TooM", 0.02f, wings = false, copper = true, cSize = 1.5f, cDist = -0.5f)
        randoms["TactHaelStrom"] = goats("TactHaelStrom", 0.02f, warden = true, wSize = 0.75f, wDist = -0.5f )
        randoms["Berefts"] = goats("Berefts", 0.02f, eg = true, egSize = 0.5f )
        randoms["Syntocx"] = goats("Syntocx", 0.02f,sniffer = true, sBaby = true, sPitch = -180f, sSize = 0.5f, sHeight = 1f)
        randoms["pathwalker25"] = goats("pathwalker25", 0.02f,breeze = true, brSize = 1f, sHeight = 1f)
        randoms["JulienLovesMort"] = goats("JulienLovesMort", 0.02f, frog = true, fSize = 2f, fHeight = 1f)
        randoms["ATobii"] = goats("ATobii", 0.02f, ravager = true, rSize = 0.6f, rHeight = 1f)

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
                if (breezeModel == null) breezeModel = net.minecraft.client.model.BreezeModel(mc.entityModels.bakeLayer(ModelLayers.BREEZE))
                dummyBreeze?.tickCount = (dummyBreeze?.tickCount ?: 0) + 1

                if (dummyFrog == null) dummyFrog = net.minecraft.world.entity.animal.frog.Frog(EntityType.FROG, mc.level!!)
                if (frogModel == null) frogModel = net.minecraft.client.model.FrogModel(mc.entityModels.bakeLayer(ModelLayers.FROG))
                dummyFrog?.tickCount = (dummyFrog?.tickCount ?: 0) + 1
            } catch (_: Exception) {}
        }

        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            if (mc.level == null) return@register
            for (player in mc.level!!.players()) {
                val isMe = player == mc.player
                val hd = randoms[player.gameProfile.name]

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
                }
            }
        }
    }

    private inline fun renderPetWrapper(
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
        val breeze = dummyBreeze ?: return; val model = breezeModel ?: return
        renderPetWrapper(player, ctx, breeze, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<net.minecraft.world.entity.monster.breeze.Breeze, net.minecraft.client.renderer.entity.state.BreezeRenderState, net.minecraft.client.model.BreezeModel>(player, breeze, model, BREEZE_TEXTURE, matrix, partialTick)
        }
    }

    private fun renderFrog(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val frog = dummyFrog ?: return; val model = frogModel ?: return
        renderPetWrapper(player, ctx, frog, size, pitch, yaw, dist, height, side) { matrix, partialTick ->
            renderPetState<net.minecraft.world.entity.animal.frog.Frog, net.minecraft.client.renderer.entity.state.FrogRenderState, net.minecraft.client.model.FrogModel>(player, frog, model, FROG_TEXTURE, matrix, partialTick)
        }
    }

    @JvmStatic
    fun preRenderCallbackScaleHook(entityRenderer: AvatarRenderState, matrix: PoseStack) {
        val gameProfile = entityRenderer.getData(GAME_PROFILE_KEY) ?: return
        val name = gameProfile.name
        val isMe = name == mc.player?.gameProfile?.name
        val hardcoded = randoms[name]

        if (isMe) {
            if (enabled && dev) {
                val isAnyActive = (!zooMode && devMob.value != 0) || (zooMode && (dragonToggle || snifferToggle || wardenToggle || copperToggle || ravagerToggle || egToggle || creakingToggle || breezeToggle || frogToggle))

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

    private fun goats(
        name: String, size: Float, wings: Boolean = false, ride: Boolean = false, rideHeight: Float = -2.2f,
        dragon: Boolean = false, dSize: Float = 0.4f, dPitch: Float = 20f, dDist: Float = 1.5f, dSide: Float = 0.0f, dHeight: Float = 0.0f,
        sniffer: Boolean = false, sBaby: Boolean = false, sSize: Float = 0.4f, sPitch: Float = 0f, sDist: Float = 1.5f, sSide: Float = 0.0f, sHeight: Float = 0.0f,
        warden: Boolean = false, wSize: Float = 0.4f, wPitch: Float = 0f, wDist: Float = 1.5f, wSide: Float = 0.0f, wHeight: Float = 0.0f,
        copper: Boolean = false, cSize: Float = 0.4f, cPitch: Float = 0f, cDist: Float = 1.5f, cSide: Float = 0.0f, cHeight: Float = 0.0f,
        ravager: Boolean = false, rSize: Float = 1.0f, rPitch: Float = 0f, rDist: Float = 0.0f, rSide: Float = 0.0f, rHeight: Float = 0.0f,
        eg: Boolean = false, egSize: Float = 1.0f, egPitch: Float = 0f, egDist: Float = 0.0f, egSide: Float = 0.0f, egHeight: Float = 0.0f,
        creaking: Boolean = false, crSize: Float = 1.0f, crPitch: Float = 0f, crDist: Float = 0.0f, crSide: Float = 0.0f, crHeight: Float = 0.0f,
        breeze: Boolean = false, brSize: Float = 1.0f, brPitch: Float = 0f, brDist: Float = 0.0f, brSide: Float = 0.0f, brHeight: Float = 0.0f,
        frog: Boolean = false, fSize: Float = 1.0f, fPitch: Float = 0f, fDist: Float = 0.0f, fSide: Float = 0.0f, fHeight: Float = 0.0f
    ): RandomPlayer {
        return RandomPlayer(
            customName = null, name = name, isDev = true, wingsColor = listOf(255, 255, 255), scale = listOf(size, size, size), wings = wings, ride = ride, rideHeight = rideHeight,
            dragon = dragon, dragonSize = dSize, dragonPitch = dPitch, dragonDist = dDist, dragonSide = dSide, dragonHeight = dHeight,
            sniffer = sniffer, snifferBaby = sBaby, snifferSize = sSize, snifferPitch = sPitch, snifferDist = sDist, snifferSide = sSide, snifferHeight = sHeight,
            warden = warden, wardenSize = wSize, wardenPitch = wPitch, wardenDist = wDist, wardenSide = wSide, wardenHeight = wHeight,
            copper = copper, copperSize = cSize, copperPitch = cPitch, copperDist = cDist, copperSide = cSide, copperHeight = cHeight,
            ravager = ravager, ravagerSize = rSize, ravagerPitch = rPitch, ravagerDist = rDist, ravagerSide = rSide, ravagerHeight = rHeight,
            eg = eg, egSize = egSize, egPitch = egPitch, egDist = egDist, egSide = egSide, egHeight = egHeight,
            creaking = creaking, creakingSize = crSize, creakingPitch = crPitch, creakingDist = crDist, creakingSide = crSide, creakingHeight = crHeight,
            breeze = breeze, breezeSize = brSize, breezePitch = brPitch, breezeDist = brDist, breezeSide = brSide, breezeHeight = brHeight,
            frog = frog, frogSize = fSize, frogPitch = fPitch, frogDist = fDist, frogSide = fSide, frogHeight = fHeight
        )
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
            else -> null
        }
    }

    fun cyclePet(forward: Boolean) {
        var next = devMob.value + (if (forward) 1 else -1)
        if (next > 9) next = 1
        if (next < 1) next = 9
        devMob.value = next
    }

    fun isCurrentPetZooEnabled(): Boolean {
        return when (devMob.value) {
            1 -> dragonToggle; 2 -> snifferToggle; 3 -> wardenToggle; 4 -> copperToggle; 5 -> ravagerToggle; 6 -> egToggle; 7 -> creakingToggle; 8 -> breezeToggle; 9 -> frogToggle; else -> false
        }
    }
    fun toggleCurrentPetZoo() {
        when (devMob.value) {
            1 -> dragonToggle = !dragonToggle; 2 -> snifferToggle = !snifferToggle; 3 -> wardenToggle = !wardenToggle; 4 -> copperToggle = !copperToggle; 5 -> ravagerToggle = !ravagerToggle; 6 -> egToggle = !egToggle; 7 -> creakingToggle = !creakingToggle; 8 -> breezeToggle = !breezeToggle; 9 -> frogToggle = !frogToggle
        }
    }
    @JvmStatic
    val GAME_PROFILE_KEY: RenderStateDataKey<GameProfile> = RenderStateDataKey.create { "shizo:game_profile" }
}

