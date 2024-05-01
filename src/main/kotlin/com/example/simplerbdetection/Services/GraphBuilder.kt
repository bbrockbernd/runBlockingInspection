package com.example.simplerbdetection.Services

import com.example.simplerbdetection.CallGraph.CallEdge
import com.example.simplerbdetection.CallGraph.FunctionNode
import com.example.simplerbdetection.CallGraph.RBGraph
import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.MyPsiUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingElement
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.*

class GraphBuilder(private val project: Project) {
    private var totalFilesTodo: (Int) -> Unit = {}
    private var incrementFilesDone: () -> Unit = {}
    private var rbFileFound: (VirtualFile) -> Unit = {}
    private var relevantFiles: List<VirtualFile> = emptyList()
    private var urlToVirtualFileMap: MutableMap<String, VirtualFile> = mutableMapOf()
    
    private val rbGraph = RBGraph()

    fun getGraph(): RBGraph = rbGraph

    fun setRelevantFiles(relevantFiles: List<VirtualFile>): GraphBuilder {
        this.relevantFiles = relevantFiles
        relevantFiles.forEach {urlToVirtualFileMap[it.url] = it}
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
        // while until graph is unchanged for now only one call
        pruneEdgesRound()
        pruneNodesRound()
        
        
        return this
    }
    
    private fun pruneEdgesRound() {
        val weakEdges = rbGraph.edges.filter {!it.strongCall}
        weakEdges.forEach {
            if (!verifyEdge(it)) {
                it.parent.childEdges.remove(it)
                it.child.parentEdges.remove(it)
                rbGraph.edges.remove(it)
            }
        }
    }

    // returns false if edge should be removed
    private fun verifyEdge(edge: CallEdge): Boolean {
//        val (url, offset) = edge.child.declarationSite.split("#")
//        val vFile = urlToVirtualFileMap[url]!!
//        val psiFile =  PsiManager.getInstance(project).findFile(vFile)
//        val psiElement = psiFile?.findElementAt(offset.toInt())!!
//        val psiFun = MyPsiUtils.findParentFunPsiElement(psiElement)!!
        
        val classId = edge.child.classFqName
        val (url, offset) = edge.callSite.split("#")
        val vFile = urlToVirtualFileMap[url]!!
        val psiFile =  PsiManager.getInstance(project).findFile(vFile)
        val psiElement = psiFile?.findElementAt(offset.toInt())!!


        val queue: Queue<KtNameReferenceExpression> = LinkedList()
        MyPsiUtils.findParentDotQualified(psiElement)?.let{ 
            if (it.receiverExpression is KtNameReferenceExpression){
                queue.add(it.receiverExpression as KtNameReferenceExpression)
            }
        }
        
        while (queue.isNotEmpty()) {
            val currentExpr = queue.poll()
            val origin = currentExpr.mainReference.resolve()
            println()
        }
        
        return false
    }
    
    
    private fun pruneNodesRound() {
        
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
                
                overrides.forEach { fn ->
                    // Get or create function node and explore
                    val functionNode = rbGraph.getOrCreateFunction(fn)
                    // If no overrides -> Strong connection 
                    rbGraph.connect(currentNode, functionNode, MyPsiUtils.getUrl(call)!!, overrides.size == 1)
                    exploreFunDeclaration(fn, functionNode)
                }
            }
        }
    }
}