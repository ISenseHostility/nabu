package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.advancement.GardenProgressTrigger;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class NabuTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Nabu.MOD_ID, Registries.TRIGGER_TYPE);

    public static final RegistrySupplier<GardenProgressTrigger> GARDEN_PROGRESS =
            TRIGGERS.register("garden_progress", GardenProgressTrigger::new);

    private NabuTriggers() {
    }

    /**
     * Award progress to everyone close enough to have seen it happen.
     *
     * <p>Radius attribution rather than exact credit: the three moments this backs all originate
     * in block-entity ticks with no player in scope, and threading an owner through the shrine
     * would mean new persisted state for what is ultimately a toast.
     */
    public static void fireNearby(ServerLevel level, BlockPos pos, double radius, GardenProgressTrigger.Stage stage) {
        double radiusSq = radius * radius;
        for (ServerPlayer player : level.getPlayers(candidate ->
                candidate.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radiusSq)) {
            GARDEN_PROGRESS.get().trigger(player, stage);
        }
    }

    public static void register() {
        TRIGGERS.register();
    }
}
