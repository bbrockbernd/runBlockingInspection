package com.example.simplerbdetection.Services

import com.example.simplerbdetection.CallGraph.FunctionNode
import com.example.simplerbdetection.CallGraph.RBGraph
import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.MyPsiUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

class GraphBuilder(private val project: Project) {
    private var totalFilesTodo: (Int) -> Unit = {}
    private var incrementFilesDone: () -> Unit = {}
    private var rbFileFound: (VirtualFile) -> Unit = {}
    private var relevantFiles: List<VirtualFile> = emptyList()
    
    private val rbGraph = RBGraph()
    
    fun buildGraph(): GraphBuilder {
        totalFilesTodo(relevantFiles.size)
        relevantFiles.forEachIndexed() { index, file ->
            println("Building graph: $index / ${relevantFiles.size}")
            // Search kotlin file for runBlocking calls, and generate tree
            MyPsiUtils.findRunBlockings(file, project).forEach { rb ->
                createSubtree(rb)
                rbFileFound(file)
            }
            // Search for async, and launch builders in case globalscope or android scopes are used
            MyPsiUtils.findNonBlockingBuilders(file, project).forEach { builder ->
                createSubtree(builder)
            }
            // suspend fun for completeness
            MyPsiUtils.findSuspendFuns(file, project).forEach { susFun ->
                if (susFun is KtNamedFunction)
                    exploreFunDeclaration(susFun, rbGraph.getOrCreateFunction(susFun))
            }
            incrementFilesDone()
        }
        return this
    }
    
    fun pruneGraph(): GraphBuilder {
        return this
    }
    
    fun getGraph(): RBGraph = rbGraph
    
    fun setRelevantFiles(relevantFiles: List<VirtualFile>): GraphBuilder {
        this.relevantFiles = relevantFiles
        return this
    }
    fun setIncrementFilesDoneFunction(incrementFilesDone: () -> Unit): GraphBuilder {
        this.incrementFilesDone = incrementFilesDone
        return this
    }

    fun setTotalFilesTodo(totalFilesTodo: (Int) -> Unit): GraphBuilder {
        this.totalFilesTodo = totalFilesTodo
        return this
    }
    
    fun setRbFileFound(fileFound: (VirtualFile) -> Unit): GraphBuilder {
        this.rbFileFound = fileFound
        return this
    }
    
    private fun createSubtree(builder: PsiElement) {
        //Add runBlocking root to graph
        val runBlockingNode = rbGraph.addBuilder(builder)
        // println("Adding runBlocking ${runBlockingNode.id}")
        exploreFunDeclaration(builder, runBlockingNode)
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
                val funFile = MyPsiUtils.getFileForElement(psiFn)
                if (!relevantFiles.contains(funFile)) return

                // Find all function overrides
                val overrides = mutableListOf<KtNamedFunction>(psiFn)
                psiFn.forEachOverridingElement { _, overrideFn ->
                    if (overrideFn is KtNamedFunction && relevantFiles.contains(MyPsiUtils.getFileForElement(overrideFn))) overrides.add(overrideFn)
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
}