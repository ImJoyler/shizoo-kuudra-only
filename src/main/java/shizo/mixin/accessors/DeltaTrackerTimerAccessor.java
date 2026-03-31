package shizo.mixin.accessors;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeltaTracker.Timer.class)
public interface DeltaTrackerTimerAccessor {
    @Accessor("targetMsptProvider")
    FloatUnaryOperator shizo$getTargetMsptProvider();

    @Mutable
    @Accessor("targetMsptProvider")
    void shizo$setTargetMsptProvider(FloatUnaryOperator operator);
}
