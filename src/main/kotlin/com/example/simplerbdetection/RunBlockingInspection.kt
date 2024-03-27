package com.example.simplerbdetection

import com.example.simplerbdetection.Actions.RunBlockingQuickFix
import com.example.simplerbdetection.Services.DetectRunBlockingService
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.*
import com.intellij.codeInspection.reference.RefElement
import com.intellij.codeInspection.reference.RefEntity
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

class RunBlockingInspection() : GlobalInspectionTool() {

    override fun runInspection(
        scope: AnalysisScope,
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        manager.project.service<DetectRunBlockingService>().analyseProject()
        super.runInspection(scope, manager, globalContext, problemDescriptionsProcessor)
    }

    
    override fun checkElement(
        refEntity: RefEntity,
        scope: AnalysisScope,
        manager: InspectionManager,
        globalContext: GlobalInspectionContext
    ): Array<CommonProblemDescriptor>? {
        
        
        if (refEntity is RefElement) {
            val psiElement = refEntity.psiElement
            if (psiElement is KtFile) {
                val problemsList: MutableList<ProblemDescriptor> = mutableListOf()
                val runBlockings = manager.project.service<DetectRunBlockingService>().getRunBlockingInCoroutines(psiElement.virtualFile)
                for (rb in runBlockings) {
                    if (rb.element is KtCallExpression && rb.element.calleeExpression != null) {
                        val expr = rb.element.calleeExpression as PsiElement
                        problemsList.add(manager.createProblemDescriptor(expr, getDescription(rb.stacTrace), false, arrayOf(
                            RunBlockingQuickFix()
                        ), ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
                    }
                }
                return problemsList.toTypedArray()
            }
        }
        return null
    }
    
    private fun getDescription(callStack: List<String>): String {
        return buildString {
            append("<html><h>RunBlocking in coroutine</h><p>")
            callStack.forEach { append("$it<br>") }
            append("</p></html>")
        }
    }

    override fun isGraphNeeded(): Boolean = false
}