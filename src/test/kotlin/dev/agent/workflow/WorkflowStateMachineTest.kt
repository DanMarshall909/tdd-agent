package dev.agent.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class WorkflowStateMachineTest : BehaviorSpec({
    given("an initial workflow state") {
        val initial = WorkflowState.initial()

        `when`("submitting a feature description") {
            val event = WorkflowEvent.FeatureSubmitted("Password reset by email")
            val result = WorkflowStateMachine.reduce(initial, event)

            then("it stays in requirements and stores the description") {
                result.shouldBeInstanceOf<TransitionResult.Success>()
                val state = (result as TransitionResult.Success).state
                state.phase shouldBe WorkflowPhase.REQUIREMENTS
                state.requirements.featureDescription shouldBe "Password reset by email"
            }
        }

        `when`("approving scenarios without any scenarios") {
            val result = WorkflowStateMachine.reduce(initial, WorkflowEvent.ScenariosApproved)

            then("it rejects the transition") {
                result.shouldBeInstanceOf<TransitionResult.Rejected>()
            }
        }
    }

    given("a requirements state with generated scenarios") {
        val scenario = Scenario(
            name = "Password reset",
            steps = listOf(
                Step(StepType.GIVEN, "a registered user"),
                Step(StepType.WHEN, "they request a reset"),
                Step(StepType.THEN, "they receive an email"),
            ),
        )
        val stateWithScenarios = WorkflowState.initial().copy(
            requirements = RequirementsData(
                featureDescription = "Password reset",
                scenarios = listOf(scenario),
                approved = false,
            ),
        )

        `when`("approving scenarios") {
            val result = WorkflowStateMachine.reduce(stateWithScenarios, WorkflowEvent.ScenariosApproved)

            then("it transitions to research and locks requirements") {
                result.shouldBeInstanceOf<TransitionResult.Success>()
                val state = (result as TransitionResult.Success).state
                state.phase shouldBe WorkflowPhase.RESEARCH
                state.requirements.approved shouldBe true
            }
        }
    }

    given("a research phase state") {
        val researchState = WorkflowState.initial().copy(
            phase = WorkflowPhase.RESEARCH,
        )

        `when`("completing research") {
            val result = WorkflowStateMachine.reduce(
                researchState,
                WorkflowEvent.ResearchCompleted("Reviewed prior art"),
            )

            then("it transitions to planning with a summary") {
                result.shouldBeInstanceOf<TransitionResult.Success>()
                val state = (result as TransitionResult.Success).state
                state.phase shouldBe WorkflowPhase.PLANNING
                state.research.summary shouldBe "Reviewed prior art"
            }
        }

        `when`("approving a plan directly") {
            val result = WorkflowStateMachine.reduce(researchState, WorkflowEvent.PlanApproved)

            then("it rejects the transition") {
                result.shouldBeInstanceOf<TransitionResult.Rejected>()
            }
        }
    }

    given("a planning phase state") {
        val planningState = WorkflowState.initial().copy(
            phase = WorkflowPhase.PLANNING,
        )

        `when`("proposing a plan") {
            val result = WorkflowStateMachine.reduce(
                planningState,
                WorkflowEvent.PlanProposed("Use repositories + service layer"),
            )

            then("it remains in planning with the plan recorded") {
                result.shouldBeInstanceOf<TransitionResult.Success>()
                val state = (result as TransitionResult.Success).state
                state.phase shouldBe WorkflowPhase.PLANNING
                state.planning.plan shouldBe "Use repositories + service layer"
            }
        }

        `when`("approving a plan without one") {
            val result = WorkflowStateMachine.reduce(planningState, WorkflowEvent.PlanApproved)

            then("it rejects the transition") {
                result.shouldBeInstanceOf<TransitionResult.Rejected>()
            }
        }
    }
})
