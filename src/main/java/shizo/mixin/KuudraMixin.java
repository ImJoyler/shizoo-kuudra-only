package shizo.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import shizo.utils.skyblock.kuudra.KuudraUtils;
import shizo.module.impl.kuudra.general.KuudraInfo;

import java.util.function.Predicate;
@Mixin(GameRenderer.class)
public class KuudraMixin {
    @ModifyArg(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
            ),
            index = 4
    )
    private Predicate<Entity> replacePickPredicate(Predicate<Entity> original) {

        return entity -> {
            if (!KuudraInfo.clickThroughEnabled() || !KuudraUtils.INSTANCE.getInKuudra()) {
                return original.test(entity);
            }
            int phase = KuudraUtils.INSTANCE.getPhase();
            EntityType<?> type = entity.getType();
            if (type == EntityType.PLAYER && (phase == 1 || phase == 2 || phase == 4)) {
                //System.out.println("nigger");
                return false;
            }
            if (type == EntityType.GIANT && (phase == 1 || phase == 3)) {
                return false;
            }
            if (type == EntityType.ZOMBIE && phase == 2) {
                return false;
            }
            if (KuudraInfo.shouldClickThroughCannon() && (phase == 2)) {
                if (type == EntityType.ARMOR_STAND) {
                    String name = entity.getName().getString().toUpperCase();
                    if (name.contains("CANNON") || name.contains("CLICK")) {
                        return false;
                    }
                }
            }
            return original.test(entity);


        };
    }
}