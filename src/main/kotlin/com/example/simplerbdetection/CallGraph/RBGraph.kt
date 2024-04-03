package com.example.simplerbdetection.CallGraph

import com.example.simplerbdetection.MyPsiUtils
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.*

class RBGraph {
    private var functionMap = mutableMapOf<String, FunctionNode>()
    private val fileMap = mutableMapOf<String, MutableSet<FunctionNode>>()
    
    fun clear() {
       functionMap.clear()
       fileMap.clear() 
    }
    
    fun addBuilder(psiElement: PsiElement): FunctionNode {
        if (psiElement !is KtCallExpression) throw IllegalArgumentException("Builder must be KtCallExpression")
        val filePath = psiElement.containingFile.virtualFile.path
        val ktFun: KtNamedFunction = psiElement.calleeExpression?.reference?.resolve() as KtNamedFunction
        val fqName: String = ktFun.fqName.toString()
        val url = MyPsiUtils.getUrl(psiElement) ?: ""
        
        val functionNode: FunctionNode = getOrAddBuilderToFM("${fqName}_${url}", url, fqName)
        addToFileMap(functionNode, filePath)
        functionNode.isBuilder = true
        return functionNode
    }
    
    fun getOrCreateFunction(func: KtNamedFunction): FunctionNode {
        val id = FunctionNode.generateId(func)
        val filePath = func.containingFile.virtualFile.path
        return functionMap.getOrPut(id) {
            val node = FunctionNode(func)
            addToFileMap(node, filePath)
            node
        }
    }
    
    fun containsFun(id: String): Boolean = functionMap.contains(id)
    fun getFunction(id: String): FunctionNode {
        return functionMap[id]!!
    }

    /**
     * Performs a breadth-first search starting from the given start node to find a builder or suspend node in the function graph.
     *
     * @param start The start node for the breadth-first search.
     * @return A list of nodes representing the path from the start node to the builder node.
     */
    fun findBuilderBFS(start: FunctionNode): List<FunctionNode> {
        // Map to backtrack traversed path
        val cameFrom: MutableMap<FunctionNode, FunctionNode> = mutableMapOf()
        // Set all visited to false
        functionMap.values.forEach { it.visited = false }
        
        // BFS
        val queue: Queue<FunctionNode> = LinkedList()
        queue.add(start)
        var builderNode = start
        while (!queue.isEmpty()) {
            val currentNode = queue.poll()
            if (currentNode.isBuilder || currentNode.isSuspend) {
                builderNode = currentNode
                break
            }
            currentNode.visited = true
            val unexploredParents = currentNode.parents.filter { !it.visited }
            queue.addAll(unexploredParents)
            unexploredParents.forEach { cameFrom[it] = currentNode }
        }
        
        // Generate trace trough cameFrom backtrack map
        var backWardsNode = builderNode
        val traceAccumulator: MutableList<FunctionNode> = mutableListOf(backWardsNode)
        while (backWardsNode != start) {
            backWardsNode = cameFrom[backWardsNode]!!
            traceAccumulator.add(backWardsNode)
        }
        return traceAccumulator
    }
    
    private fun getOrAddBuilderToFM(id: String, declerationSite: String, fqName: String): FunctionNode {
        return functionMap.getOrPut(id) { FunctionNode(id, declerationSite, fqName, false) }
    }
    
    private fun addToFileMap(fn: FunctionNode, filePath: String) {
        val set = fileMap.getOrPut(filePath) { mutableSetOf() }
        set.add(fn)
    }
}