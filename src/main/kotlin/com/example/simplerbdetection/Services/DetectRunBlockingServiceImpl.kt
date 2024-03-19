package com.example.simplerbdetection.Services

import com.example.simplerbdetection.CallGraph.FunctionNode
import com.example.simplerbdetection.CallGraph.RBGraph
import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.MyPsiUtils
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class DetectRunBlockingServiceImpl(override val project: Project) : DetectRunBlockingService, ProblemsProvider {
    private val relevantFiles = mutableListOf<VirtualFile>()
    private val rbFiles = mutableSetOf<VirtualFile>()
    private val rbGraph = RBGraph()

    override fun isInAsyncContext(element: PsiElement): String? {
        
        // Find first interesting parent if any, Aka function definition or async builder
        val psiFunOrBuilder = PsiTreeUtil.findFirstParent(element, true) { 
                    it is KtNamedFunction 
                    || ElementFilters.launchBuilder.isAccepted(it) 
                    || ElementFilters.asyncBuilder.isAccepted(it)
                    || ElementFilters.runBlockingBuilder.isAccepted(it)}
        
        // If found element is function def, check if it runs in coroutine
        if (psiFunOrBuilder is KtNamedFunction) {
            // Check if function exists in graph (and therefore runs in coroutine)
            if (rbGraph.containsFun(FunctionNode.generateId(psiFunOrBuilder))) {
                val funNode = rbGraph.getFunction(FunctionNode.generateId(psiFunOrBuilder))
                // Check if node runs in coroutine TODO might be redundant and could be removed
                // Then find one of the roots of this subtree and return its file
                if (funNode.asyncContext) return rbGraph.findBuilder(funNode).filePath
                return null
            }
        } else if (psiFunOrBuilder != null) return element.containingFile.virtualFile.path
        return null
    }

    override fun isAsyncMarkedFunction(element: KtNamedFunction): Boolean {
        val funId = FunctionNode.generateId(element)
        return rbGraph.containsFun(funId) && rbGraph.getFunction(funId).asyncContext
    }

    override fun fileUpdate(element: KtFile) {
        TODO("Not yet implemented")
    }

    override fun analyseProject() {
        fullAnalysis()
        checkAllRunBlockings()
    }
    
    private fun checkAllRunBlockings() {
        rbFiles.forEachIndexed() { index, file ->
            println("Checking RBs: $index / ${rbFiles.size}")
            findRunBlockings(file).forEach { rb ->
                val path = isInAsyncContext(rb)
                if (path != null) {
                    val lineNr = getLineNumber(rb)
                    println("RB in async context in file ${file.path} at line $lineNr, comes from $path")
                }
            }
        }
    }
    
    private fun getLineNumber(psiElement: PsiElement) : Int {
        val project = psiElement.project
        val document = psiElement.containingFile.viewProvider.document
        return document?.getLineNumber(psiElement.textRange.startOffset)?.plus(1) ?: -1
    }

    // Full project analysis for nested run blocking calls 
    private fun fullAnalysis() {
        // Clear rb graph
        rbGraph.clear()
        rbFiles.clear()

        // Get editable kotlin files
        updateRelevantFiles()
        relevantFiles.forEachIndexed() { index, file ->
            println("Building graph: $index / ${relevantFiles.size}")
            // Search kotlin file for runBlocking calls, and generate tree
            findRunBlockings(file).forEach { rb ->
                createSubtree(rb, file)
                rbFiles.add(file)
            }
//             Search for async, and launch builders in case globalscope or android scopes are used
            findNonBlockingBuilders(file).forEach { builder -> 
                createSubtree(builder, file)
            }
            // suspend fun for completeness
            findSuspendFuns(file).forEach { susFun ->
                if (susFun is KtNamedFunction) 
                    exploreFunDeclaration(susFun, rbGraph.getOrCreateFunction(FunctionNode.generateId(susFun), file.path))
            }
        }
    }

    private fun createSubtree(builder: PsiElement, file: VirtualFile) {
        //Add runBlocking root to graph
        val runBlockingNode = rbGraph.addBuilder(file.path)
//        println("Adding runBlocking ${runBlockingNode.id}")
        exploreFunDeclaration(builder, runBlockingNode)
    }

    private fun getFileForElement(psiElement: PsiElement): VirtualFile {
        val ktFile = PsiTreeUtil.findFirstParent(psiElement) { it is KtFile } as KtFile
        return ktFile.virtualFile
    }

    private fun exploreFunDeclaration(currentPsiEl: PsiElement, currentNode: FunctionNode) {
//        println("Exploring function ${currentNode.id}")
        // If explored stop
        if (currentNode.visited) return
        currentNode.visited = true
        //Find all calls from this runBlocking context
        val methodCalls = MyPsiUtils.findAllChildren(currentPsiEl, { it is KtCallExpression }, 
            { ElementFilters.launchBuilder.isAccepted(it) || ElementFilters.asyncBuilder.isAccepted(it) || ElementFilters.runBlockingBuilder.isAccepted(it) }).filterIsInstance<KtCallExpression>()

        for (call in methodCalls) {
            // Find method decl for call
            //TODO fix deprc call
            val psiFn = call.calleeExpression?.reference?.resolve()
            if (psiFn is KtNamedFunction) {
                // if method in non-relevant file skip call.calleeExpression.reference.resolve()
                val funFile = getFileForElement(psiFn)
                if (!relevantFiles.contains(funFile)) return

                // Get or create function node and explore
                val id = FunctionNode.generateId(psiFn)
                val functionNode = rbGraph.getOrCreateFunction(id, funFile.path)
                FunctionNode.connect(currentNode, functionNode)
                exploreFunDeclaration(psiFn, functionNode)
            }
        }
    }

    private fun findRunBlockings(file: VirtualFile): List<PsiElement> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
        return MyPsiUtils.findAllChildren(psiFile) { ElementFilters.runBlockingBuilder.isAccepted(it) }
    }
    
    private fun findNonBlockingBuilders(file: VirtualFile): List<PsiElement> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
        return MyPsiUtils.findAllChildren(psiFile) { ElementFilters.launchBuilder.isAccepted(it) || ElementFilters.asyncBuilder.isAccepted(it) }
    }
    
    private fun findSuspendFuns(file: VirtualFile): List<PsiElement> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
        return MyPsiUtils.findAllChildren(psiFile) { ElementFilters.suspendFun.isAccepted(it) }
    }

    private fun updateRelevantFiles(): MutableList<VirtualFile> {
        relevantFiles.clear()
        ProjectFileIndex.getInstance(project).iterateContent {
            if (it.fileType is KotlinFileType) {
                relevantFiles.add(it)
            }
            true
        }
        return relevantFiles
    }

}