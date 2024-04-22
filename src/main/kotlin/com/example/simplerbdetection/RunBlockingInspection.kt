package com.example.simplerbdetection

import com.example.simplerbdetection.Services.DetectRunBlockingService
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.JobDescriptor
import com.intellij.codeInspection.reference.RefFileImpl
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression


class RunBlockingInspection() : GlobalInspectionTool() {
    
    private val jobDescriptor = JobDescriptor("Analyzing runBlocking")

    override fun runInspection(
        scope: AnalysisScope,
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        
        manager.project.service<DetectRunBlockingService>().analyseProject(scope, {jobDescriptor.totalAmount = it}, {
            globalContext.incrementJobDoneAmount(jobDescriptor, "Kt file ${jobDescriptor.doneAmount}/${jobDescriptor.totalAmount}")
        })
        val badRunBlockings = manager.project.service<DetectRunBlockingService>().wholeProject()
        val rbFileMap = mutableMapOf<String, RefFileImpl>()
        badRunBlockings.forEach {
            val refEntity = rbFileMap.computeIfAbsent(it.element.containingFile.virtualFile.path)
            { _ -> RefFileImpl(it.element.containingFile, globalContext.refManager) }
            val expr: PsiElement =
                if (it.element is KtCallExpression && it.element.calleeExpression != null) it.element.calleeExpression!! else it.element
            problemDescriptionsProcessor.addProblemElement(
                refEntity,
                manager.createProblemDescriptor(
                    expr,
                    "RunBlocking builder called from coroutine",
                    false,
                    arrayOf(),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            )
        }
    }
    
//    var excludeTestClasses = false

//    override fun getOptionsPane(): OptPane {
//        return pane(
//            checkbox(
//                "excludeTestClasses",
//                "Exclude test classes"
//            )
//        )
//    }

    override fun getAdditionalJobs(context: GlobalInspectionContext): Array<JobDescriptor>? {
        return arrayOf(jobDescriptor)
    }

    override fun isGraphNeeded(): Boolean = false
}