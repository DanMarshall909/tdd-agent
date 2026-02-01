package dev.agent.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.agent.CodeInserter
import dev.agent.CodeRunner
import dev.agent.LlmAdapter
import dev.agent.OpenCodeAdapter
import dev.agent.TddOrchestrator
import java.time.Duration

/**
 * IntelliJ project-level service for dependency injection.
 * Provides lazy initialization of TDD components.
 */
@Service(Service.Level.PROJECT)
class TddService(private val project: Project) {
    private val llmAdapter: LlmAdapter by lazy {
        OpenCodeAdapter(model = null, timeout = Duration.ofMinutes(5))
    }

    private val codeRunner: CodeRunner by lazy {
        IdeCodeRunner(project)
    }

    private val codeInserter: CodeInserter by lazy {
        IdeCodeInserter(project)
    }

    val orchestrator: TddOrchestrator by lazy {
        TddOrchestrator(llmAdapter, codeRunner, codeInserter)
    }

    companion object {
        fun getInstance(project: Project): TddService = project.getService(TddService::class.java)
    }
}
