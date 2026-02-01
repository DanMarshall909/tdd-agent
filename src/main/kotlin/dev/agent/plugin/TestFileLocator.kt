package dev.agent.plugin

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile

internal object TestFileLocator {
    fun findTestFile(project: Project, productionFile: PsiFile): PsiFile? {
        val virtualFile = productionFile.virtualFile ?: return null
        val fileIndex = ProjectFileIndex.getInstance(project)
        if (fileIndex.isInTestSourceContent(virtualFile)) {
            return productionFile
        }

        val packagePath = getPackagePath(productionFile)
        val baseName = virtualFile.nameWithoutExtension
        val candidates = listOf("${baseName}Test.kt", "${baseName}Spec.kt")
        val psiManager = PsiManager.getInstance(project)
        val testRoots = getTestSourceRoots(project, fileIndex)

        for (root in testRoots) {
            for (candidate in candidates) {
                val file = findRelativeFile(root, packagePath, candidate)
                if (file != null) {
                    return psiManager.findFile(file)
                }
            }
        }

        return null
    }

    fun createTestFile(project: Project, productionFile: PsiFile, suffix: String = "Test"): PsiFile? {
        val virtualFile = productionFile.virtualFile ?: return null
        val fileIndex = ProjectFileIndex.getInstance(project)
        val testRoots = getTestSourceRoots(project, fileIndex)
        if (testRoots.isEmpty()) {
            return null
        }

        val packageName = getPackageName(productionFile)
        val packagePath = getPackagePath(productionFile)
        val baseName = virtualFile.nameWithoutExtension
        val className = "$baseName$suffix"
        val fileName = "$className.kt"
        val root = testRoots.first()

        return WriteCommandAction.writeCommandAction(project).compute<PsiFile?, RuntimeException> {
            val targetDir = if (packagePath.isBlank()) {
                root
            } else {
                VfsUtil.createDirectoryIfMissing(root, packagePath) ?: return@compute null
            }

            val psiDir = PsiManager.getInstance(project).findDirectory(targetDir) ?: return@compute null
            val existing = psiDir.findFile(fileName)
            if (existing != null) {
                return@compute existing
            }

            val created = psiDir.createFile(fileName)
            val content = buildTestFileContent(packageName, className)
            val document = PsiDocumentManager.getInstance(project).getDocument(created)
            if (document != null) {
                document.setText(content)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            } else {
                VfsUtil.saveText(created.virtualFile, content)
            }

            created
        }
    }

    private fun getTestSourceRoots(project: Project, fileIndex: ProjectFileIndex): List<VirtualFile> {
        return ProjectRootManager.getInstance(project)
            .contentSourceRoots
            .filter { fileIndex.isInTestSourceContent(it) }
    }

    private fun getPackagePath(file: PsiFile): String {
        val packageName = getPackageName(file)
        return if (packageName.isBlank()) "" else packageName.replace('.', '/')
    }

    private fun getPackageName(file: PsiFile): String {
        return (file as? KtFile)?.packageFqName?.asString().orEmpty()
    }

    private fun findRelativeFile(root: VirtualFile, packagePath: String, fileName: String): VirtualFile? {
        val parts = ArrayList<String>()
        if (packagePath.isNotBlank()) {
            parts.addAll(packagePath.split('/'))
        }
        parts.add(fileName)
        return VfsUtil.findRelativeFile(root, *parts.toTypedArray())
    }

    private fun buildTestFileContent(packageName: String, className: String): String {
        val header = if (packageName.isBlank()) "" else "package $packageName\n\n"
        return buildString {
            append(header)
            append("import io.kotest.core.spec.style.BehaviorSpec\n\n")
            append("class $className : BehaviorSpec({\n")
            append("})\n")
        }
    }
}
