package uz.ithunter.crm.casemodule.engine.activator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import uz.ithunter.crm.workflow.StageType;

/**
 * Indexes the {@link StageActivator} beans by stage type.
 *
 * <p>Unlike {@link uz.ithunter.crm.casemodule.engine.TransitionEvaluator}, a missing entry here is
 * legal and does <b>not</b> fail startup. A transition condition with no handler is a routing bug that
 * would silently stall a case, but a stage type with no activator simply has no automatic side effect
 * - {@code ACCOUNTING} is exactly that by specification, and in this phase so are the types whose side
 * effect belongs to a later block:
 * <ul>
 *   <li>{@code PRIMARY_CHECK}, {@code EXECUTION}, {@code FINAL_REVIEW} create a {@code Task} - Phase 9
 *       (FINAL_IMPLEMENTATION_ORDER.md names {@code ExecutionStageActivator} as Phase 9's class, so
 *       Phase 7 deliberately does not squat on it);</li>
 *   <li>{@code ENDORSEMENT} opens an {@code ApprovalRound}, {@code SIGNING} promotes a document
 *       version - Phase 10.</li>
 * </ul>
 * ASSUMPTIONS.md A25 records this. The stage still activates, still gets its deadline and still
 * appears in the timeline; only the automatic follow-up work is absent, which is an honest
 * {@code [~]} rather than a fake implementation.
 */
@Component
public class StageActivatorRegistry {

    private final Map<StageType, StageActivator> activators = new EnumMap<>(StageType.class);

    public StageActivatorRegistry(List<StageActivator> stageActivators) {
        for (StageActivator activator : stageActivators) {
            StageActivator previous = activators.put(activator.supportedType(), activator);
            if (previous != null) {
                throw new IllegalStateException("two StageActivators claim " + activator.supportedType()
                        + ": " + previous.getClass().getName() + " and " + activator.getClass().getName());
            }
        }
    }

    public Optional<StageActivator> resolve(StageType stageType) {
        return Optional.ofNullable(activators.get(stageType));
    }
}
