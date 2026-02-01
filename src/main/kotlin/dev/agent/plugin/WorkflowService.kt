package dev.agent.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.agent.workflow.TransitionResult
import dev.agent.workflow.WorkflowEvent
import dev.agent.workflow.WorkflowState
import dev.agent.workflow.WorkflowStateMachine

@Service(Service.Level.PROJECT)
class WorkflowService {
    private var state: WorkflowState = WorkflowState.initial()

    fun dispatch(event: WorkflowEvent): TransitionResult {
        val result = WorkflowStateMachine.reduce(state, event)
        if (result is TransitionResult.Success) {
            state = result.state
        }
        return result
    }

    fun getState(): WorkflowState = state

    companion object {
        fun getInstance(project: Project): WorkflowService = project.getService(WorkflowService::class.java)
    }
}
