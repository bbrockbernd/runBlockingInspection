package com.example.simplerbdetection.Services

import com.intellij.psi.PsiElement

interface DetectRunBlockingService {
    
    fun analyzeRunBlocking(element: PsiElement): List<Pair<String, String>>?
    fun analyseProject()
    fun wholeProject(): List<RunBlockingProblem>
    data class RunBlockingProblem(val element: PsiElement, val stacTrace: List<Pair<String, String>>)
}

