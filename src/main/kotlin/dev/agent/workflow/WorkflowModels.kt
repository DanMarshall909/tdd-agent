package dev.agent.workflow

import kotlinx.serialization.Serializable

@Serializable
data class Scenario(
    val name: String,
    val steps: List<Step>
)

@Serializable
data class Step(
    val type: StepType,
    val text: String
)

@Serializable
enum class StepType {
    GIVEN,
    WHEN,
    THEN,
    AND
}

enum class WorkflowPhase {
    REQUIREMENTS,
    RESEARCH,
    PLANNING,
    IMPLEMENTATION
}

data class RequirementsData(
    val featureDescription: String,
    val scenarios: List<Scenario>,
    val approved: Boolean
)

data class ResearchData(
    val summary: String?
)

data class PlanningData(
    val plan: String?,
    val approved: Boolean
)

data class ImplementationData(
    val steps: List<Step>,
    val currentStepIndex: Int
) {
    fun currentStepOrNull(): Step? = steps.getOrNull(currentStepIndex)
}

data class WorkflowState(
    val phase: WorkflowPhase,
    val requirements: RequirementsData,
    val research: ResearchData,
    val planning: PlanningData,
    val implementation: ImplementationData
) {
    companion object {
        fun initial(): WorkflowState = WorkflowState(
            phase = WorkflowPhase.REQUIREMENTS,
            requirements = RequirementsData(
                featureDescription = "",
                scenarios = emptyList(),
                approved = false,
            ),
            research = ResearchData(summary = null),
            planning = PlanningData(plan = null, approved = false),
            implementation = ImplementationData(
                steps = emptyList(),
                currentStepIndex = 0,
            ),
        )
    }
}
