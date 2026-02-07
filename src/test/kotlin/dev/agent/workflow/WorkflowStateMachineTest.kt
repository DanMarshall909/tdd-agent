package dev.agent.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class WorkflowStateMachineTest : BehaviorSpec({
    given("an initial workflow state") {
        val initial = WorkflowState.initial()

        `when`("submitting a feature description") {
            val event = WorkflowEvent.FeatureSubmitted("Password reset by email", null)
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
                additionalRequirements = null,
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

        `when`("approving a plan without scenarios") {
            val withPlan = WorkflowStateMachine.reduce(
                planningState,
                WorkflowEvent.PlanProposed("Simple plan"),
            ) as TransitionResult.Success

            val result = WorkflowStateMachine.reduce(withPlan.state, WorkflowEvent.PlanApproved)

            then("it rejects the transition") {
                result.shouldBeInstanceOf<TransitionResult.Rejected>()
            }
        }
    }

    given("an implementation phase state with queued steps") {
        val steps = listOf(
            Step(StepType.GIVEN, "a context is prepared"),
            Step(StepType.THEN, "the outcome is verified"),
        )
        val implementationState = WorkflowState.initial().copy(
            phase = WorkflowPhase.IMPLEMENTATION,
            implementation = ImplementationData(steps, currentStepIndex = 0),
        )

        `when`("completing the current step before generating code") {
            val result = WorkflowStateMachine.reduce(implementationState, WorkflowEvent.StepCompleted)

            then("it rejects because sequence rules are enforced") {
                result.shouldBeInstanceOf<TransitionResult.Rejected>()
            }
        }

        `when`("generating implementation before a test") {
            val result = WorkflowStateMachine.reduce(
                implementationState,
                WorkflowEvent.ImplementationGenerated("class Impl"),
            )

            then("it rejects the transition") {
                result.shouldBeInstanceOf<TransitionResult.Rejected>()
            }
        }

        `when`("generating test then implementation and completing the step") {
            val testGenerated = WorkflowStateMachine.reduce(
                implementationState,
                WorkflowEvent.TestGenerated("class ExampleTest"),
            )
            testGenerated.shouldBeInstanceOf<TransitionResult.Success>()
            val withTest = (testGenerated as TransitionResult.Success).state
            withTest.implementation.stepStatus shouldBe ImplementationStepStatus.TEST_GENERATED
            withTest.implementation.generatedTestCode shouldBe "class ExampleTest"

            val implementationGenerated = WorkflowStateMachine.reduce(
                withTest,
                WorkflowEvent.ImplementationGenerated("class ExampleImpl"),
            )
            implementationGenerated.shouldBeInstanceOf<TransitionResult.Success>()
            val withImplementation = (implementationGenerated as TransitionResult.Success).state
            withImplementation.implementation.stepStatus shouldBe ImplementationStepStatus.IMPLEMENTATION_GENERATED
            withImplementation.implementation.generatedImplementationCode shouldBe "class ExampleImpl"

            val result = WorkflowStateMachine.reduce(withImplementation, WorkflowEvent.StepCompleted)

            then("it advances and resets per-step status") {
                result.shouldBeInstanceOf<TransitionResult.Success>()
                val state = (result as TransitionResult.Success).state
                state.phase shouldBe WorkflowPhase.IMPLEMENTATION
                state.implementation.currentStepIndex shouldBe 1
                state.implementation.stepStatus shouldBe ImplementationStepStatus.READY_FOR_TEST
                state.implementation.generatedTestCode shouldBe null
                state.implementation.generatedImplementationCode shouldBe null
            }
        }

        `when`("completing steps beyond the queue") {
            val firstWithTest = WorkflowStateMachine.reduce(
                implementationState,
                WorkflowEvent.TestGenerated("class ExampleTest"),
            ) as TransitionResult.Success
            val firstWithImpl = WorkflowStateMachine.reduce(
                firstWithTest.state,
                WorkflowEvent.ImplementationGenerated("class ExampleImpl"),
            ) as TransitionResult.Success
            val firstDone = WorkflowStateMachine.reduce(
                firstWithImpl.state,
                WorkflowEvent.StepCompleted,
            ) as TransitionResult.Success

            val secondWithTest = WorkflowStateMachine.reduce(
                firstDone.state,
                WorkflowEvent.TestGenerated("class ExampleTest2"),
            ) as TransitionResult.Success
            val secondWithImpl = WorkflowStateMachine.reduce(
                secondWithTest.state,
                WorkflowEvent.ImplementationGenerated("class ExampleImpl2"),
            ) as TransitionResult.Success
            val secondDone = WorkflowStateMachine.reduce(
                secondWithImpl.state,
                WorkflowEvent.StepCompleted,
            ) as TransitionResult.Success

            val result = WorkflowStateMachine.reduce(secondDone.state, WorkflowEvent.StepCompleted)

            then("it rejects because no current step exists") {
                result.shouldBeInstanceOf<TransitionResult.Rejected>()
            }
        }
    }

    given("an implementation phase state without queued steps") {
        val emptyImplementationState = WorkflowState.initial().copy(
            phase = WorkflowPhase.IMPLEMENTATION,
            implementation = ImplementationData(emptyList(), currentStepIndex = 0),
        )

        `when`("finishing a step when none exist") {
            val result = WorkflowStateMachine.reduce(emptyImplementationState, WorkflowEvent.StepCompleted)

            then("it rejects the transition") {
                result.shouldBeInstanceOf<TransitionResult.Rejected>()
            }
        }
    }
})
