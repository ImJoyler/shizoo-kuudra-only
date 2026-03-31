package shizo.mixin.accessors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("deltaTracker")
    DeltaTracker.Timer shizo$getDeltaTracker();
}
