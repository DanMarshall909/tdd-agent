package dev.agent.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile

internal object ProductionFileLocator {
    fun findProductionFile(project: Project, testFile: PsiFile): PsiFile? {
        val virtualFile = testFile.virtualFile ?: return null
        val fileIndex = ProjectFileIndex.getInstance(project)
        if (!fileIndex.isInTestSourceContent(virtualFile)) {
            return testFile
        }

        val packagePath = getPackagePath(testFile)
        val baseName = getBaseName(virtualFile.nameWithoutExtension)
        val psiManager = PsiManager.getInstance(project)
        val sourceRoots = getSourceRoots(project, fileIndex)

        for (root in sourceRoots) {
            val file = findRelativeFile(root, packagePath, "$baseName.kt")
            if (file != null) {
                return psiManager.findFile(file)
            }
        }

        return null
    }

    private fun getSourceRoots(project: Project, fileIndex: ProjectFileIndex): List<VirtualFile> {
        return ProjectRootManager.getInstance(project)
            .contentSourceRoots
            .filter { !fileIndex.isInTestSourceContent(it) }
    }

    private fun getPackagePath(file: PsiFile): String {
        val packageName = (file as? KtFile)?.packageFqName?.asString().orEmpty()
        return if (packageName.isBlank()) "" else packageName.replace('.', '/')
    }

    private fun getBaseName(nameWithoutExtension: String): String {
        return when {
            nameWithoutExtension.endsWith("Test") -> nameWithoutExtension.removeSuffix("Test")
            nameWithoutExtension.endsWith("Spec") -> nameWithoutExtension.removeSuffix("Spec")
            else -> nameWithoutExtension
        }
    }

    private fun findRelativeFile(root: VirtualFile, packagePath: String, fileName: String): VirtualFile? {
        val parts = ArrayList<String>()
        if (packagePath.isNotBlank()) {
            parts.addAll(packagePath.split('/'))
        }
        parts.add(fileName)
        return VfsUtil.findRelativeFile(root, *parts.toTypedArray())
    }
}
