package com.example.simplerbdetection.Services

import com.intellij.analysis.AnalysisScope
import com.intellij.psi.PsiElement

interface DetectRunBlockingService {
    fun analyzeRunBlocking(element: PsiElement): List<Pair<String, String>>?
    fun wholeProject(): List<RunBlockingProblem>
    fun analyseProject(scope: AnalysisScope? = null, totalFilesTodo: ((Int) -> Unit) = {}, incrementFilesDone: (() -> Unit) = {})
    data class RunBlockingProblem(val element: PsiElement, val stacTrace: List<Pair<String, String>>)
}

