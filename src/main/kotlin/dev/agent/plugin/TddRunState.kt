package dev.agent.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class TddRunState(project: Project) {
    var lastTestFile: VirtualFile? = null
    var lastTestClassFqn: String? = null
    var lastProductionFile: VirtualFile? = null

    companion object {
        fun getInstance(project: Project): TddRunState = project.getService(TddRunState::class.java)
    }
}
