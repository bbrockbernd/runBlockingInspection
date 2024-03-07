package com.example.simplerbdetection.Annotators

import com.example.simplerbdetection.ElementFilters
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

class RunBlockingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) { 
        if (!ElementFilters.runBlockingFilter.isAccepted(element)) return
        if (isInAsyncContext(element)) holder.rbAnnotate(element)
    }
    
    private fun isInAsyncContext(element: PsiElement): Boolean {
        if (hasRunBlockingParent(element) || hasSuspendParent(element)) return true
        val parentFun = findFunParent(element)
        if (parentFun == null) {
            println("Parent function not found of element ${element.text}")
            return false
        }
        return ReferencesSearch.search(parentFun)
            .mapNotNull { it.resolve() }
            .map { isInAsyncContext(it) }
            .any()
    }
    
    private fun hasRunBlockingParent(element: PsiElement): Boolean 
            = PsiTreeUtil.findFirstParent(element.parent) { ElementFilters.runBlockingFilter.isAccepted(it) } != null
    
    private fun hasSuspendParent(element: PsiElement): Boolean
            = PsiTreeUtil.findFirstParent(element.parent) { ElementFilters.suspendFun.isAccepted(it) } != null
    
    private fun findFunParent(element: PsiElement): KtNamedFunction? 
            = PsiTreeUtil.findFirstParent(element.parent) { it is KtNamedFunction } as KtNamedFunction?
    
    private fun AnnotationHolder.rbAnnotate(element: PsiElement) {
        this
            .newAnnotation(HighlightSeverity.WARNING, "runBlocking call from coroutine")
            .range(element.children.first { it is KtNameReferenceExpression })
            .create()
    }
}