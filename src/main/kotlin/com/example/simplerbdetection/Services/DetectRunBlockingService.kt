package com.example.simplerbdetection.Services

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

interface DetectRunBlockingService {
    
    fun isInAsyncContext(element: PsiElement): List<Pair<String, String>>?
    fun isAsyncMarkedFunction(element: KtNamedFunction): Boolean
    fun fileUpdate(element: KtFile)
    fun analyseProject()
    fun getRunBlockingInCoroutines(virtualFile: VirtualFile): List<RunBlockingProblem>
    fun isAsyncAndHasRunBlockings(psiElement: KtNamedFunction): List<RunBlockingProblem>
    fun wholeProject(): List<RunBlockingProblem>
    data class RunBlockingProblem(val element: PsiElement, val stacTrace: List<Pair<String, String>>)
}

