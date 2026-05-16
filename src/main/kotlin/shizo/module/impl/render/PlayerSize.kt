package shizo.module.impl.render

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import shizo.clickgui.settings.Setting.Companion.withDependency
import shizo.clickgui.settings.impl.*
import shizo.module.impl.Module
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.model.*
import net.minecraft.client.model.dragon.EnderDragonModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.state.*
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.Panda
import net.minecraft.world.entity.animal.Parrot
import net.minecraft.world.entity.animal.PolarBear
import net.minecraft.world.entity.animal.camel.Camel
import net.minecraft.world.entity.animal.coppergolem.CopperGolem
import net.minecraft.world.entity.animal.frog.Frog
import net.minecraft.world.entity.animal.goat.Goat
import net.minecraft.world.entity.animal.horse.Llama
import net.minecraft.world.entity.animal.sniffer.Sniffer
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.monster.ElderGuardian
import net.minecraft.world.entity.monster.Ravager
import net.minecraft.world.entity.monster.breeze.Breeze
import net.minecraft.world.entity.monster.creaking.Creaking
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import shizo.mixin.accessors.EntityRendererAccessor
import shizo.utils.renderUtils.CustomModelRenderer
import java.net.URI
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

object PlayerSize : Module(
    name = "Player Size",
    description = "Changes the size of players and can spawn personal pets."
) {
    private const val RANDOMS_URL = "https://gist.githubusercontent.com/ImJoyler/9d54e9b658d8390a9ea79e37fd54c61c/raw"

    private val dev by BooleanSetting("Use Custom Settings", true, "Uses your sliders below instead of hardcoded values.")
    private val devSize by BooleanSetting("Dev Size", true, "Toggles client side dev size for your own player.")
    private val devSizeX by NumberSetting("Size X", 1f, -1f, 10f, 0.1f, "X scale.").withDependency { devSize }
    private val devSizeY by NumberSetting("Size Y", 1f, -1f, 30f, 0.1f, "Y scale.").withDependency { devSize }
    private val devSizeZ by NumberSetting("Size Z", 1f, -1f, 10f, 0.1f, "Z scale.").withDependency { devSize }

    val wardrobeLauncher = WardrobeLauncherSetting()

    var zooMode by BooleanSetting("Render Multiple Mobs", false, "").withDependency { false }
    val devMob = SelectorSetting("Pet Mob", "None", arrayListOf("None", "Dragon", "Sniffer", "Warden", "Copper Golem", "Ravager", "Elder Guardian", "Creaking", "Breeze", "Frog", "Panda", "Llama", "Goat", "Parrot", "Glow Squid", "TTS", "Polar Bear", "Camel"),"").withDependency { false }

    var hidePlayer by BooleanSetting("Hide Player", false,"").withDependency { false }
    var schizoMode by BooleanSetting("Schizo Mode", false, "").withDependency { false }
    var devRide by BooleanSetting("Ride Pet", false, "").withDependency { false }
    private val devRideHeight by NumberSetting("Ride Height", -2.2f, -15.0f, 15.0f, 0.1f, "").withDependency { devRide && devMob.value != 0 }

    var devSnifferBaby by BooleanSetting("Baby Sniffer", false, "").withDependency { false }

    private val ttsSwingMap = HashMap<UUID, Int>()
    private val copperSwingMap = HashMap<UUID, Int>()
    private val polarSwingMap = HashMap<UUID, Int>()
    var randoms = HashMap<String, RandomPlayer>()

    data class HardcodedConfig(val enabled: Boolean, val size: Float, val pitch: Float, val dist: Float, val side: Float, val height: Float, val yaw: Float = 0f, val baby: Boolean = false)

    abstract class PetAdapter(val id: Int, val name: String) {
        val toggle = BooleanSetting("Enable $name", false, "").withDependency { zooMode }
        val config = PetConfigSetting("$name Settings", id).withDependency { false }
        open fun tick(level: Level) {}
        abstract fun render(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float, isBaby: Boolean = false)
        abstract fun getHardcoded(rp: RandomPlayer): HardcodedConfig?
    }

    open class StandardPetAdapter<E : Entity, S : EntityRenderState, M : EntityModel<S>>(
        id: Int, name: String,
        val texture: ResourceLocation,
        private val entityFactory: (Level) -> E,
        private val modelFactory: () -> M,
        private val hardcodedMapper: (RandomPlayer) -> HardcodedConfig?
    ) : PetAdapter(id, name) {
        var entity: E? = null
        var model: M? = null

        override fun getHardcoded(rp: RandomPlayer) = hardcodedMapper(rp)

        override fun tick(level: Level) {
            if (entity == null) entity = entityFactory(level)
            if (model == null) model = modelFactory()
            customTick(entity!!, model!!)
        }

        open fun customTick(e: E, m: M) { e.tickCount++ }

        override fun render(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float, isBaby: Boolean) {
            val e = entity ?: return; val m = model ?: return
            renderPetWrapper(player, ctx, e, size, pitch, yaw, dist, height, side) { matrix, partial ->
                beforeRenderState(e, player)
                renderPetState(player, e, m, texture, matrix, partial) { modifyState(it, player, e, partial, isBaby) }
                afterRenderState(matrix, mc.renderBuffers().bufferSource(), e, m, partial)
            }
        }

        open fun beforeRenderState(entity: E, player: Player) {}
        open fun modifyState(state: S, player: Player, entity: E, partialTick: Float, isBaby: Boolean) {}
        open fun afterRenderState(matrix: PoseStack, buffers: net.minecraft.client.renderer.MultiBufferSource, entity: E, model: M, partialTick: Float) {}
    }

    private val PETS = listOf(
        object : StandardPetAdapter<EnderDragon, EnderDragonRenderState, EnderDragonModel>(1, "Dragon", ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png"), { EnderDragon(EntityType.ENDER_DRAGON, it) }, { EnderDragonModel(mc.entityModels.bakeLayer(ModelLayers.ENDER_DRAGON)) }, { if (it.dragon) HardcodedConfig(it.dragon, it.dragonSize, it.dragonPitch, it.dragonDist, it.dragonSide, it.dragonHeight) else null }) {
            override fun customTick(e: EnderDragon, m: EnderDragonModel) { e.oFlapTime = e.flapTime; e.flapTime += 0.1f; if (e.flapTime < 0f) e.flapTime = 0f; e.flightHistory.record(e.y, e.yRot) }
        },
        object : StandardPetAdapter<Sniffer, SnifferRenderState, SnifferModel>(2, "Sniffer", ResourceLocation.withDefaultNamespace("textures/entity/sniffer/sniffer.png"), { Sniffer(EntityType.SNIFFER, it) }, { SnifferModel(mc.entityModels.bakeLayer(ModelLayers.SNIFFER)) }, { if (it.sniffer) HardcodedConfig(it.sniffer, it.snifferSize, it.snifferPitch, it.snifferDist, it.snifferSide, it.snifferHeight, baby = it.snifferBaby) else null }) {
            var babyModel: SnifferModel? = null
            override fun customTick(e: Sniffer, m: SnifferModel) { if (babyModel == null) babyModel = SnifferModel(mc.entityModels.bakeLayer(ModelLayers.SNIFFER_BABY)); e.tickCount++ }
            override fun render(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float, isBaby: Boolean) {
                val e = entity ?: return; val m = if (isBaby) babyModel ?: return else model ?: return
                e.walkAnimation.setSpeed(player.walkAnimation.speed()); e.age = if (isBaby) -100 else 0
                renderPetWrapper(player, ctx, e, size, pitch, yaw, dist, height, side) { matrix, partial -> renderPetState(player, e, m, texture, matrix, partial) { it.isBaby = isBaby } }
            }
        },
        object : StandardPetAdapter<Warden, WardenRenderState, WardenModel>(3, "Warden", ResourceLocation.withDefaultNamespace("textures/entity/warden/warden.png"), { Warden(EntityType.WARDEN, it) }, { WardenModel(mc.entityModels.bakeLayer(ModelLayers.WARDEN)) }, { if (it.warden) HardcodedConfig(it.warden, it.wardenSize, it.wardenPitch, it.wardenDist, it.wardenSide, it.wardenHeight) else null }) {
            override fun beforeRenderState(entity: Warden, player: Player) { if (player.swingTime > 0) entity.attackAnimationState.startIfStopped(entity.tickCount) else entity.attackAnimationState.stop() }
        },
        object : StandardPetAdapter<CopperGolem, CopperGolemRenderState, CopperGolemModel>(4, "Copper Golem", ResourceLocation.withDefaultNamespace("textures/entity/copper_golem/copper_golem.png"), { CopperGolem(EntityType.COPPER_GOLEM, it) }, { CopperGolemModel(mc.entityModels.bakeLayer(ModelLayers.COPPER_GOLEM)) }, { if (it.copper) HardcodedConfig(it.copper, it.copperSize, it.copperPitch, it.copperDist, it.copperSide, it.copperHeight) else null }) {
            override fun modifyState(state: CopperGolemRenderState, player: Player, entity: CopperGolem, partialTick: Float, isBaby: Boolean) {
                val uuid = player.uuid
                if (player.swingTime == 1) { val lastStart = copperSwingMap[uuid] ?: -100; if (player.tickCount - lastStart > 20) copperSwingMap[uuid] = player.tickCount }
                val animStartTick = copperSwingMap[uuid]
                if (animStartTick != null && player.tickCount - animStartTick <= 20) state.interactionGetItem.start(animStartTick) else state.interactionGetItem.stop()
            }
            override fun afterRenderState(matrix: PoseStack, buffers: net.minecraft.client.renderer.MultiBufferSource, entity: CopperGolem, model: CopperGolemModel, partialTick: Float) {
                model.renderToBuffer(matrix, buffers.getBuffer(RenderType.eyes(ResourceLocation.withDefaultNamespace("textures/entity/copper_golem/copper_golem_eyes.png"))), 15728880, OverlayTexture.NO_OVERLAY, -1)
                (buffers as? net.minecraft.client.renderer.MultiBufferSource.BufferSource)?.endBatch()
            }
        },
        object : StandardPetAdapter<Ravager, RavagerRenderState, RavagerModel>(5, "Ravager", ResourceLocation.withDefaultNamespace("textures/entity/illager/ravager.png"), { Ravager(EntityType.RAVAGER, it) }, { RavagerModel(mc.entityModels.bakeLayer(ModelLayers.RAVAGER)) }, { if (it.ravager) HardcodedConfig(it.ravager, it.ravagerSize, it.ravagerPitch, it.ravagerDist, it.ravagerSide, it.ravagerHeight) else null }) {
            override fun modifyState(state: RavagerRenderState, player: Player, entity: Ravager, partialTick: Float, isBaby: Boolean) { state.attackTicksRemaining = if (player.swingTime > 0) 10f - player.swingTime.toFloat() else 0f }
        },
        StandardPetAdapter(6, "Elder Guardian", ResourceLocation.withDefaultNamespace("textures/entity/guardian_elder.png"), { ElderGuardian(EntityType.ELDER_GUARDIAN, it) }, { GuardianModel(mc.entityModels.bakeLayer(ModelLayers.ELDER_GUARDIAN)) }, { if (it.eg) HardcodedConfig(it.eg, it.egSize, it.egPitch, it.egDist, it.egSide, it.egHeight) else null }),
        object : StandardPetAdapter<Creaking, CreakingRenderState, CreakingModel>(7, "Creaking", ResourceLocation.withDefaultNamespace("textures/entity/creaking/creaking.png"), { Creaking(EntityType.CREAKING, it) }, { CreakingModel(mc.entityModels.bakeLayer(ModelLayers.CREAKING)) }, { if (it.creaking) HardcodedConfig(it.creaking, it.creakingSize, it.creakingPitch, it.creakingDist, it.creakingSide, it.creakingHeight) else null }) {
            override fun beforeRenderState(entity: Creaking, player: Player) { if (player.swingTime > 0) entity.attackAnimationState.startIfStopped(entity.tickCount) else entity.attackAnimationState.stop() }
        },
        object : StandardPetAdapter<Breeze, BreezeRenderState,BreezeModel>(8, "Breeze", ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze.png"), { Breeze(EntityType.BREEZE, it) }, { BreezeModel(mc.entityModels.bakeLayer(ModelLayers.BREEZE)) }, { if (it.breeze) HardcodedConfig(it.breeze, it.breezeSize, it.breezePitch, it.breezeDist, it.breezeSide, it.breezeHeight) else null }) {
            var windModel: BreezeModel? = null
            override fun customTick(e: Breeze, m: BreezeModel) { if (windModel == null) windModel = BreezeModel(mc.entityModels.bakeLayer(ModelLayers.BREEZE_WIND)); e.tickCount++ }
            override fun modifyState(state: BreezeRenderState, player: Player, entity: Breeze, partialTick: Float, isBaby: Boolean) { windModel?.setupAnim(state) }
            override fun afterRenderState(matrix: PoseStack, buffers: net.minecraft.client.renderer.MultiBufferSource, entity: Breeze, model: BreezeModel, partialTick: Float) {
                val time = entity.tickCount + partialTick
                val windBuffer = try { buffers.getBuffer(RenderType.breezeWind(ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_wind.png"), time * 0.02f, time * 0.01f)) } catch (e: NoSuchMethodError) { buffers.getBuffer(RenderType.energySwirl(ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_wind.png"), time * 0.02f, time * 0.01f)) }
                windModel?.renderToBuffer(matrix, windBuffer, 15728880, OverlayTexture.NO_OVERLAY, -1)
                (buffers as? net.minecraft.client.renderer.MultiBufferSource.BufferSource)?.endBatch()
            }
        },
        StandardPetAdapter(9, "Frog", ResourceLocation.withDefaultNamespace("textures/entity/frog/temperate_frog.png"), { Frog(EntityType.FROG, it) }, { FrogModel(mc.entityModels.bakeLayer(ModelLayers.FROG)) }, { if (it.frog) HardcodedConfig(it.frog, it.frogSize, it.frogPitch, it.frogDist, it.frogSide, it.frogHeight) else null }),
        StandardPetAdapter(10, "Panda", ResourceLocation.withDefaultNamespace("textures/entity/panda/panda.png"), { Panda(EntityType.PANDA, it) }, { PandaModel(mc.entityModels.bakeLayer(ModelLayers.PANDA)) }, { if (it.panda) HardcodedConfig(it.panda, it.pandaSize, it.pandaPitch, it.pandaDist, it.pandaSide, it.pandaHeight) else null }),
        StandardPetAdapter(11, "Llama", ResourceLocation.withDefaultNamespace("textures/entity/llama/creamy.png"), { Llama(EntityType.LLAMA, it) }, { LlamaModel(mc.entityModels.bakeLayer(ModelLayers.LLAMA)) }, { if (it.llama) HardcodedConfig(it.llama, it.llamaSize, it.llamaPitch, it.llamaDist, it.llamaSide, it.llamaHeight) else null }),
        StandardPetAdapter(12, "Goat", ResourceLocation.withDefaultNamespace("textures/entity/goat/goat.png"), { Goat(EntityType.GOAT, it) }, { GoatModel(mc.entityModels.bakeLayer(ModelLayers.GOAT)) }, { if (it.goat) HardcodedConfig(it.goat, it.goatSize, it.goatPitch, it.goatDist, it.goatSide, it.goatHeight) else null }),
        StandardPetAdapter(13, "Parrot", ResourceLocation.withDefaultNamespace("textures/entity/parrot/parrot_red_blue.png"), { Parrot(EntityType.PARROT, it) }, { ParrotModel(mc.entityModels.bakeLayer(ModelLayers.PARROT)) }, { if (it.parrot) HardcodedConfig(it.parrot, it.parrotSize, it.parrotPitch, it.parrotDist, it.parrotSide, it.parrotHeight) else null }),
        StandardPetAdapter(14, "Glow Squid", ResourceLocation.withDefaultNamespace("textures/entity/squid/glow_squid.png"), { net.minecraft.world.entity.GlowSquid(EntityType.GLOW_SQUID, it) }, { SquidModel(mc.entityModels.bakeLayer(ModelLayers.GLOW_SQUID)) }, { if (it.glowSquid) HardcodedConfig(it.glowSquid, it.glowSquidSize, it.glowSquidPitch, it.glowSquidDist, it.glowSquidSide, it.glowSquidHeight) else null }),
        object : PetAdapter(15, "TTS") {
            override fun render(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float, isBaby: Boolean) { renderTts(player, ctx, size, pitch, yaw, dist, height, side) }
            override fun getHardcoded(rp: RandomPlayer) = if (rp.tts) HardcodedConfig(rp.tts, rp.ttsSize, rp.ttsPitch, rp.ttsDist, rp.ttsSide, rp.ttsHeight, rp.ttsYaw) else null
        },
        object : StandardPetAdapter<PolarBear, PolarBearRenderState, PolarBearModel>(16, "Polar Bear", ResourceLocation.withDefaultNamespace("textures/entity/bear/polarbear.png"), { PolarBear(EntityType.POLAR_BEAR, it) }, { PolarBearModel(mc.entityModels.bakeLayer(ModelLayers.POLAR_BEAR)) }, { if (it.polar) HardcodedConfig(it.polar, it.polarSize, it.polarPitch, it.polarDist, it.polarSide, it.polarHeight) else null }) {
            override fun modifyState(state: PolarBearRenderState, player: Player, entity: PolarBear, partialTick: Float, isBaby: Boolean) {
                val uuid = player.uuid; if (player.swingTime == 1) { val lastStart = polarSwingMap[uuid] ?: -100; if (player.tickCount - lastStart > 20) polarSwingMap[uuid] = player.tickCount }
                val animStartTick = polarSwingMap[uuid]; state.standScale = if (animStartTick != null && player.tickCount - animStartTick <= 20) 1.0f else 0.0f
            }
        },
        StandardPetAdapter(17, "Camel", ResourceLocation.withDefaultNamespace("textures/entity/camel/camel.png"), { Camel(EntityType.CAMEL, it) }, { CamelModel(mc.entityModels.bakeLayer(ModelLayers.CAMEL)) }, { if (it.camel) HardcodedConfig(it.camel, it.camelSize, it.camelPitch, it.camelDist, it.camelSide, it.camelHeight) else null })
    )

    init {
        this.registerSetting(wardrobeLauncher)
        this.registerSetting(devMob)
        PETS.forEach {
            this.registerSetting(it.toggle)
            this.registerSetting(it.config)
        }

        thread {
            try {
                val connection = URI("$RANDOMS_URL?t=${System.currentTimeMillis()}").toURL().openConnection()
                connection.setRequestProperty("Cache-Control", "no-cache")
                val response = connection.getInputStream().bufferedReader().readText()
                Gson().fromJson(response, Array<RandomPlayer>::class.java).forEach { randoms[it.uuid.lowercase()] = it }
                shizo.Shizo.logger.info("Loaded ${randoms.size} custom player cosmetics via UUID!")
            } catch (e: Exception) { shizo.Shizo.logger.error("Failed to load randoms JSON from Gist", e) }
        }

        ClientTickEvents.START_CLIENT_TICK.register {
            if (mc.level == null) return@register
            try { PETS.forEach { it.tick(mc.level!!) } } catch (_: Exception) {}
        }

        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            if (mc.level == null) return@register
            for (player in mc.level!!.players()) {
                val isMe = player == mc.player
                val hd = randoms[player.uuid.toString().lowercase()]

                PETS.forEach { pet ->
                    if (isMe && enabled && dev) {
                        if ((!zooMode && devMob.value == pet.id) || (zooMode && pet.toggle.value)) {
                            val d = pet.config.value
                            pet.render(player, context, d.size, d.pitch, d.yaw, d.dist, d.height, d.side, if (pet.id == 2) devSnifferBaby else false)
                        }
                    } else if (hd != null) {
                        val hConfig = pet.getHardcoded(hd)
                        if (hConfig != null && hConfig.enabled) {
                            pet.render(player, context, hConfig.size, hConfig.pitch, hConfig.yaw, hConfig.dist, hConfig.height, hConfig.side, hConfig.baby)
                        }
                    }
                }
            }
        }
    }

    private fun <E : Entity, S : EntityRenderState, M : EntityModel<S>> renderPetState(
        player: Player, dummyEntity: E, model: M, texture: ResourceLocation, matrix: PoseStack, partialTick: Float, stateModifier: (S) -> Unit = {}
    ) {
        val renderer = mc.entityRenderDispatcher.getRenderer(dummyEntity)
        @Suppress("UNCHECKED_CAST") val accessor = renderer as? EntityRendererAccessor<E, S> ?: return
        val state = accessor.callCreateRenderState()
        accessor.callExtractRenderState(dummyEntity, state, partialTick)

        if (state is LivingEntityRenderState) {
            state.walkAnimationPos = player.walkAnimation.position(partialTick)
            state.walkAnimationSpeed = player.walkAnimation.speed(partialTick)
        }
        stateModifier(state)
        model.setupAnim(state)

        val buffers = mc.renderBuffers().bufferSource()
        model.renderToBuffer(matrix, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), 15728880, OverlayTexture.NO_OVERLAY, -1)
        buffers.endBatch()
    }

    private fun renderPetWrapper(player: Player, context: WorldRenderContext, dummyEntity: Entity, scale: Float, pitch: Float, yaw: Float, distMult: Float, heightOff: Float, sideOff: Float, renderBlock: (PoseStack, Float) -> Unit) {
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

    private fun renderTts(player: Player, ctx: WorldRenderContext, size: Float, pitch: Float, yaw: Float, dist: Float, height: Float, side: Float) {
        val path = CustomModelRenderer.ALL_MODEL_PATHS["Tung Tung Sahur"] ?: return
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val isMe = player == mc.player
        if (isMe && mc.options.cameraType.isFirstPerson) return
        val uuid = player.uuid
        if (player.swingTime == 1) { val lastStart = ttsSwingMap[uuid] ?: -100; if (player.tickCount - lastStart > 20) ttsSwingMap[uuid] = player.tickCount }

        val animStartTick = ttsSwingMap[uuid]
        var hitProgress = 0f
        if (animStartTick != null && player.tickCount - animStartTick <= 20) hitProgress = ((player.tickCount - animStartTick) + partialTick) / 8f

        val bodyRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)
        val smoothedYaw = bodyRot + (Mth.wrapDegrees(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - bodyRot) * 0.6f)

        val bufferSource = ctx.consumers() as? net.minecraft.client.renderer.MultiBufferSource ?: return
        val matrix = ctx.matrices(); matrix.pushPose()

        val yawRad = Math.toRadians(bodyRot.toDouble())
        val distH = size * dist; val distSide = size * side
        val rX = Mth.lerp(partialTick.toDouble(), player.xo, player.x) + (-sin(yawRad) * distH) + (-cos(yawRad) * distSide)
        val rY = Mth.lerp(partialTick.toDouble(), player.yo, player.y) + height
        val rZ = Mth.lerp(partialTick.toDouble(), player.zo, player.z) + (cos(yawRad) * distH) + (-sin(yawRad) * distSide)

        val camPos = mc.gameRenderer.mainCamera.position
        matrix.translate(rX - camPos.x, rY - camPos.y, rZ - camPos.z)
        CustomModelRenderer.submit(path, matrix, bufferSource, smoothedYaw, 15728880, player.walkAnimation.speed(partialTick), player.walkAnimation.position(partialTick) * 0.001f, hitProgress.coerceIn(0f, 1f), size, side, height, dist, 0f, yaw, 0f)
        matrix.popPose()
    }

    @JvmStatic
    fun preRenderCallbackScaleHook(entityRenderer: AvatarRenderState, matrix: PoseStack) {
        val gameProfile = entityRenderer.getData(GAME_PROFILE_KEY) ?: return
        val uuidString = gameProfile.id?.toString()?.lowercase() ?: return
        val isMe = uuidString == mc.player?.uuid?.toString()?.lowercase()
        val hardcoded = randoms[uuidString]

        if (isMe) {
            if (enabled && dev) {
                if (((!zooMode && devMob.value != 0) || (zooMode && isCurrentPetZooEnabled())) && devRide) {
                    matrix.translate(0f, devRideHeight, 0f); entityRenderer.isPassenger = true
                }
                if (hidePlayer && ((!zooMode && devMob.value != 0) || (zooMode && isCurrentPetZooEnabled()))) {
                    matrix.scale(0.01f, 0.01f, 0.01f)
                } else if (devSize) {
                    if (devSizeY < 0) matrix.translate(0f, devSizeY * 2, 0f)
                    matrix.scale(devSizeX, devSizeY, devSizeZ)
                    entityRenderer.nameTagAttachment?.let { pos -> entityRenderer.nameTagAttachment = net.minecraft.world.phys.Vec3(pos.x, (pos.y + 0.15) * kotlin.math.abs(devSizeY), pos.z) }
                }
            } else if (hardcoded != null) {
                if (hardcoded.ride) { matrix.translate(0f, hardcoded.rideHeight, 0f); entityRenderer.isPassenger = true }
                applyHardcodedScale(matrix, hardcoded, entityRenderer)
            }
        } else if (hardcoded != null) {
            if (hardcoded.ride) { matrix.translate(0f, hardcoded.rideHeight, 0f); entityRenderer.isPassenger = true }
            applyHardcodedScale(matrix, hardcoded, entityRenderer)
        }
    }

    private fun applyHardcodedScale(matrix: PoseStack, data: RandomPlayer, entityRenderer: AvatarRenderState) {
        if (data.scale[1] < 0) matrix.translate(0f, data.scale[1] * 2, 0f)
        matrix.scale(data.scale[0], data.scale[1], data.scale[2])
        entityRenderer.nameTagAttachment?.let { pos -> entityRenderer.nameTagAttachment = net.minecraft.world.phys.Vec3(pos.x, (pos.y + 0.15) * kotlin.math.abs(data.scale[1]), pos.z) }
    }

    fun getActivePetConfig(): PetConfigSetting? = PETS.find { it.id == devMob.value }?.config
    fun isCurrentPetZooEnabled(): Boolean = PETS.find { it.id == devMob.value }?.toggle?.value ?: false
    fun toggleCurrentPetZoo() { PETS.find { it.id == devMob.value }?.let { it.toggle.value = !it.toggle.value } }
    fun cyclePet(forward: Boolean) {
        var next = devMob.value + (if (forward) 1 else -1)
        if (next > PETS.size) next = 0
        if (next < 0) next = PETS.size
        devMob.value = next
    }

    @JvmStatic
    val GAME_PROFILE_KEY: RenderStateDataKey<GameProfile> = RenderStateDataKey.create { "shizo:game_profile" }

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

        @SerializedName("Dragon") val dragon: Boolean = false, @SerializedName("DragonSize") val dragonSize: Float = 0.4f, @SerializedName("DragonPitch") val dragonPitch: Float = 20f, @SerializedName("DragonDist") val dragonDist: Float = 1.5f, @SerializedName("DragonSide") val dragonSide: Float = 0.0f, @SerializedName("DragonHeight") val dragonHeight: Float = 0.0f,
        @SerializedName("Sniffer") val sniffer: Boolean = false, @SerializedName("SnifferBaby") val snifferBaby: Boolean = false, @SerializedName("SnifferSize") val snifferSize: Float = 0.4f, @SerializedName("SnifferPitch") val snifferPitch: Float = 0f, @SerializedName("SnifferDist") val snifferDist: Float = 1.5f, @SerializedName("SnifferSide") val snifferSide: Float = 0.0f, @SerializedName("SnifferHeight") val snifferHeight: Float = 0.0f,
        @SerializedName("Warden") val warden: Boolean = false, @SerializedName("WardenSize") val wardenSize: Float = 0.4f, @SerializedName("WardenSide") val wardenSide: Float = 0.0f, @SerializedName("WardenPitch") val wardenPitch: Float = 0f, @SerializedName("WardenDist") val wardenDist: Float = 1.5f, @SerializedName("WardenHeight") val wardenHeight: Float = 0.0f,
        @SerializedName("Copper") val copper: Boolean = false, @SerializedName("CopperSize") val copperSize: Float = 0.4f, @SerializedName("CopperSide") val copperSide: Float = 0.0f, @SerializedName("CopperPitch") val copperPitch: Float = 0f, @SerializedName("CopperDist") val copperDist: Float = 1.5f, @SerializedName("CopperHeight") val copperHeight: Float = 0.0f,
        @SerializedName("Ravager") val ravager: Boolean = false, @SerializedName("RavagerSize") val ravagerSize: Float = 1.0f, @SerializedName("RavagerSide") val ravagerSide: Float = 0.0f, @SerializedName("RavagerPitch") val ravagerPitch: Float = 0f, @SerializedName("RavagerDist") val ravagerDist: Float = 0.0f, @SerializedName("RavagerHeight") val ravagerHeight: Float = 0.0f,
        @SerializedName("EG") val eg: Boolean = false, @SerializedName("EGSize") val egSize: Float = 1.0f, @SerializedName("EGSide") val egSide: Float = 0.0f, @SerializedName("EGPitch") val egPitch: Float = 0f, @SerializedName("EGDist") val egDist: Float = 0.0f, @SerializedName("EGHeight") val egHeight: Float = 0.0f,
        @SerializedName("Creaking") val creaking: Boolean = false, @SerializedName("CreakingSize") val creakingSize: Float = 1.0f, @SerializedName("CreakingSide") val creakingSide: Float = 0.0f, @SerializedName("CreakingPitch") val creakingPitch: Float = 0f, @SerializedName("CreakingDist") val creakingDist: Float = 0.0f, @SerializedName("CreakingHeight") val creakingHeight: Float = 0.0f,
        @SerializedName("Breeze") val breeze: Boolean = false, @SerializedName("BreezeSize") val breezeSize: Float = 1.0f, @SerializedName("BreezeSide") val breezeSide: Float = 0.0f, @SerializedName("BreezePitch") val breezePitch: Float = 0f, @SerializedName("BreezeDist") val breezeDist: Float = 0.0f, @SerializedName("BreezeHeight") val breezeHeight: Float = 0.0f,
        @SerializedName("Frog") val frog: Boolean = false, @SerializedName("FrogSize") val frogSize: Float = 1.0f, @SerializedName("FrogSide") val frogSide: Float = 0.0f, @SerializedName("FrogPitch") val frogPitch: Float = 0f, @SerializedName("FrogDist") val frogDist: Float = 0.0f, @SerializedName("FrogHeight") val frogHeight: Float = 0.0f,
        @SerializedName("Panda") val panda: Boolean = false, @SerializedName("PandaSize") val pandaSize: Float = 1.0f, @SerializedName("PandaSide") val pandaSide: Float = 0.0f, @SerializedName("PandaPitch") val pandaPitch: Float = 0f, @SerializedName("PandaDist") val pandaDist: Float = 0.0f, @SerializedName("PandaHeight") val pandaHeight: Float = 0.0f,
        @SerializedName("Llama") val llama: Boolean = false, @SerializedName("LlamaSize") val llamaSize: Float = 1.0f, @SerializedName("LlamaSide") val llamaSide: Float = 0.0f, @SerializedName("LlamaPitch") val llamaPitch: Float = 0f, @SerializedName("LlamaDist") val llamaDist: Float = 0.0f, @SerializedName("LlamaHeight") val llamaHeight: Float = 0.0f,
        @SerializedName("Goat") val goat: Boolean = false, @SerializedName("GoatSize") val goatSize: Float = 1.0f, @SerializedName("GoatSide") val goatSide: Float = 0.0f, @SerializedName("GoatPitch") val goatPitch: Float = 0f, @SerializedName("GoatDist") val goatDist: Float = 0.0f, @SerializedName("GoatHeight") val goatHeight: Float = 0.0f,
        @SerializedName("Parrot") val parrot: Boolean = false, @SerializedName("ParrotSize") val parrotSize: Float = 1.0f, @SerializedName("ParrotSide") val parrotSide: Float = 0.0f, @SerializedName("ParrotPitch") val parrotPitch: Float = 0f, @SerializedName("ParrotDist") val parrotDist: Float = 0.0f, @SerializedName("ParrotHeight") val parrotHeight: Float = 0.0f,
        @SerializedName("GlowSquid") val glowSquid: Boolean = false, @SerializedName("GlowSquidSize") val glowSquidSize: Float = 1.0f, @SerializedName("GlowSquidSide") val glowSquidSide: Float = 0.0f, @SerializedName("GlowSquidPitch") val glowSquidPitch: Float = 0f, @SerializedName("GlowSquidDist") val glowSquidDist: Float = 0.0f, @SerializedName("GlowSquidHeight") val glowSquidHeight: Float = 0.0f,
        @SerializedName("TTS") val tts: Boolean = false, @SerializedName("TTSSize") val ttsSize: Float = 1.0f, @SerializedName("TTSSide") val ttsSide: Float = 0.0f, @SerializedName("TTSPitch") val ttsPitch: Float = 0f, @SerializedName("TTSYaw") val ttsYaw: Float = 0f, @SerializedName("TTSDist") val ttsDist: Float = 0.0f, @SerializedName("TTSHeight") val ttsHeight: Float = 0.0f,
        @SerializedName("Polar") val polar: Boolean = false, @SerializedName("PolarSize") val polarSize: Float = 1.0f, @SerializedName("PolarSide") val polarSide: Float = 0.0f, @SerializedName("PolarPitch") val polarPitch: Float = 0f, @SerializedName("PolarDist") val polarDist: Float = 0.0f, @SerializedName("PolarHeight") val polarHeight: Float = 0.0f,
        @SerializedName("Camel") val camel: Boolean = false, @SerializedName("CamelSize") val camelSize: Float = 1.0f, @SerializedName("CamelSide") val camelSide: Float = 0.0f, @SerializedName("CamelPitch") val camelPitch: Float = 0f, @SerializedName("CamelDist") val camelDist: Float = 0.0f, @SerializedName("CamelHeight") val camelHeight: Float = 0.0f
    )
}