package shizo.commands


import com.github.stivais.commodore.Commodore
import shizo.utils.RotationUtils
import shizo.utils.devMessage
import shizo.utils.modMessage

val rotationCommand = Commodore("rotate", "rot") {
    runs { yaw: Float, pitch: Float ->
        RotationUtils.smoothRotate(yaw, pitch, 150, 300, false)
        devMessage("§aRotating to $yaw, $pitch (Defaults: 150-300ms, No Overshoot)")
    }

    runs { yaw: Float, pitch: Float, time: Long ->
        RotationUtils.smoothRotate(yaw, pitch, time, time, false)
        devMessage("§aRotating to $yaw, $pitch in ${time}ms")
    }

    runs { yaw: Float, pitch: Float, min: Long, max: Long ->
        RotationUtils.smoothRotate(yaw, pitch, min, max, false)
        devMessage("§aRotating to $yaw, $pitch between $min-${max}ms")
    }

    runs { yaw: Float, pitch: Float, min: Long, max: Long, overshoot: Boolean ->
        RotationUtils.smoothRotate(yaw, pitch, min, max, overshoot)
        devMessage("§aRotating to $yaw, $pitch (Overshoot: $overshoot)")
    }
}