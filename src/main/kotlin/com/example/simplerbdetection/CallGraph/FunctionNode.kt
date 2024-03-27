package com.example.simplerbdetection.CallGraph

import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.MyPsiUtils
import org.jetbrains.kotlin.psi.KtNamedFunction

class FunctionNode(
    val id: String, 
    val filePath: String,
    val fqName: String,
    val lineNr: Int,
    val isSuspend: Boolean
) {
    
    constructor(psiFun: KtNamedFunction) : this (
        generateId(psiFun), 
        psiFun.containingFile.virtualFile.path,
        psiFun.fqName.toString(),
        MyPsiUtils.getLineNumber(psiFun),
        ElementFilters.suspendFun.isAccepted(psiFun)
        )
    
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