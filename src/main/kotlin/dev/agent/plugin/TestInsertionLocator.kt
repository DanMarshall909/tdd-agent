package dev.agent.plugin

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeEntry

internal object TestInsertionLocator {
    fun findBehaviorSpecClass(testFile: KtFile): KtClassOrObject? {
        return findBehaviorSpec(testFile)
    }

    fun findInsertionPoint(testFile: KtFile): PsiElement? {
        val behaviorSpec = findBehaviorSpec(testFile) ?: return null
        val lambda = findBehaviorSpecLambda(behaviorSpec) ?: return null
        val body = lambda.bodyExpression ?: return lambda

        val givenCalls = PsiTreeUtil.collectElementsOfType(body, KtCallExpression::class.java)
            .filter { it.calleeExpression?.text == "given" }
        if (givenCalls.isNotEmpty()) {
            return givenCalls.maxByOrNull { it.textRange.startOffset }
        }

        val statements = body.statements
        return if (statements.isNotEmpty()) {
            statements.last()
        } else {
            body.lBrace ?: body
        }
    }

    private fun findBehaviorSpec(testFile: KtFile): KtClassOrObject? {
        return testFile.declarations
            .filterIsInstance<KtClassOrObject>()
            .firstOrNull { declaration ->
                declaration.superTypeListEntries.any { entry ->
                    when (entry) {
                        is KtSuperTypeCallEntry -> isBehaviorSpecType(entry.calleeExpression?.text)
                        is KtSuperTypeEntry -> isBehaviorSpecType(entry.typeReference?.text)
                        else -> false
                    }
                }
            }
    }

    private fun findBehaviorSpecLambda(declaration: KtClassOrObject): KtLambdaExpression? {
        val callEntry = declaration.superTypeListEntries
            .filterIsInstance<KtSuperTypeCallEntry>()
            .firstOrNull { entry -> isBehaviorSpecType(entry.calleeExpression?.text) }
            ?: return null

        val lambdaArg = callEntry.lambdaArguments.firstOrNull()?.getLambdaExpression()
        if (lambdaArg != null) {
            return lambdaArg
        }

        val valueArg = callEntry.valueArguments
            .mapNotNull { it.getArgumentExpression() as? KtLambdaExpression }
            .firstOrNull()

        return valueArg
    }

    private fun isBehaviorSpecType(typeText: String?): Boolean {
        if (typeText.isNullOrBlank()) {
            return false
        }
        return typeText == "BehaviorSpec" || typeText.endsWith(".BehaviorSpec")
    }
}
