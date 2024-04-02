package com.example.simplerbdetection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
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
        fun getLineNumber(psiElement: PsiElement) : Int {
            val document = psiElement.containingFile.viewProvider.document
            return document?.getLineNumber(psiElement.textRange.startOffset)?.plus(1) ?: -1
        }
        
        fun getUrl(element: PsiElement): String? {
            if (!element.isPhysical) return null
            val containingFile = if (element is PsiFileSystemItem) element else element.containingFile
            if (containingFile == null) return null
            val virtualFile = containingFile.virtualFile ?: return null
            return if (element is PsiFileSystemItem) virtualFile.url else virtualFile.url + "#" + element.textOffset
        }
        
    }
}