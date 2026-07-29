package ai.jarno.nabu.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

/**
 * Progress through the restoration of the Gardens.
 *
 * <p>One trigger covering three moments, because none of them is expressible with a vanilla
 * criterion: a screw priming boosts a bed whether or not a player is watching, and the shrine
 * latches terraces from a block-entity tick. There is no player in any of those code paths, so
 * the trigger is fired at whoever is nearby -- see {@code NabuTriggers.fireNearby}.
 */
public class GardenProgressTrigger extends SimpleCriterionTrigger<GardenProgressTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Stage stage) {
        trigger(player, instance -> instance.stage() == stage);
    }

    /** The three rungs of the chain this trigger backs. */
    public enum Stage implements StringRepresentable {
        /** A planting bed reached Boosted. */
        BOOSTED("boosted"),
        /** A terrace latched as restored. */
        TERRACE("terrace"),
        /** The Gardens completed, once and for all. */
        AWAKENED("awakened");

        public static final Codec<Stage> CODEC = StringRepresentable.fromEnum(Stage::values);

        private final String name;

        Stage(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Stage stage)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        Stage.CODEC.fieldOf("stage").forGetter(TriggerInstance::stage))
                .apply(instance, TriggerInstance::new));
    }
}
