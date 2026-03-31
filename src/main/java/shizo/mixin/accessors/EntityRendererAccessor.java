package shizo.mixin.accessors;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor<T extends Entity, S extends EntityRenderState> {
    @Invoker("createRenderState")
    S callCreateRenderState();

    @Invoker("extractRenderState")
    void callExtractRenderState(T entity, S state, float partialTick);
}