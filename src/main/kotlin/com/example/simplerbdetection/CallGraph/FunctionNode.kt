package com.example.simplerbdetection.CallGraph

import org.jetbrains.kotlin.psi.KtNamedFunction

class FunctionNode(val id: String, val filePath: String) {
    val children = mutableSetOf<FunctionNode>()
    val parents = mutableSetOf<FunctionNode>()
    var asyncContext = true
    var visited = false
    var isBuilder = false
    
    fun addChild(child: FunctionNode) {
        children.add(child)
    }
    
    fun addParent(parent: FunctionNode) {
        parents.add(parent)
    }
    
    companion object {
        fun connect(parent: FunctionNode, child: FunctionNode) {
            parent.addChild(child)
            child.addParent(parent)
        }
        
        fun generateId(psiFun: KtNamedFunction): String {
            return "${psiFun.fqName}_${buildString{ psiFun.valueParameters.forEach { append(it.typeReference?.text) }}}"
        }
    }
}