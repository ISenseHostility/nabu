package ai.jarno.nabu.item;

import ai.jarno.nabu.registry.NabuItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.fox.Fox;

/**
 * Multi-birth behaviour for the Fertility Charm.
 *
 * <p>All of the policy lives here so both loaders behave identically; the platform modules
 * only supply the hook that notices a birth and calls {@link #onBabyBorn}.
 *
 * <p>The vanilla child is never touched. A charm can only ever <em>add</em> siblings, capped at
 * {@link #MAX_EXTRA}, and only while a player is carrying the charm in their offhand close
 * enough to have plausibly been the one breeding the animals.
 */
public final class FertilityCharm {
    /** Extra young on top of the vanilla one, so at most three are born at once. */
    public static final int MAX_EXTRA = 2;

    private static final double CHARM_RADIUS = 8.0;
    private static final double CHARM_RADIUS_SQR = CHARM_RADIUS * CHARM_RADIUS;

    private static final float SECOND_CHANCE = 0.35F;
    private static final float THIRD_CHANCE = 0.10F;

    /** How far siblings are nudged apart so they do not spawn inside one another. */
    private static final double MIN_SPREAD = 0.5;
    private static final double MAX_SPREAD = 1.0;

    private FertilityCharm() {
    }

    /**
     * Called by each loader's breeding hook when vanilla produces a child. The vanilla child
     * itself is neither passed in nor touched -- this only ever adds siblings beside it.
     *
     * <p>Everything is anchored on {@code parentA} rather than the newborn, because at least
     * one loader fires its hook before vanilla has positioned the child.
     */
    public static void onBabyBorn(ServerLevel level, AgeableMob parentA, AgeableMob parentB) {
        if (isExcluded(parentA) || !charmHolderNearby(level, parentA)) {
            return;
        }

        RandomSource random = level.getRandom();
        int extra = rollExtra(random);

        for (int i = 0; i < extra; i++) {
            AgeableMob sibling = parentA.getBreedOffspring(level, parentB);
            if (sibling == null) {
                // Some parents refuse to produce further offspring; take the hint.
                return;
            }

            sibling.setBaby(true);

            double angle = random.nextDouble() * Math.PI * 2.0;
            double spread = MIN_SPREAD + random.nextDouble() * (MAX_SPREAD - MIN_SPREAD);
            sibling.snapTo(
                    parentA.getX() + Math.cos(angle) * spread,
                    parentA.getY(),
                    parentA.getZ() + Math.sin(angle) * spread,
                    random.nextFloat() * 360.0F,
                    0.0F);

            level.addFreshEntity(sibling);
        }
    }

    /**
     * Species the charm deliberately skips, so both loaders behave identically.
     *
     * <p>Foxes breed through {@code Fox$FoxBreedGoal}, which inlines the whole thing and never
     * calls {@code spawnChildFromBreeding}. NeoForge patches that path and would fire its event
     * anyway; Fabric's mixin cannot see it. Rather than ship a mod that quietly behaves
     * differently per loader, foxes are excluded on both until Fabric can cover them too.
     */
    private static boolean isExcluded(AgeableMob parent) {
        return parent instanceof Fox;
    }

    /** Always the vanilla one; sometimes a second; rarely a third. */
    private static int rollExtra(RandomSource random) {
        int extra = 0;
        if (random.nextFloat() < SECOND_CHANCE) {
            extra++;
            if (random.nextFloat() < THIRD_CHANCE) {
                extra++;
            }
        }
        return Math.min(extra, MAX_EXTRA);
    }

    private static boolean charmHolderNearby(ServerLevel level, Entity birthplace) {
        for (ServerPlayer player : level.players()) {
            if (player.getOffhandItem().is(NabuItems.FERTILITY_CHARM.get())
                    && player.distanceToSqr(birthplace) <= CHARM_RADIUS_SQR) {
                return true;
            }
        }
        return false;
    }
}
