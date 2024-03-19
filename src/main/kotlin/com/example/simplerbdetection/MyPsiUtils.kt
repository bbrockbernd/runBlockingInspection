package com.example.simplerbdetection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor

class MyPsiUtils {
    companion object {
        
        fun findAllChildren(startElement: PsiElement, condition: (PsiElement) -> Boolean, fenceCondition: (PsiElement) -> Boolean): List<PsiElement> {
            val foundChildren = mutableListOf<PsiElement>()
            startElement.accept(object: PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (startElement != element && fenceCondition(element)) return
                    if (startElement != element && condition(element)) foundChildren.add(element)
                    super.visitElement(element)
                }
            })
            return foundChildren
        }
        fun findAllChildren(startElement: PsiElement, condition: (PsiElement) -> Boolean): List<PsiElement>{
          return findAllChildren(startElement, condition) { false }
        }
        
    }
}