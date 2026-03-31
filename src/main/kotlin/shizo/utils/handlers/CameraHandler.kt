package shizo.utils.handlers

// how are they so good at coding wtf?
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import shizo.events.RenderEvent
import shizo.events.core.on
import kotlin.math.PI

interface CameraProvider {
    fun isActive(): Boolean = true
    fun getPriority(): Int = 0

    fun shouldBlockKeyboardMovement(): Boolean = false
    fun shouldBlockMouseMovement(): Boolean = false

    fun shouldOverridePosition(): Boolean = false
    fun getCameraPosition(): Vec3? = null

    fun shouldOverrideYaw(): Boolean = false
    fun getYaw(): Float = 0f

    fun shouldOverridePitch(): Boolean = false
    fun getPitch(): Float = 0f

    fun shouldOverrideHitPos(): Boolean = false
    fun getPosForHit(): Vec3? = null

    fun shouldOverrideHitRot(): Boolean = false
    fun getRotForHit(): Vec3? = null
}

object CameraHandler {
    private const val YAW_FLAG: Byte = 0x01
    private const val PITCH_FLAG: Byte = 0x02
    private const val POSITION_FLAG: Byte = 0x04
    private const val BLOCK_KEYS_FLAG: Byte = 0x08
    private const val BLOCK_MOUSE_FLAG: Byte = 0x10
    private const val HIT_ROT_FLAG: Byte = 0x20
    private const val HIT_POS_FLAG: Byte = 0x40

    private val providers = mutableListOf<CameraProvider>()

    private var yaw = 0.0f
    private var pitch = 0.0f
    private val rotation = Quaternionf()

    var cameraPos: Vec3 = Vec3.ZERO
        private set

    private var hitPos: Vec3 = Vec3.ZERO
    private var hitRot: Vec3 = Vec3.ZERO
    private val cameraBlockPos = BlockPos.MutableBlockPos()
    private var flags: Byte = 0

    init {
        on<RenderEvent.Extract> {
            flags = 0
            if (providers.isEmpty()) return@on
            providers.removeIf { !it.isActive() }

            if (providers.isEmpty()) return@on

            if (providers.any { it.shouldBlockKeyboardMovement() }) flags = (flags.toInt() or BLOCK_KEYS_FLAG.toInt()).toByte()
            if (providers.any { it.shouldBlockMouseMovement() }) flags = (flags.toInt() or BLOCK_MOUSE_FLAG.toInt()).toByte()

            val sortedProviders = providers.sortedBy { it.getPriority() }

            val positionProvider = sortedProviders.firstOrNull { it.shouldOverridePosition() }
            val yawProvider = sortedProviders.firstOrNull { it.shouldOverrideYaw() }
            val pitchProvider = sortedProviders.firstOrNull { it.shouldOverridePitch() }
            val hitPosProvider = sortedProviders.firstOrNull { it.shouldOverrideHitPos() }
            val hitRotProvider = sortedProviders.firstOrNull { it.shouldOverrideHitRot() }

            if (positionProvider != null) {
                val pos = positionProvider.getCameraPosition()
                if (pos != null) {
                    cameraPos = pos
                    cameraBlockPos.set(cameraPos.x, cameraPos.y, cameraPos.z)
                    flags = (flags.toInt() or POSITION_FLAG.toInt()).toByte()
                }
            }

            var rotChanged = false

            if (yawProvider != null) {
                val newYaw = yawProvider.getYaw()
                if (newYaw != yaw) rotChanged = true
                yaw = newYaw
                flags = (flags.toInt() or YAW_FLAG.toInt()).toByte()
            }

            if (pitchProvider != null) {
                val newPitch = pitchProvider.getPitch()
                if (newPitch != pitch) rotChanged = true
                pitch = newPitch
                flags = (flags.toInt() or PITCH_FLAG.toInt()).toByte()
            }

            if (hitPosProvider != null) {
                val pos = hitPosProvider.getPosForHit()
                if (pos != null) {
                    hitPos = pos
                    flags = (flags.toInt() or HIT_POS_FLAG.toInt()).toByte()
                }
            }

            if (hitRotProvider != null) {
                val rot = hitRotProvider.getRotForHit()
                if (rot != null) {
                    hitRot = rot
                    flags = (flags.toInt() or HIT_ROT_FLAG.toInt()).toByte()
                }
            }

            if (rotChanged) updateRotation()
        }
    }

    private fun updateRotation() {
        rotation.rotationYXZ((PI - yaw * (PI / 180.0)).toFloat(), -pitch * (PI / 180.0).toFloat(), 0.0f)
    }

    @JvmStatic
    fun registerProvider(cameraProvider: CameraProvider) {
        if (!providers.contains(cameraProvider)) {
            providers.add(cameraProvider)
        }
    }


    @JvmStatic
    fun onGetCameraPos(cir: CallbackInfoReturnable<Vec3>) {
        if ((flags.toInt() and POSITION_FLAG.toInt()) == 0) return
        cir.returnValue = cameraPos
    }

    @JvmStatic
    fun onGetCameraBlockPos(cir: CallbackInfoReturnable<BlockPos>) {
        if ((flags.toInt() and POSITION_FLAG.toInt()) == 0) return
        cir.returnValue = cameraBlockPos
    }

    @JvmStatic
    fun onGetCameraYaw(cir: CallbackInfoReturnable<Float>) {
        if ((flags.toInt() and YAW_FLAG.toInt()) == 0) return
        cir.returnValue = yaw
    }

    @JvmStatic
    fun onGetCameraPitch(cir: CallbackInfoReturnable<Float>) {
        if ((flags.toInt() and PITCH_FLAG.toInt()) == 0) return
        cir.returnValue = pitch
    }

    @JvmStatic
    fun onGetCameraRotation(cir: CallbackInfoReturnable<Quaternionf>) {
        if ((flags.toInt() and (PITCH_FLAG.toInt() or YAW_FLAG.toInt())) == 0) return
        cir.returnValue = rotation
    }

    @JvmStatic
    fun onPrePollInputs(inputs: Input): Input {
        if ((flags.toInt() and BLOCK_KEYS_FLAG.toInt()) == 0) return inputs
        return Input(false, false, false, false, false, false, false)
    }

    @JvmStatic
    fun onGetPositionForHit(vec: Vec3): Vec3 {
        if ((flags.toInt() and HIT_POS_FLAG.toInt()) == 0) return vec
        return hitPos
    }

    @JvmStatic
    fun onGetRotationForHit(vec: Vec3): Vec3 {
        if ((flags.toInt() and HIT_ROT_FLAG.toInt()) == 0) return vec
        return hitRot
    }
}