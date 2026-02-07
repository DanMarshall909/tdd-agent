package dev.agent.workflow

sealed class WorkflowEvent {
    data class FeatureSubmitted(val description: String, val additionalRequirements: String?) : WorkflowEvent()
    data class ScenariosGenerated(val scenarios: List<Scenario>) : WorkflowEvent()
    object ScenariosApproved : WorkflowEvent()
    data class ResearchCompleted(val summary: String) : WorkflowEvent()
    data class PlanProposed(val plan: String) : WorkflowEvent()
    object PlanApproved : WorkflowEvent()
    data class TestGenerated(val code: String) : WorkflowEvent()
    data class ImplementationGenerated(val code: String) : WorkflowEvent()
    object StepCompleted : WorkflowEvent()
}

sealed class TransitionResult {
    data class Success(val state: WorkflowState) : TransitionResult()
    data class Rejected(val reason: String) : TransitionResult()
}

object WorkflowRejectionReasons {
    const val FEATURE_SUBMISSION_REQUIRES_REQUIREMENTS_PHASE =
        "Feature submission only allowed in requirements phase"
    const val REQUIREMENTS_LOCKED = "Requirements are locked"
    const val SCENARIO_GENERATION_REQUIRES_REQUIREMENTS_PHASE =
        "Scenario generation only allowed in requirements phase"
    const val SCENARIO_APPROVAL_REQUIRES_REQUIREMENTS_PHASE =
        "Scenario approval only allowed in requirements phase"
    const val CANNOT_APPROVE_WITHOUT_SCENARIOS = "Cannot approve without scenarios"
    const val RESEARCH_COMPLETION_REQUIRES_RESEARCH_PHASE =
        "Research completion only allowed in research phase"
    const val PLAN_PROPOSAL_REQUIRES_PLANNING_PHASE = "Plan proposals only allowed in planning phase"
    const val PLAN_APPROVAL_REQUIRES_PLANNING_PHASE = "Plan approval only allowed in planning phase"
    const val CANNOT_APPROVE_EMPTY_PLAN = "Cannot approve an empty plan"
    const val CANNOT_START_IMPLEMENTATION_WITHOUT_SCENARIOS = "Cannot start implementation without scenarios"
    const val TEST_GENERATION_REQUIRES_IMPLEMENTATION_PHASE =
        "Test generation only allowed in implementation phase"
    const val NO_CURRENT_IMPLEMENTATION_STEP_AVAILABLE = "No current implementation step available"
    const val CANNOT_GENERATE_TEST_FOR_CURRENT_STATE = "Cannot generate test for current step in the current state"
    const val GENERATED_TEST_CODE_CANNOT_BE_EMPTY = "Generated test code cannot be empty"
    const val IMPLEMENTATION_GENERATION_REQUIRES_IMPLEMENTATION_PHASE =
        "Implementation generation only allowed in implementation phase"
    const val GENERATE_TEST_BEFORE_IMPLEMENTATION = "Generate a test before generating implementation"
    const val GENERATED_IMPLEMENTATION_CODE_CANNOT_BE_EMPTY = "Generated implementation code cannot be empty"
    const val STEP_COMPLETION_REQUIRES_IMPLEMENTATION_PHASE =
        "Step completion only allowed in implementation phase"
    const val NO_CURRENT_IMPLEMENTATION_STEP_TO_COMPLETE = "No current implementation step to complete"
    const val COMPLETE_TEST_AND_IMPLEMENTATION_BEFORE_STEP =
        "Complete test and implementation generation before finishing the step"
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
            is WorkflowEvent.TestGenerated -> handleTestGenerated(state, event)
            is WorkflowEvent.ImplementationGenerated -> handleImplementationGenerated(state, event)
            WorkflowEvent.StepCompleted -> handleStepCompleted(state)
        }
    }

    private fun handleFeatureSubmitted(state: WorkflowState, event: WorkflowEvent.FeatureSubmitted): TransitionResult {
        if (state.phase != WorkflowPhase.REQUIREMENTS) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.FEATURE_SUBMISSION_REQUIRES_REQUIREMENTS_PHASE)
        }
        if (state.requirements.approved) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.REQUIREMENTS_LOCKED)
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
            return TransitionResult.Rejected(WorkflowRejectionReasons.SCENARIO_GENERATION_REQUIRES_REQUIREMENTS_PHASE)
        }
        if (state.requirements.approved) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.REQUIREMENTS_LOCKED)
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
            return TransitionResult.Rejected(WorkflowRejectionReasons.SCENARIO_APPROVAL_REQUIRES_REQUIREMENTS_PHASE)
        }
        if (state.requirements.scenarios.isEmpty()) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.CANNOT_APPROVE_WITHOUT_SCENARIOS)
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
            return TransitionResult.Rejected(WorkflowRejectionReasons.RESEARCH_COMPLETION_REQUIRES_RESEARCH_PHASE)
        }
        val updated = state.copy(
            phase = WorkflowPhase.PLANNING,
            research = state.research.copy(summary = event.summary.trim()),
        )
        return TransitionResult.Success(updated)
    }

    private fun handlePlanProposed(state: WorkflowState, event: WorkflowEvent.PlanProposed): TransitionResult {
        if (state.phase != WorkflowPhase.PLANNING) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.PLAN_PROPOSAL_REQUIRES_PLANNING_PHASE)
        }
        val updated = state.copy(
            planning = state.planning.copy(plan = event.plan.trim()),
        )
        return TransitionResult.Success(updated)
    }

    private fun handlePlanApproved(state: WorkflowState): TransitionResult {
        if (state.phase != WorkflowPhase.PLANNING) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.PLAN_APPROVAL_REQUIRES_PLANNING_PHASE)
        }
        val plan = state.planning.plan?.trim().orEmpty()
        if (plan.isBlank()) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.CANNOT_APPROVE_EMPTY_PLAN)
        }
        if (state.requirements.scenarios.isEmpty()) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.CANNOT_START_IMPLEMENTATION_WITHOUT_SCENARIOS)
        }
        val steps = flattenSteps(state.requirements.scenarios)
        val updated = state.copy(
            phase = WorkflowPhase.IMPLEMENTATION,
            planning = state.planning.copy(approved = true),
            implementation = state.implementation.copy(
                steps = steps,
                currentStepIndex = 0,
                stepStatus = ImplementationStepStatus.READY_FOR_TEST,
                generatedTestCode = null,
                generatedImplementationCode = null,
            ),
        )
        return TransitionResult.Success(updated)
    }

    private fun handleTestGenerated(state: WorkflowState, event: WorkflowEvent.TestGenerated): TransitionResult {
        if (state.phase != WorkflowPhase.IMPLEMENTATION) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.TEST_GENERATION_REQUIRES_IMPLEMENTATION_PHASE)
        }
        if (state.implementation.currentStepOrNull() == null) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.NO_CURRENT_IMPLEMENTATION_STEP_AVAILABLE)
        }
        if (state.implementation.stepStatus != ImplementationStepStatus.READY_FOR_TEST) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.CANNOT_GENERATE_TEST_FOR_CURRENT_STATE)
        }
        val code = event.code.trim()
        if (code.isBlank()) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.GENERATED_TEST_CODE_CANNOT_BE_EMPTY)
        }
        val updated = state.copy(
            implementation = state.implementation.copy(
                stepStatus = ImplementationStepStatus.TEST_GENERATED,
                generatedTestCode = code,
                generatedImplementationCode = null,
            ),
        )
        return TransitionResult.Success(updated)
    }

    private fun handleImplementationGenerated(
        state: WorkflowState,
        event: WorkflowEvent.ImplementationGenerated
    ): TransitionResult {
        if (state.phase != WorkflowPhase.IMPLEMENTATION) {
            return TransitionResult.Rejected(
                WorkflowRejectionReasons.IMPLEMENTATION_GENERATION_REQUIRES_IMPLEMENTATION_PHASE,
            )
        }
        if (state.implementation.currentStepOrNull() == null) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.NO_CURRENT_IMPLEMENTATION_STEP_AVAILABLE)
        }
        if (state.implementation.stepStatus != ImplementationStepStatus.TEST_GENERATED) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.GENERATE_TEST_BEFORE_IMPLEMENTATION)
        }
        val code = event.code.trim()
        if (code.isBlank()) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.GENERATED_IMPLEMENTATION_CODE_CANNOT_BE_EMPTY)
        }
        val updated = state.copy(
            implementation = state.implementation.copy(
                stepStatus = ImplementationStepStatus.IMPLEMENTATION_GENERATED,
                generatedImplementationCode = code,
            ),
        )
        return TransitionResult.Success(updated)
    }

    private fun handleStepCompleted(state: WorkflowState): TransitionResult {
        if (state.phase != WorkflowPhase.IMPLEMENTATION) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.STEP_COMPLETION_REQUIRES_IMPLEMENTATION_PHASE)
        }
        if (state.implementation.currentStepOrNull() == null) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.NO_CURRENT_IMPLEMENTATION_STEP_TO_COMPLETE)
        }
        if (state.implementation.stepStatus != ImplementationStepStatus.IMPLEMENTATION_GENERATED) {
            return TransitionResult.Rejected(WorkflowRejectionReasons.COMPLETE_TEST_AND_IMPLEMENTATION_BEFORE_STEP)
        }
        val nextIndex = state.implementation.currentStepIndex + 1
        val updated = state.copy(
            implementation = state.implementation.copy(
                currentStepIndex = nextIndex,
                stepStatus = ImplementationStepStatus.READY_FOR_TEST,
                generatedTestCode = null,
                generatedImplementationCode = null,
            ),
        )
        return TransitionResult.Success(updated)
    }

    private fun flattenSteps(scenarios: List<Scenario>): List<Step> {
        return scenarios.flatMap { scenario ->
            scenario.steps
        }
    }
}
