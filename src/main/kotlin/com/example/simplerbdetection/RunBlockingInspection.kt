package com.example.simplerbdetection

import com.example.simplerbdetection.Services.DetectRunBlockingService
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.JobDescriptor
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.reference.RefFileImpl
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression


class RunBlockingInspection() : GlobalInspectionTool() {
    
    private val jobDescriptor = JobDescriptor("Analyzing runBlocking")
    
    enum class ExplorationLevel { STRICT, DECLARATION, ALL }

    override fun runInspection(
        scope: AnalysisScope,
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        
        manager.project.service<DetectRunBlockingService>().processProject(scope, {jobDescriptor.totalAmount = it}, {
            globalContext.incrementJobDoneAmount(jobDescriptor, RunBlockingInspectionBundle.message("analysis.graphbuilding.progress", jobDescriptor.doneAmount, jobDescriptor.totalAmount))
        }, explorationLevel)
        val badRunBlockings = manager.project.service<DetectRunBlockingService>().wholeProject()
        val rbFileMap = mutableMapOf<String, RefFileImpl>()
        badRunBlockings.forEach {
            val refEntity = rbFileMap.computeIfAbsent(it.element.containingFile.virtualFile.path)
            { _ -> RefFileImpl(it.element.containingFile, globalContext.refManager) }
            val expr: PsiElement =
                if (it.element is KtCallExpression && it.element.calleeExpression != null) it.element.calleeExpression!! 
                else it.element
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
    
    @JvmField
    var explorationLevel: ExplorationLevel = ExplorationLevel.DECLARATION 

    override fun getOptionsPane(): OptPane {
        return OptPane.pane(
            OptPane.dropdown("explorationLevel", RunBlockingInspectionBundle.message("presentation.settings.exploration.title"),
                OptPane.option(ExplorationLevel.STRICT, RunBlockingInspectionBundle.message("presentation.settings.exploration.option.strict")),
                OptPane.option(ExplorationLevel.DECLARATION, RunBlockingInspectionBundle.message("presentation.settings.exploration.option.declaration")), 
                OptPane.option(ExplorationLevel.ALL, RunBlockingInspectionBundle.message("presentation.settings.exploration.option.all"))
                )
        )
    }

    override fun getAdditionalJobs(context: GlobalInspectionContext): Array<JobDescriptor>? {
        return arrayOf(jobDescriptor)
    }

    override fun isGraphNeeded(): Boolean = false
}