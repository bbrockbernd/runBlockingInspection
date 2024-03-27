package com.example.simplerbdetection.Actions

import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.MyPsiUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiElementFilter
import org.jetbrains.kotlin.psi.KtCallExpression

class TestButton : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rbFun = findFunction(e.project!!, "Builders.kt", ElementFilters.runBlockingDeclarationFile, ElementFilters.runBlockingBuilderDeclaration)
        val rbResults = ReferencesSearch
            .search(rbFun, GlobalSearchScope.projectScope(e.project!!))
            .filter { it.element.parent is KtCallExpression }.map {it.element}
        
        // Find all launch builders in project
        val launchFun = findFunction(e.project!!, "Builders.common.kt",ElementFilters.launchAndAsyncDeclarationFile, ElementFilters.launchBuilderDeclaration)
        val launchResults = ReferencesSearch
            .search(launchFun, GlobalSearchScope.projectScope(e.project!!))
            .filter { it.element.parent is KtCallExpression }.map {it.element}
        
        // Find all async builders in project
        val asyncFun = findFunction(e.project!!, "Builders.common.kt",ElementFilters.launchAndAsyncDeclarationFile, ElementFilters.asyncBuilderDeclaration)
        val asyncResults = ReferencesSearch
            .search(asyncFun, GlobalSearchScope.projectScope(e.project!!))
            .filter { it.element.parent is KtCallExpression }.map {it.element}
        
        
    }
    
    private fun findFunction(project: Project, fileName: String, fileFilter: PsiElementFilter, funDefFilter: PsiElementFilter): PsiElement {
        val files = FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.everythingScope(project))
        val psiFile = files
            .map{PsiManager.getInstance(project).findFile(it)}
            .filterNotNull()
            .first { fileFilter.isAccepted(it) }

        val funDef = MyPsiUtils.findAllChildren(psiFile) { funDefFilter.isAccepted(it) }.first()

        
        return funDef
    }
}