package com.example.simplerbdetection.Services

import com.example.simplerbdetection.CallGraph.FunctionNode
import com.example.simplerbdetection.CallGraph.GraphBuilder
import com.example.simplerbdetection.CallGraph.RBGraph
import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.MyPsiUtils
import com.example.simplerbdetection.RunBlockingInspection
import com.example.simplerbdetection.RunBlockingInspectionBundle
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.util.collectionUtils.concat

internal class DetectRunBlockingServiceImpl(override val project: Project) : DetectRunBlockingService,
    ProblemsProvider {
    private val relevantFiles = mutableListOf<VirtualFile>()
    private val rbFiles = mutableSetOf<VirtualFile>()
    private var rbGraph = RBGraph()

    private val resultsMemo = mutableMapOf<String, List<Pair<String, String>>?>()

    override fun analyzeRunBlocking(element: PsiElement): List<Pair<String, String>>? =
        MyPsiUtils.getUrl(element)?.let { resultsMemo.computeIfAbsent(it) { analyzeRunBlockingImpl(element) } }


    /**
     * Analyzes the given [element] to determine if it is running inside a coroutine and returns a stack trace
     * if it is. This method assumes [element] is runBlocking.
     * The stack trace is a potential call stack that could lead from a coroutine to this runBlocking.
     *
     * @param element The PSI element to analyze (runBlocking).
     * @return A list of pairs representing the function name and its call/decl site if the given [element]
     *         is running inside a coroutine, null otherwise.
     */
    private fun analyzeRunBlockingImpl(element: PsiElement): List<Pair<String, String>>? {
        // Find first interesting parent if any, Aka function definition or async builder
        val psiFunOrBuilder = PsiTreeUtil.findFirstParent(element, true) {
            it is KtNamedFunction
                    || ElementFilters.launchBuilder.isAccepted(it)
                    || ElementFilters.asyncBuilder.isAccepted(it)
                    || ElementFilters.runBlockingBuilderInvocation.isAccepted(it)
        }

        // If found element is function def, check if it runs in coroutine
        if (psiFunOrBuilder is KtNamedFunction) {
            // Check if function exists in graph (and therefore runs in coroutine)
            if (rbGraph.containsFun(FunctionNode.generateId(psiFunOrBuilder))) {
                val funNode = rbGraph.getFunction(FunctionNode.generateId(psiFunOrBuilder))
                if (funNode.asyncContext) {
                    // Find the shortest path from async primitive to this runBlocking
                    val callEdgeTrace = rbGraph.findBuilderBFS(funNode)

                    val stackTrace =
                        if (callEdgeTrace.size != 0) mutableListOf<Pair<String, String>>(
                            Pair(
                                callEdgeTrace[0].parent.fqName,
                                callEdgeTrace[0].parent.declarationSite
                            )
                        )
                        else mutableListOf<Pair<String, String>>(Pair(funNode.fqName, funNode.declarationSite))

                    for (i in 0..<callEdgeTrace.size) {
                        stackTrace.add(Pair(callEdgeTrace[i].child.fqName, callEdgeTrace[i].callSite))
                    }
                    stackTrace.add(
                        Pair(
                            RunBlockingInspectionBundle.message("analysis.found.runblocking"),
                            MyPsiUtils.getUrl(element) ?: ""
                        )
                    )


                    return stackTrace
                }
                return null
            }
            // else if element is builder
        } else if (psiFunOrBuilder != null && psiFunOrBuilder is KtCallExpression) {
            val ktFun: KtNamedFunction = psiFunOrBuilder.calleeExpression?.reference?.resolve() as KtNamedFunction
            val fqName: String = ktFun.fqName.toString()
            return listOf(
                Pair(fqName, MyPsiUtils.getUrl(psiFunOrBuilder) ?: ""),
                Pair(
                    RunBlockingInspectionBundle.message("analysis.found.runblocking"),
                    MyPsiUtils.getUrl(element) ?: ""
                )
            )
        }
        return null
    }

    override fun processProject(
        scope: AnalysisScope?,
        totalFilesTodo: (Int) -> Unit,
        incrementFilesDone: () -> Unit,
        level: RunBlockingInspection.ExplorationLevel
    ) {
        // Get editable kotlin files
        updateRelevantFiles(scope)
        fullAnalysis(totalFilesTodo, incrementFilesDone, level)
        wholeProject()
    }

    override fun wholeProject(): List<DetectRunBlockingService.RunBlockingProblem> {
        return rbFiles.fold(mutableListOf()) { results, file ->
            results.concat(checkRunBlockingsForFile(file))!!.toMutableList()
        }
    }

    private fun checkRunBlockingsForFile(file: VirtualFile): List<DetectRunBlockingService.RunBlockingProblem> {
        if (!rbFiles.contains(file)) return emptyList()
        val rbs: MutableList<DetectRunBlockingService.RunBlockingProblem> = mutableListOf()
        MyPsiUtils.findRunBlockings(file, project).forEach { rb ->
            val stackTrace = analyzeRunBlocking(rb)
            if (stackTrace != null) {
                rbs.add(
                    DetectRunBlockingService.RunBlockingProblem(rb, stackTrace)
                )
            }
        }
        return rbs
    }

    // Full project analysis for nested run blocking calls 
    private fun fullAnalysis(
        totalFilesTodo: (Int) -> Unit,
        incrementFilesDone: (() -> Unit),
        level: RunBlockingInspection.ExplorationLevel
    ) {
        // Clear rb graph
        rbFiles.clear()
        resultsMemo.clear()

        rbGraph = GraphBuilder(project)
            .setRbFileFound { file -> rbFiles.add(file) }
            .setIncrementFilesDoneFunction(incrementFilesDone)
            .setTotalFilesTodo(totalFilesTodo)
            .setRelevantFiles(relevantFiles)
            .setExplorationLevel(level)
            .buildGraph()
    }

    private fun updateRelevantFiles(scope: AnalysisScope?): MutableList<VirtualFile> {
        relevantFiles.clear()

        ProjectFileIndex.getInstance(project).iterateContent {
            if (it.fileType is KotlinFileType && scope?.contains(it) != false) {
                relevantFiles.add(it)
            }
            true
        }
        return relevantFiles
    }
}