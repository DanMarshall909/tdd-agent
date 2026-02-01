package dev.agent.workflow

sealed class WorkflowEvent {
    data class FeatureSubmitted(val description: String, val additionalRequirements: String?) : WorkflowEvent()
    data class ScenariosGenerated(val scenarios: List<Scenario>) : WorkflowEvent()
    object ScenariosApproved : WorkflowEvent()
    data class ResearchCompleted(val summary: String) : WorkflowEvent()
    data class PlanProposed(val plan: String) : WorkflowEvent()
    object PlanApproved : WorkflowEvent()
    object StepCompleted : WorkflowEvent()
}

sealed class TransitionResult {
    data class Success(val state: WorkflowState) : TransitionResult()
    data class Rejected(val reason: String) : TransitionResult()
}

object WorkflowStateMachine {
    fun reduce(state: WorkflowState, event: WorkflowEvent): TransitionResult {
        return when (event) {
            is WorkflowEvent.FeatureSubmitted -> handleFeatureSubmitted(state, event)
            is WorkflowEvent.ScenariosGenerated -> handleScenariosGenerated(state, event)
            WorkflowEvent.ScenariosApproved -> handleScenariosApproved(state)
            is WorkflowEvent.ResearchCompleted -> handleResearchCompleted(state, event)
            is WorkflowEvent.PlanProposed -> handlePlanProposed(state, event)
            WorkflowEvent.PlanApproved -> handlePlanApproved(state)
            WorkflowEvent.StepCompleted -> handleStepCompleted(state)
        }
    }

    private fun handleFeatureSubmitted(state: WorkflowState, event: WorkflowEvent.FeatureSubmitted): TransitionResult {
        if (state.phase != WorkflowPhase.REQUIREMENTS) {
            return TransitionResult.Rejected("Feature submission only allowed in requirements phase")
        }
        if (state.requirements.approved) {
            return TransitionResult.Rejected("Requirements are locked")
        }
        val updated = state.copy(
            requirements = state.requirements.copy(
                featureDescription = event.description.trim(),
                additionalRequirements = event.additionalRequirements?.trim().orEmpty().ifBlank { null },
            ),
        )
        return TransitionResult.Success(updated)
    }

    private fun handleScenariosGenerated(
        state: WorkflowState,
        event: WorkflowEvent.ScenariosGenerated
    ): TransitionResult {
        if (state.phase != WorkflowPhase.REQUIREMENTS) {
            return TransitionResult.Rejected("Scenario generation only allowed in requirements phase")
        }
        if (state.requirements.approved) {
            return TransitionResult.Rejected("Requirements are locked")
        }
        val updated = state.copy(
            requirements = state.requirements.copy(
                scenarios = event.scenarios,
                approved = false,
            ),
        )
        return TransitionResult.Success(updated)
    }

    private fun handleScenariosApproved(state: WorkflowState): TransitionResult {
        if (state.phase != WorkflowPhase.REQUIREMENTS) {
            return TransitionResult.Rejected("Scenario approval only allowed in requirements phase")
        }
        if (state.requirements.scenarios.isEmpty()) {
            return TransitionResult.Rejected("Cannot approve without scenarios")
        }
        val updated = state.copy(
            phase = WorkflowPhase.RESEARCH,
            requirements = state.requirements.copy(approved = true),
        )
        return TransitionResult.Success(updated)
    }

    private fun handleResearchCompleted(
        state: WorkflowState,
        event: WorkflowEvent.ResearchCompleted
    ): TransitionResult {
        if (state.phase != WorkflowPhase.RESEARCH) {
            return TransitionResult.Rejected("Research completion only allowed in research phase")
        }
        val updated = state.copy(
            phase = WorkflowPhase.PLANNING,
            research = state.research.copy(summary = event.summary.trim()),
        )
        return TransitionResult.Success(updated)
    }

    private fun handlePlanProposed(state: WorkflowState, event: WorkflowEvent.PlanProposed): TransitionResult {
        if (state.phase != WorkflowPhase.PLANNING) {
            return TransitionResult.Rejected("Plan proposals only allowed in planning phase")
        }
        val updated = state.copy(
            planning = state.planning.copy(plan = event.plan.trim()),
        )
        return TransitionResult.Success(updated)
    }

    private fun handlePlanApproved(state: WorkflowState): TransitionResult {
        if (state.phase != WorkflowPhase.PLANNING) {
            return TransitionResult.Rejected("Plan approval only allowed in planning phase")
        }
        val plan = state.planning.plan?.trim().orEmpty()
        if (plan.isBlank()) {
            return TransitionResult.Rejected("Cannot approve an empty plan")
        }
        if (state.requirements.scenarios.isEmpty()) {
            return TransitionResult.Rejected("Cannot start implementation without scenarios")
        }
        val steps = flattenSteps(state.requirements.scenarios)
        val updated = state.copy(
            phase = WorkflowPhase.IMPLEMENTATION,
            planning = state.planning.copy(approved = true),
            implementation = state.implementation.copy(
                steps = steps,
                currentStepIndex = 0,
            ),
        )
        return TransitionResult.Success(updated)
    }

    private fun handleStepCompleted(state: WorkflowState): TransitionResult {
        if (state.phase != WorkflowPhase.IMPLEMENTATION) {
            return TransitionResult.Rejected("Step completion only allowed in implementation phase")
        }
        val nextIndex = state.implementation.currentStepIndex + 1
        val updated = state.copy(
            implementation = state.implementation.copy(currentStepIndex = nextIndex),
        )
        return TransitionResult.Success(updated)
    }

    private fun flattenSteps(scenarios: List<Scenario>): List<Step> {
        return scenarios.flatMap { scenario ->
            scenario.steps
        }
    }
}
