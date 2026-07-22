package ai.jarno.nabu.neoforge;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.item.FertilityCharm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;

@Mod(Nabu.MOD_ID)
public final class NabuNeoForge {
    public NabuNeoForge() {
        Nabu.init();
        NeoForge.EVENT_BUS.addListener(NabuNeoForge::onBabySpawn);
    }

    /**
     * NeoForge fires this between vanilla creating the child and adding it to the level, so
     * the child is not positioned yet -- {@link FertilityCharm} anchors on the parents instead.
     */
    private static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (event.isCanceled() || event.getChild() == null) {
            return;
        }
        if (event.getParentA() instanceof AgeableMob parentA
                && event.getParentB() instanceof AgeableMob parentB
                && parentA.level() instanceof ServerLevel level) {
            FertilityCharm.onBabyBorn(level, parentA, parentB);
        }
    }
}
