package com.example.simplerbdetection.Annotators

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiElementFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class RunBlockingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val runBlockingFilter = PsiElementFilter {el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression)
                    return@PsiElementFilter callee.getReferencedName() == "runBlocking"
            }
            false
        }

       if (runBlockingFilter.isAccepted(element)) holder
           .newAnnotation(HighlightSeverity.WARNING, "TEST TEST RUNBLOCKING HERE")
           .range(element.children.first { it is KtNameReferenceExpression })
           .create()
        
    }
}