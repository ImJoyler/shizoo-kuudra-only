package shizo.mixin;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shizo.utils.handlers.CameraHandler;

@Mixin(value = Camera.class)
public abstract class CameraMixin {

    @Inject(method = "getPosition", at = @At("HEAD"), cancellable = true)
    private void onGetPosition(CallbackInfoReturnable<Vec3> cir) {
        CameraHandler.onGetCameraPos(cir);
    }

    @Inject(method = "getBlockPosition", at = @At("HEAD"), cancellable = true)
    private void onGetBlockPos(CallbackInfoReturnable<BlockPos> cir) {
        CameraHandler.onGetCameraBlockPos(cir);
    }


    @Inject(method = "getXRot", at = @At("HEAD"), cancellable = true)
    private void onGetPitch(CallbackInfoReturnable<Float> cir) {
        CameraHandler.onGetCameraPitch(cir);
    }

    @Inject(method = "getYRot", at = @At("HEAD"), cancellable = true)
    private void onGetYaw(CallbackInfoReturnable<Float> cir) {
        CameraHandler.onGetCameraYaw(cir);
    }

    @Inject(method = "rotation", at = @At("HEAD"), cancellable = true)
    private void onGetRotation(CallbackInfoReturnable<Quaternionf> cir) {
        CameraHandler.onGetCameraRotation(cir);
    }
}
