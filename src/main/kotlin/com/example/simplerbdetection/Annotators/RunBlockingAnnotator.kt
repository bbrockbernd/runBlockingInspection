package com.example.simplerbdetection.Annotators

import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.Services.DetectRunBlockingService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

// Annotator is currently disabled
class RunBlockingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (ElementFilters.runBlockingBuilderInvocation.isAccepted(element)) {
            val result = element.project.service<DetectRunBlockingService>().analyzeRunBlocking(element)
            if (result != null) {
                holder.rbAnnotate(element)
            }
        }
//        if (element is KtNamedFunction) {
//            println("Annotator called")
//            if (element.project.service<DetectRunBlockingService>().isAsyncMarkedFunction(element)) {
//                findRunBlockings(element).forEach { holder.rbAnnotate(it) }
//            }
//        }
    }

    private fun findRunBlockings(element: PsiElement): List<PsiElement> {
        val runBlockingList = mutableListOf<PsiElement>()
        element.accept(object: PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (ElementFilters.runBlockingBuilderInvocation.isAccepted(element)) runBlockingList.add(element)
                super.visitElement(element)
            }
        })
        return runBlockingList
    }
    
    private fun hasRunBlockingParent(element: PsiElement): Boolean 
            = PsiTreeUtil.findFirstParent(element.parent) { ElementFilters.runBlockingBuilderInvocation.isAccepted(it) } != null
    
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
    private fun AnnotationHolder.testtest(element: PsiElement) {
        this
            .newAnnotation(HighlightSeverity.WARNING, "TEST TEST")
            .create()
    }
}