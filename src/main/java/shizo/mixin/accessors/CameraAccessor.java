package shizo.mixin.accessors;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("eyeHeight")
    float getEyeHeight();

    @Accessor("eyeHeight")
    void setEyeHeight(float value);

    @Accessor("eyeHeightOld")
    float getEyeHeightOld();

    @Accessor("eyeHeightOld")
    void setEyeHeightOld(float value);
}