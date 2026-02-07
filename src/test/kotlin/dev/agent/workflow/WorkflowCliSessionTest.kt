package dev.agent.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class WorkflowCliSessionTest : BehaviorSpec({
    val featureDescription = "Reset password"
    val researchSummary = "Reviewed docs"
    val planText = "Use services"

    fun createSession(): CliWorkflowSession {
        val sampleScenario = Scenario(
            name = "CLI sample",
            steps = listOf(
                Step(StepType.GIVEN, "prepare"),
                Step(StepType.WHEN, "act"),
                Step(StepType.THEN, "verify"),
            ),
        )
        return CliWorkflowSession(listOf(sampleScenario))
    }

    fun moveToImplementation(session: CliWorkflowSession) {
        session.handle("${CliWorkflowSession.COMMAND_SUBMIT} $featureDescription")
        session.handle(CliWorkflowSession.COMMAND_GENERATE_SCENARIOS)
        session.handle(CliWorkflowSession.COMMAND_APPROVE_SCENARIOS)
        session.handle("${CliWorkflowSession.COMMAND_COMPLETE_RESEARCH} $researchSummary")
        session.handle("${CliWorkflowSession.COMMAND_PROPOSE_PLAN} $planText")
        session.handle(CliWorkflowSession.COMMAND_APPROVE_PLAN)
    }

    given("a CLI workflow session") {
        `when`("inspecting the status before any commands") {
            val session = createSession()
            val status = session.handle(CliWorkflowSession.COMMAND_STATUS)

            then("it reports the requirements phase") {
                status.message.shouldContain("${CliWorkflowSession.MESSAGE_PHASE_PREFIX} REQUIREMENTS")
                status.message.shouldContain("${CliWorkflowSession.MESSAGE_STEP_STATUS_PREFIX} READY_FOR_TEST")
            }
        }

        `when`("running through the happy path to implementation") {
            val session = createSession()
            moveToImplementation(session)

            then("the state machine reaches implementation") {
                session.currentState().phase shouldBe WorkflowPhase.IMPLEMENTATION
                session.currentState().implementation.steps.size shouldBe 3
            }
        }

        `when`("completing an implementation step") {
            val session = createSession()
            moveToImplementation(session)
            session.handle("${CliWorkflowSession.COMMAND_GENERATE_TEST} class ExampleTest")
            session.handle("${CliWorkflowSession.COMMAND_GENERATE_IMPL} class ExampleImpl")
            val result = session.handle(CliWorkflowSession.COMMAND_COMPLETE_STEP)

            then("the current step index advances") {
                result.message.shouldContain(CliWorkflowSession.MESSAGE_ACTION_SUCCEEDED_TOKEN)
                session.currentState().implementation.currentStepIndex shouldBe 1
            }
        }

        `when`("querying status after test generation") {
            val session = createSession()
            moveToImplementation(session)
            session.handle("${CliWorkflowSession.COMMAND_GENERATE_TEST} class ExampleTestStatus")
            val status = session.handle(CliWorkflowSession.COMMAND_STATUS)

            then("it reports the tracked implementation step status") {
                status.message.shouldContain("${CliWorkflowSession.MESSAGE_STEP_STATUS_PREFIX} TEST_GENERATED")
            }
        }

        `when`("completing a step before generating test and implementation") {
            val session = createSession()
            moveToImplementation(session)
            val result = session.handle(CliWorkflowSession.COMMAND_COMPLETE_STEP)

            then("the command is rejected") {
                result.message.shouldContain(CliWorkflowSession.MESSAGE_ACTION_REJECTED_TOKEN)
            }
        }

        `when`("trying to complete a step when none remain") {
            val session = createSession()
            moveToImplementation(session)
            // complete all queued implementation steps
            session.handle("${CliWorkflowSession.COMMAND_GENERATE_TEST} class ExampleTest1")
            session.handle("${CliWorkflowSession.COMMAND_GENERATE_IMPL} class ExampleImpl1")
            session.handle(CliWorkflowSession.COMMAND_COMPLETE_STEP)
            session.handle("${CliWorkflowSession.COMMAND_GENERATE_TEST} class ExampleTest2")
            session.handle("${CliWorkflowSession.COMMAND_GENERATE_IMPL} class ExampleImpl2")
            session.handle(CliWorkflowSession.COMMAND_COMPLETE_STEP)
            session.handle("${CliWorkflowSession.COMMAND_GENERATE_TEST} class ExampleTest3")
            session.handle("${CliWorkflowSession.COMMAND_GENERATE_IMPL} class ExampleImpl3")
            session.handle(CliWorkflowSession.COMMAND_COMPLETE_STEP)
            val result = session.handle(CliWorkflowSession.COMMAND_COMPLETE_STEP)

            then("the command is rejected with no next step") {
                result.message.shouldContain(WorkflowRejectionReasons.NO_CURRENT_IMPLEMENTATION_STEP_TO_COMPLETE)
            }
        }

        `when`("entering an unknown command") {
            val session = createSession()
            val response = session.handle("unknown")

            then("the user is guided to help") {
                response.message.shouldContain(CliWorkflowSession.MESSAGE_UNKNOWN_COMMAND_PREFIX)
            }
        }
    }
})
