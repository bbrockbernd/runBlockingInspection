package com.example.simplerbdetection.CallGraph

import java.util.*

class RBGraph {
    private var functionMap = mutableMapOf<String, FunctionNode>()
    private val fileMap = mutableMapOf<String, MutableSet<FunctionNode>>()
    
    fun clear(): Unit {
       functionMap.clear()
       fileMap.clear() 
    }
    
    fun addBuilder(filePath: String): FunctionNode {
        val functionNode: FunctionNode = getOrAddToFM("__COR_BUILDER_${UUID.randomUUID()}", filePath)
        addToFileMap(functionNode, filePath)
        functionNode.isBuilder = true
        return functionNode
    }
    
    fun getOrCreateFunction(id: String, filePath: String): FunctionNode {
        return functionMap.getOrPut(id) {
            val node = FunctionNode(id, filePath)
            addToFileMap(node, filePath)
            node
        }
    }
    
    fun containsFun(id: String): Boolean = functionMap.contains(id)
    fun getFunction(id: String): FunctionNode {
        return functionMap[id]!!
    }
    
    fun findBuilder(start: FunctionNode): FunctionNode {
        // Set all visited to false
        functionMap.values.forEach { it.visited = false }
        var currentNode = start
        while (!currentNode.isBuilder) {
            currentNode.visited = true
            currentNode = currentNode.parents.first { !it.visited }
        }
        return currentNode
    }
    
    private fun getOrAddToFM(id: String, filePath: String): FunctionNode {
        return functionMap.getOrPut(id) { FunctionNode(id, filePath) }
    }
    
    private fun addToFileMap(fn: FunctionNode, filePath: String) {
        val set = fileMap.getOrPut(filePath) { mutableSetOf() }
        set.add(fn)
    }
    
    
}