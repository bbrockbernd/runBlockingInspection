package com.example.runBlockingInspection.rbgraph

import com.example.runBlockingInspection.utils.ElementFilters
import com.example.runBlockingInspection.utils.MyPsiUtils
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Represents a node in the function hierarchy.
 *
 * @property id The unique identifier of the function.
 * @property declarationSite The declaration site of the function.
 * @property fqName The fully qualified name of the function.
 * @property isSuspend Indicates whether the function is a suspend function or not.
 */
class FunctionNode(
    val id: String, 
    val declarationSite: String,
    val fqName: String,
    val fileAndLine: String,
    val isSuspend: Boolean
) {
    
    constructor(psiFun: KtNamedFunction) : this (
        generateId(psiFun), 
        MyPsiUtils.getUrl(psiFun) ?: "",
        psiFun.fqName.toString(),
        MyPsiUtils.getFileAndLine(psiFun) ?: "",
        ElementFilters.suspendFun.isAccepted(psiFun)
        )
    
    val childEdges = mutableSetOf<CallEdge>()
    
    val parentEdges = mutableSetOf<CallEdge>()
    var asyncContext = true
    var visited = false
    var isBuilder = false
    
    fun addChild(childConnection: CallEdge) {
        childEdges.add(childConnection)
    }
    
    fun addParent(parentConnection: CallEdge) {
        parentEdges.add(parentConnection)
    }
    
    companion object {
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