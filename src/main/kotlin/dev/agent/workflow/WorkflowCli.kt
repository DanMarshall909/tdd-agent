package dev.agent.workflow

import java.io.BufferedReader
import java.io.InputStreamReader

data class CliCommandResult(val message: String, val exit: Boolean = false)

class CliWorkflowSession(private val cannedScenarios: List<Scenario> = defaultScenarios) {
    companion object {
        const val COMMAND_SUBMIT = "submit"
        const val COMMAND_GENERATE_SCENARIOS = "generate-scenarios"
        const val COMMAND_APPROVE_SCENARIOS = "approve-scenarios"
        const val COMMAND_COMPLETE_RESEARCH = "complete-research"
        const val COMMAND_PROPOSE_PLAN = "propose-plan"
        const val COMMAND_APPROVE_PLAN = "approve-plan"
        const val COMMAND_GENERATE_TEST = "generate-test"
        const val COMMAND_GENERATE_IMPL = "generate-impl"
        const val COMMAND_COMPLETE_STEP = "complete-step"
        const val COMMAND_STATUS = "status"
        const val COMMAND_HELP = "help"
        const val COMMAND_EXIT = "exit"
        const val COMMAND_QUIT = "quit"

        const val MESSAGE_UNKNOWN_COMMAND_PREFIX = "Unknown command"
        const val MESSAGE_STEP_STATUS_PREFIX = "Step status:"
        const val MESSAGE_PHASE_PREFIX = "Phase:"
        const val MESSAGE_ACTION_SUCCEEDED_TOKEN = "succeeded"
        const val MESSAGE_ACTION_REJECTED_TOKEN = "rejected"

        private val defaultScenarios = listOf(
            Scenario(
                name = "Manual workflow sample",
                steps = listOf(
                    Step(StepType.GIVEN, "the workspace is ready"),
                    Step(StepType.WHEN, "an action is requested"),
                    Step(StepType.THEN, "the feature validates"),
                ),
            ),
        )
    }

    private var state: WorkflowState = WorkflowState.initial()

    fun handle(input: String): CliCommandResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return CliCommandResult("Enter a command (type '$COMMAND_HELP' to list commands).")
        }
        val parts = trimmed.split(" ", limit = 2)
        val command = parts[0].lowercase()
        val args = parts.getOrNull(1) ?: ""
        return when (command) {
            COMMAND_SUBMIT -> handleSubmit(args)
            COMMAND_GENERATE_SCENARIOS -> handleGenerateScenarios()
            COMMAND_APPROVE_SCENARIOS -> handleTransition(
                "Approve scenarios",
            ) { dispatch(WorkflowEvent.ScenariosApproved) }
            COMMAND_COMPLETE_RESEARCH -> handleCompleteResearch(args)
            COMMAND_PROPOSE_PLAN -> handleProposePlan(args)
            COMMAND_APPROVE_PLAN -> handleTransition("Approve plan") { dispatch(WorkflowEvent.PlanApproved) }
            COMMAND_GENERATE_TEST -> handleGenerateTest(args)
            COMMAND_GENERATE_IMPL -> handleGenerateImplementation(args)
            COMMAND_COMPLETE_STEP -> handleTransition("Complete step") { dispatch(WorkflowEvent.StepCompleted) }
            COMMAND_STATUS -> CliCommandResult(describeDetailedState())
            COMMAND_HELP -> CliCommandResult(helpText())
            COMMAND_EXIT, COMMAND_QUIT -> CliCommandResult("Bye.", exit = true)
            else -> CliCommandResult(
                "$MESSAGE_UNKNOWN_COMMAND_PREFIX '$command'. Type '$COMMAND_HELP' to list commands.",
            )
        }
    }

    fun currentState(): WorkflowState = state

    private fun handleSubmit(args: String): CliCommandResult {
        if (args.isBlank()) {
            return CliCommandResult("Usage: submit <feature description> | <optional additional requirements>")
        }
        val (description, additional) = args.split("|", limit = 2)
            .map(String::trim)
            .let { splits ->
                val desc = splits.getOrNull(0).orEmpty()
                val add = splits.getOrNull(1)
                desc to add
            }
        if (description.isBlank()) {
            return CliCommandResult("Feature description cannot be empty.")
        }
        val result = dispatch(WorkflowEvent.FeatureSubmitted(description, additional?.ifBlank { null }))
        return formatTransition("Submit feature", result)
    }

    private fun handleGenerateScenarios(): CliCommandResult {
        if (cannedScenarios.isEmpty()) {
            return CliCommandResult("No canned scenarios configured.")
        }
        val result = dispatch(WorkflowEvent.ScenariosGenerated(cannedScenarios))
        return formatTransition("Generate scenarios", result)
    }

    private fun handleCompleteResearch(args: String): CliCommandResult {
        if (args.isBlank()) {
            return CliCommandResult("Usage: complete-research <summary>")
        }
        val result = dispatch(WorkflowEvent.ResearchCompleted(args.trim()))
        return formatTransition("Complete research", result)
    }

    private fun handleProposePlan(args: String): CliCommandResult {
        if (args.isBlank()) {
            return CliCommandResult("Usage: propose-plan <plan>")
        }
        val result = dispatch(WorkflowEvent.PlanProposed(args.trim()))
        return formatTransition("Propose plan", result)
    }

    private fun handleGenerateTest(args: String): CliCommandResult {
        val code = args.trim()
        if (code.isBlank()) {
            return CliCommandResult("Usage: generate-test <code>")
        }
        val result = dispatch(WorkflowEvent.TestGenerated(code))
        return formatTransition("Generate test", result)
    }

    private fun handleGenerateImplementation(args: String): CliCommandResult {
        val code = args.trim()
        if (code.isBlank()) {
            return CliCommandResult("Usage: generate-impl <code>")
        }
        val result = dispatch(WorkflowEvent.ImplementationGenerated(code))
        return formatTransition("Generate implementation", result)
    }

    private fun handleTransition(action: String, block: () -> TransitionResult): CliCommandResult {
        val result = block()
        return formatTransition(action, result)
    }

    private fun dispatch(event: WorkflowEvent): TransitionResult {
        val result = WorkflowStateMachine.reduce(state, event)
        if (result is TransitionResult.Success) {
            state = result.state
        }
        return result
    }

    private fun formatTransition(action: String, result: TransitionResult): CliCommandResult {
        return when (result) {
            is TransitionResult.Success -> {
                CliCommandResult("$action $MESSAGE_ACTION_SUCCEEDED_TOKEN\n${describeShortState()}")
            }
            is TransitionResult.Rejected -> {
                CliCommandResult("$action $MESSAGE_ACTION_REJECTED_TOKEN: ${result.reason}")
            }
        }
    }

    private fun describeShortState(): String {
        val description = state.requirements.featureDescription.ifBlank { "<unset>" }
        val remainingSteps = state.implementation.steps.size - state.implementation.currentStepIndex
        return "$MESSAGE_PHASE_PREFIX ${state.phase} | " +
            "$MESSAGE_STEP_STATUS_PREFIX ${state.implementation.stepStatus} | " +
            "Feature: $description | Remaining steps: $remainingSteps"
    }

    private fun describeDetailedState(): String {
        val feature = state.requirements.featureDescription.ifBlank { "<unset>" }
        val additional = state.requirements.additionalRequirements.orEmpty().ifBlank { "<none>" }
        val scenarios = if (state.requirements.scenarios.isEmpty()) {
            "none"
        } else {
            state.requirements.scenarios.joinToString { it.name }
        }
        val currentStep = state.implementation.currentStepOrNull()
        val stepDescription = currentStep?.let { "${it.type} ${it.text}" } ?: "<none>"
        val remainingSteps = state.implementation.steps.size - state.implementation.currentStepIndex
        return buildString {
            appendLine("$MESSAGE_PHASE_PREFIX ${state.phase}")
            appendLine("Feature: $feature")
            appendLine("Additional: $additional")
            appendLine("Scenarios: $scenarios")
            appendLine("$MESSAGE_STEP_STATUS_PREFIX ${state.implementation.stepStatus}")
            appendLine("Current step: $stepDescription")
            appendLine("Remaining implementation steps: $remainingSteps")
        }.trimEnd()
    }

    private fun helpText(): String {
        return """
            |Commands:
            |  $COMMAND_SUBMIT <description> | <optional additional>  Submit requirements
            |  $COMMAND_GENERATE_SCENARIOS                              Populate sample scenarios
            |  $COMMAND_APPROVE_SCENARIOS                               Move to research phase
            |  $COMMAND_COMPLETE_RESEARCH <summary>                     Finish research
            |  $COMMAND_PROPOSE_PLAN <plan>                             Record planning
            |  $COMMAND_APPROVE_PLAN                                    Start implementation
            |  $COMMAND_GENERATE_TEST <code>                            Mark test generated for current step
            |  $COMMAND_GENERATE_IMPL <code>                            Mark implementation generated for current step
            |  $COMMAND_COMPLETE_STEP                                   Mark current implementation step done
            |  $COMMAND_STATUS                                          Dump current state
            |  $COMMAND_HELP                                            Show this help text
            |  $COMMAND_EXIT                                            Quit
        """.trimMargin()
    }
}

fun main() {
    val session = CliWorkflowSession()
    println("TDD workflow CLI")
    println("Type '${CliWorkflowSession.COMMAND_HELP}' to list commands, '${CliWorkflowSession.COMMAND_EXIT}' to quit.")
    val reader: BufferedReader = BufferedReader(InputStreamReader(System.`in`))
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            continue
        }
        val result = session.handle(trimmed)
        println(result.message)
        if (result.exit) {
            break
        }
    }
    println("Goodbye.")
}
