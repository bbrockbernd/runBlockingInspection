package com.example.simplerbdetection.CallGraph

import com.example.simplerbdetection.MyPsiUtils
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.*

class RBGraph {
    private var functionMap = mutableMapOf<String, FunctionNode>()
    private val fileMap = mutableMapOf<String, MutableSet<FunctionNode>>()
    
    fun clear(): Unit {
       functionMap.clear()
       fileMap.clear() 
    }
    
    fun addBuilder(psiElement: PsiElement): FunctionNode {
        val filePath = psiElement.containingFile.virtualFile.path
        val functionNode: FunctionNode = getOrAddBuilderToFM("__COR_BUILDER_${UUID.randomUUID()}", MyPsiUtils.getUrl(psiElement)?: "", "__COR_BUILDER")
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

    fun findBuilderBFS(start: FunctionNode): List<FunctionNode> {
        // Set all visited to false
        val cameFrom: MutableMap<FunctionNode, FunctionNode> = mutableMapOf()
        
        functionMap.values.forEach { it.visited = false }
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