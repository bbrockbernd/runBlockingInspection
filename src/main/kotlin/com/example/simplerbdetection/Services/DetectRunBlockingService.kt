package com.example.simplerbdetection.Services

import com.example.simplerbdetection.RunBlockingInspection
import com.intellij.analysis.AnalysisScope
import com.intellij.psi.PsiElement

interface DetectRunBlockingService {
    fun analyzeRunBlocking(element: PsiElement): List<Pair<String, String>>?
    fun wholeProject(): List<RunBlockingProblem>
    fun processProject(
        scope: AnalysisScope? = null,
        totalFilesTodo: ((Int) -> Unit) = {},
        incrementFilesDone: (() -> Unit) = {},
        level: RunBlockingInspection.ExplorationLevel = RunBlockingInspection.ExplorationLevel.DECLARATION
    )

    data class RunBlockingProblem(val element: PsiElement, val stacTrace: List<Pair<String, String>>)
}

