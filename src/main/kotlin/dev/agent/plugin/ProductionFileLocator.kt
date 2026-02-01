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
        val testFileName = virtualFile.nameWithoutExtension
        val psiManager = PsiManager.getInstance(project)
        val sourceRoots = getSourceRoots(project, fileIndex)

        // Try to find production file by progressively removing test suffixes
        for (root in sourceRoots) {
            // First try exact match (in case test and production have same name)
            findRelativeFile(root, packagePath, "$testFileName.kt")?.let {
                return psiManager.findFile(it)
            }

            // Then try removing Test suffix
            if (testFileName.endsWith("Test")) {
                val withoutTest = testFileName.removeSuffix("Test")
                findRelativeFile(root, packagePath, "$withoutTest.kt")?.let {
                    return psiManager.findFile(it)
                }
            }

            // Then try removing Spec suffix
            if (testFileName.endsWith("Spec")) {
                val withoutSpec = testFileName.removeSuffix("Spec")
                findRelativeFile(root, packagePath, "$withoutSpec.kt")?.let {
                    return psiManager.findFile(it)
                }
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

    private fun findRelativeFile(root: VirtualFile, packagePath: String, fileName: String): VirtualFile? {
        val parts = ArrayList<String>()
        if (packagePath.isNotBlank()) {
            parts.addAll(packagePath.split('/'))
        }
        parts.add(fileName)
        return VfsUtil.findRelativeFile(root, *parts.toTypedArray())
    }
}
