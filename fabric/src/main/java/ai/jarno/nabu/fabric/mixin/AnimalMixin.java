package ai.jarno.nabu.fabric.mixin;

import ai.jarno.nabu.item.FertilityCharm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API has no breeding or baby-spawn event of any kind, so the charm's multi-birth hook
 * has to be a mixin on this side. NeoForge gets the identical behaviour from
 * {@code BabyEntitySpawnEvent}; both funnel into the same common logic.
 *
 * <p>Injected at TAIL rather than on the {@code addFreshEntityWithPassengers} call, so there is
 * no descriptor to get wrong. The method body is a single {@code if} with no early return, so
 * TAIL also fires when no offspring was produced -- harmless, because the common logic asks for
 * its own offspring and bails when it gets none.
 */
@Mixin(Animal.class)
public abstract class AnimalMixin {
    @Inject(method = "spawnChildFromBreeding", at = @At("TAIL"))
    private void nabu$extraBabies(ServerLevel level, Animal partner, CallbackInfo ci) {
        FertilityCharm.onBabyBorn(level, (Animal) (Object) this, partner);
    }
}
