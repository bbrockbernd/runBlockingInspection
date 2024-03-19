package com.example.simplerbdetection.Services

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

interface DetectRunBlockingService {
    
    fun isInAsyncContext(element: PsiElement): String?
    fun isAsyncMarkedFunction(element: KtNamedFunction): Boolean
    fun fileUpdate(element: KtFile)
    fun analyseProject()
    
}