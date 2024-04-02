package com.example.simplerbdetection.CallGraph

import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.MyPsiUtils
import org.jetbrains.kotlin.psi.KtNamedFunction

class FunctionNode(
    val id: String, 
    val declarationSite: String,
    val fqName: String,
    val isSuspend: Boolean
) {
    
    constructor(psiFun: KtNamedFunction) : this (
        generateId(psiFun), 
        MyPsiUtils.getUrl(psiFun) ?: "",
        psiFun.fqName.toString(),
        ElementFilters.suspendFun.isAccepted(psiFun)
        )
    
    private val childrenUrlMap = mutableMapOf<FunctionNode, String>()
    
    val parents = mutableSetOf<FunctionNode>()
    var asyncContext = true
    var visited = false
    var isBuilder = false
    
    fun getChildren(): Set<FunctionNode> {
        return childrenUrlMap.keys
    }
    fun getCallSiteFor(node: FunctionNode): String {
        return childrenUrlMap[node]!!
    }
    fun addChild(child: FunctionNode, callSite: String) {
        childrenUrlMap[child] = callSite
    }
    
    fun addParent(parent: FunctionNode) {
        parents.add(parent)
    }
    
    companion object {
        fun connect(parent: FunctionNode, child: FunctionNode, callSite: String) {
            parent.addChild(child, callSite)
            child.addParent(parent)
        }
        
        fun generateId(psiFun: KtNamedFunction): String {
            return "${psiFun.fqName}_${buildString{ psiFun.valueParameters.forEach { append(it.typeReference?.text) }}}"
        }
    }

    override fun hashCode() = id.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FunctionNode

        return id == other.id
    }
}