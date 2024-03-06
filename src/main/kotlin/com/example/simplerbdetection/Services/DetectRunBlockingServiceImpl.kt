package com.example.simplerbdetection.Services

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal class DetectRunBlockingServiceImpl(private val project: Project): DetectRunBlockingService {
    override fun Detect() {
        println("Found and started service for project ${project.name}")
        val currentDoc: Document = FileEditorManager.getInstance(project).selectedTextEditor?.document ?: return
        val psiFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(currentDoc) ?: return
        val runBlockingFilter = PsiElementFilter {element -> 
            if (element is KtCallExpression) {
                val callee = element.calleeExpression
                if (callee is KtNameReferenceExpression)
                    return@PsiElementFilter callee.getReferencedName() == "runBlocking"
            }
            false
        }
        
        val results = PsiTreeUtil.collectElements(psiFile, runBlockingFilter)
        println(results.size)
    }
}