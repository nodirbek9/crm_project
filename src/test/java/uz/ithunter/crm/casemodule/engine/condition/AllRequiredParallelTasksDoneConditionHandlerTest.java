package uz.ithunter.crm.casemodule.engine.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uz.ithunter.crm.casemodule.CaseStage;
import uz.ithunter.crm.casemodule.CaseStageStatus;
import uz.ithunter.crm.casemodule.ElectronicCase;
import uz.ithunter.crm.workflow.Workflow;

class AllRequiredParallelTasksDoneConditionHandlerTest {

    private final AllRequiredParallelTasksDoneConditionHandler handler = 
            new AllRequiredParallelTasksDoneConditionHandler();

    @Test
    void conditionMetWhenAllRequiredSiblingsAreCompleted() {
        CaseStage completed1 = new CaseStage();
        completed1.setStatus(CaseStageStatus.COMPLETED);
        
        CaseStage completed2 = new CaseStage();
        completed2.setStatus(CaseStageStatus.COMPLETED);

        TransitionContext context = new TransitionContext(
                new ElectronicCase(),
                new Workflow(),
                group -> List.of(completed1, completed2)
        );

        uz.ithunter.crm.workflow.WorkflowTransition transition = mock(uz.ithunter.crm.workflow.WorkflowTransition.class);
        when(transition.getConditionValue()).thenReturn("GROUP_A");

        assertThat(handler.matches(transition, context)).isTrue();
    }

    @Test
    void conditionNotMetWhenAnyRequiredSiblingIsNotCompleted() {
        CaseStage completed = new CaseStage();
        completed.setStatus(CaseStageStatus.COMPLETED);
        
        CaseStage active = new CaseStage();
        active.setStatus(CaseStageStatus.ACTIVE);

        TransitionContext context = new TransitionContext(
                new ElectronicCase(),
                new Workflow(),
                group -> List.of(completed, active)
        );

        uz.ithunter.crm.workflow.WorkflowTransition transition = mock(uz.ithunter.crm.workflow.WorkflowTransition.class);
        when(transition.getConditionValue()).thenReturn("GROUP_A");

        assertThat(handler.matches(transition, context)).isFalse();
    }

    @Test
    void conditionNotMetWhenNoRequiredSiblingsExist() {
        TransitionContext context = new TransitionContext(
                new ElectronicCase(),
                new Workflow(),
                group -> List.of()
        );

        uz.ithunter.crm.workflow.WorkflowTransition transition = mock(uz.ithunter.crm.workflow.WorkflowTransition.class);
        when(transition.getConditionValue()).thenReturn("GROUP_A");

        assertThat(handler.matches(transition, context)).isFalse();
    }
}
