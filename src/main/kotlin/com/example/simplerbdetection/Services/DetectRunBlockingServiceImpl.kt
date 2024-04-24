package com.example.simplerbdetection.Services

import com.example.simplerbdetection.CallGraph.FunctionNode
import com.example.simplerbdetection.CallGraph.RBGraph
import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.MyPsiUtils
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.util.collectionUtils.concat

internal class DetectRunBlockingServiceImpl(override val project: Project) : DetectRunBlockingService, ProblemsProvider {
    private val relevantFiles = mutableListOf<VirtualFile>()
    private val rbFiles = mutableSetOf<VirtualFile>()
    private val rbGraph = RBGraph()

    /**
     * Analyzes the given [element] to determine if it is running inside a coroutine and returns a stack trace
     * if it is. This method assumes [element] is runBlocking. 
     * The stack trace is a potential call stack that could lead from a coroutine to this runBlocking.
     *
     * @param element The PSI element to analyze (runBlocking).
     * @return A list of pairs representing the function name and its call/decl site if the given [element]
     *         is running inside a coroutine, null otherwise.
     */
    override fun analyzeRunBlocking(element: PsiElement): List<Pair<String, String>>? {
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
                    val nodeTrace = rbGraph.findBuilderBFS(funNode)
                    val stackTrace = mutableListOf<Pair<String, String>>(Pair(nodeTrace[0].fqName, nodeTrace[0].declarationSite))
                    for (i in 1..<nodeTrace.size) {
                        stackTrace.add(Pair(nodeTrace[i].fqName, nodeTrace[i-1].getCallSiteFor(nodeTrace[i])))
                    }
                    stackTrace.add(Pair("--> runBlocking", MyPsiUtils.getUrl(element)?: ""))
                    return stackTrace
                }  
                return null
            }
            // else if element is builder
        } else if (psiFunOrBuilder != null && psiFunOrBuilder is KtCallExpression) {
            val ktFun: KtNamedFunction = psiFunOrBuilder.calleeExpression?.reference?.resolve() as KtNamedFunction
            val fqName: String = ktFun.fqName.toString()
            return listOf(Pair(fqName, MyPsiUtils.getUrl(psiFunOrBuilder)?: ""), Pair("--> runBlocking", MyPsiUtils.getUrl(element)?: ""))
        }
        return null
    }

    override fun analyseProject(scope: AnalysisScope?, totalFilesTodo: ((Int) -> Unit), incrementFilesDone: (() -> Unit)) {
        // Get editable kotlin files
        updateRelevantFiles(scope)
        fullAnalysis(totalFilesTodo, incrementFilesDone)
        wholeProject()
    }

    override fun wholeProject(): List<DetectRunBlockingService.RunBlockingProblem> {
        return rbFiles.fold(mutableListOf()) { results, file -> results.concat(checkRunBlockingsForFile(file))!!.toMutableList() } 
    }

    private fun checkRunBlockingsForFile(file: VirtualFile): List<DetectRunBlockingService.RunBlockingProblem> {
        if (!rbFiles.contains(file)) return emptyList()
        val rbs: MutableList<DetectRunBlockingService.RunBlockingProblem> = mutableListOf()
        findRunBlockings(file).forEach { rb ->
            val stackTrace = analyzeRunBlocking(rb)
            if (stackTrace != null) {
                val lineNr = MyPsiUtils.getLineNumber(rb)
                rbs.add(
                    DetectRunBlockingService.RunBlockingProblem(rb, stackTrace))
                println("\n\n------------- Found runBlocking in coroutine ------------------------------")
                stackTrace.forEach { println("  $it") }
                println("---> RB in async context in file ${file.path}:$lineNr")
            }
        }
        return rbs
    }
    
    // Full project analysis for nested run blocking calls 
    private fun fullAnalysis(totalFilesTodo: (Int) -> Unit, incrementFilesDone: (() -> Unit)) {
        // Clear rb graph
        rbGraph.clear()
        rbFiles.clear()

        totalFilesTodo(relevantFiles.size)
        relevantFiles.forEachIndexed() { index, file ->
            println("Building graph: $index / ${relevantFiles.size}")
            // Search kotlin file for runBlocking calls, and generate tree
            findRunBlockings(file).forEach { rb ->
                createSubtree(rb)
                rbFiles.add(file)
            }
            // Search for async, and launch builders in case globalscope or android scopes are used
            findNonBlockingBuilders(file).forEach { builder -> 
                createSubtree(builder)
            }
            // suspend fun for completeness
            findSuspendFuns(file).forEach { susFun ->
                if (susFun is KtNamedFunction) 
                    exploreFunDeclaration(susFun, rbGraph.getOrCreateFunction(susFun))
            }
            incrementFilesDone()
        }
    }

    private fun createSubtree(builder: PsiElement) {
        //Add runBlocking root to graph
        val runBlockingNode = rbGraph.addBuilder(builder)
        // println("Adding runBlocking ${runBlockingNode.id}")
        exploreFunDeclaration(builder, runBlockingNode)
    }

    private fun getFileForElement(psiElement: PsiElement): VirtualFile {
        val ktFile = PsiTreeUtil.findFirstParent(psiElement) { it is KtFile } as KtFile
        return ktFile.virtualFile
    }

    private fun exploreFunDeclaration(currentPsiEl: PsiElement, currentNode: FunctionNode) {
        // If explored stop
        if (currentNode.visited) return
        currentNode.visited = true
        //Find all calls from this runBlocking context
        val methodCalls = MyPsiUtils.findAllChildren(currentPsiEl, { it is KtCallExpression }, 
            { ElementFilters.launchBuilder.isAccepted(it) 
                    || ElementFilters.asyncBuilder.isAccepted(it) 
                    || ElementFilters.runBlockingBuilderInvocation.isAccepted(it) 
            }).filterIsInstance<KtCallExpression>()

        for (call in methodCalls) {
            // Find method decl for call
            //TODO fix deprc call
            val psiFn = call.calleeExpression?.reference?.resolve()
            if (psiFn is KtNamedFunction) {
                // if method in non-relevant file skip call.calleeExpression.reference.resolve()
                val funFile = getFileForElement(psiFn)
                if (!relevantFiles.contains(funFile)) return

                val overrides = mutableListOf<KtNamedFunction>(psiFn)
                psiFn.forEachOverridingElement { _, overrideFn ->
                    if (relevantFiles.contains(getFileForElement(overrideFn)) && overrideFn is KtNamedFunction) overrides.add(overrideFn)
                    true
                }
                // Get or create function node and explore
                overrides.forEach { fn ->
                    val functionNode = rbGraph.getOrCreateFunction(fn)
                    FunctionNode.connect(currentNode, functionNode, MyPsiUtils.getUrl(call)!!)
                    exploreFunDeclaration(fn, functionNode)
                }
            }
        }
    }

    private fun findRunBlockings(file: VirtualFile): List<PsiElement> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
        return MyPsiUtils.findAllChildren(psiFile) { ElementFilters.runBlockingBuilderInvocation.isAccepted(it) }
    }
    
    private fun findNonBlockingBuilders(file: VirtualFile): List<PsiElement> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
        return MyPsiUtils.findAllChildren(psiFile) { ElementFilters.launchBuilder.isAccepted(it) || ElementFilters.asyncBuilder.isAccepted(it) }
    }
    
    private fun findSuspendFuns(file: VirtualFile): List<PsiElement> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
        return MyPsiUtils.findAllChildren(psiFile) { ElementFilters.suspendFun.isAccepted(it) }
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