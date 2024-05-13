package com.example.runBlockingInspection.services

import com.android.annotations.Trace
import com.example.runBlockingInspection.rbgraph.CallEdge
import com.example.runBlockingInspection.rbgraph.FunctionNode
import com.example.runBlockingInspection.rbgraph.GraphBuilder
import com.example.runBlockingInspection.rbgraph.RBGraph
import com.example.runBlockingInspection.utils.ElementFilters
import com.example.runBlockingInspection.utils.MyPsiUtils
import com.example.runBlockingInspection.RunBlockingInspection
import com.example.runBlockingInspection.RunBlockingInspectionBundle
import com.example.runBlockingInspection.TraceElement
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.util.collectionUtils.concat

internal class DetectRunBlockingServiceImpl(override val project: Project) : DetectRunBlockingService,
    ProblemsProvider {
    private val relevantFiles = mutableListOf<VirtualFile>()
    private val rbFiles = mutableSetOf<VirtualFile>()
    private var rbGraph = RBGraph()

    private val resultsMemo = mutableMapOf<String, List<TraceElement>?>()

    /**
     * Analyzes the given [element] to determine if it is running inside a coroutine and returns a stack trace
     * if it is. This method assumes [element] is runBlocking.
     * The stack trace is a potential call stack that could lead from a coroutine to this runBlocking.
     *
     * @param element The PSI element to analyze (runBlocking).
     * @return A list of pairs representing the function name and its call/decl site if the given [element]
     *         is running inside a coroutine, null otherwise.
     */
    override fun checkRunBlocking(element: PsiElement): List<TraceElement>? =
        MyPsiUtils.getUrl(element)?.let { resultsMemo.computeIfAbsent(it) { analyzeRunBlocking(element) } }

    private fun analyzeRunBlocking(element: PsiElement): List<TraceElement>? {
        // Find first interesting parent if any, Aka function definition or async builder
        val psiFunOrBuilder = MyPsiUtils.findParent(element.parent, {
            it is KtNamedFunction
                    || ElementFilters.launchBuilder.isAccepted(it)
                    || ElementFilters.asyncBuilder.isAccepted(it)
                    || ElementFilters.runBlockingBuilderInvocation.isAccepted(it)
        }, { 
            it is KtLambdaExpression 
                    && !ElementFilters.lambdaAsArgForInlineFun.isAccepted(it)
                    && !ElementFilters.isSuspendLambda.isAccepted(it)
        })

        // If found element is function def, check if it runs in coroutine
        if (psiFunOrBuilder is KtNamedFunction) {
            // Check if function exists in graph (and therefore runs in coroutine)
            if (rbGraph.containsFun(FunctionNode.generateId(psiFunOrBuilder))) {
                val funNode = rbGraph.getFunction(FunctionNode.generateId(psiFunOrBuilder))
                if (funNode.asyncContext) {
                    // Find the shortest path from async primitive to this runBlocking
                    val callEdgeTrace = rbGraph.findBuilderBFS(funNode)
                    return generateStackTrace(callEdgeTrace, funNode, element)
                }
                return null
            }
            // else if element is builder
        } else if (psiFunOrBuilder != null && psiFunOrBuilder is KtCallExpression) {
            val ktFun: KtNamedFunction = psiFunOrBuilder.calleeExpression?.reference?.resolve() as KtNamedFunction
            val fqName: String = ktFun.fqName.toString()
            return listOf(
                TraceElement(
                    fqName, 
                    MyPsiUtils.getUrl(psiFunOrBuilder) ?: "", 
                    MyPsiUtils.getFileAndLine(psiFunOrBuilder) ?: ""
                ),
                TraceElement(
                    RunBlockingInspectionBundle.message("analysis.found.runblocking"),
                    MyPsiUtils.getUrl(element) ?: "",
                    MyPsiUtils.getFileAndLine(element) ?: ""
                )
            )
        }
        return null
    }

    /**
     * Auxilary function which maps trace to something presentable.
     */
    private fun generateStackTrace(
        callEdgeTrace: List<CallEdge>,
        funNode: FunctionNode,
        element: PsiElement
    ): MutableList<TraceElement> {
        val stackTrace =
            if (callEdgeTrace.size != 0) mutableListOf<TraceElement>(TraceElement(
                    callEdgeTrace[0].parent.fqName,
                    callEdgeTrace[0].parent.declarationSite,
                    callEdgeTrace[0].parent.fileAndLine))
            else mutableListOf<TraceElement>(TraceElement(funNode.fqName, funNode.declarationSite, funNode.fileAndLine))

        for (i in 0..<callEdgeTrace.size) {
            stackTrace.add(TraceElement(callEdgeTrace[i].child.fqName, callEdgeTrace[i].callSite, callEdgeTrace[i].fileAndLine))
        }
        stackTrace.add(TraceElement(
                RunBlockingInspectionBundle.message("analysis.found.runblocking"),
                MyPsiUtils.getUrl(element) ?: "",
            MyPsiUtils.getFileAndLine(element) ?: ""
            ))
        return stackTrace
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
        checkAllRunBlockings()
    }

    override fun checkAllRunBlockings(): List<DetectRunBlockingService.RunBlockingProblem> {
        return rbFiles.fold(mutableListOf()) { results, file ->
            results.concat(checkRunBlockingsForFile(file))!!.toMutableList()
        }
    }

    private fun checkRunBlockingsForFile(file: VirtualFile): List<DetectRunBlockingService.RunBlockingProblem> {
        if (!rbFiles.contains(file)) return emptyList()
        val rbs: MutableList<DetectRunBlockingService.RunBlockingProblem> = mutableListOf()
        MyPsiUtils.findRunBlockings(file, project).forEach { rb ->
            val stackTrace = checkRunBlocking(rb)
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

    /**
     * Get all kt files in scope.
     */
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