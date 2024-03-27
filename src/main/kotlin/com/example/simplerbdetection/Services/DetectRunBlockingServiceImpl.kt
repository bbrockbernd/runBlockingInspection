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
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.util.collectionUtils.concat

internal class DetectRunBlockingServiceImpl(override val project: Project) : DetectRunBlockingService, ProblemsProvider {
    private val relevantFiles = mutableListOf<VirtualFile>()
    private val rbFiles = mutableSetOf<VirtualFile>()
    private val rbGraph = RBGraph()

    override fun isInAsyncContext(element: PsiElement): List<String>? {
        
        // Find first interesting parent if any, Aka function definition or async builder
        val psiFunOrBuilder = PsiTreeUtil.findFirstParent(element, true) { 
                    it is KtNamedFunction 
                    || ElementFilters.launchBuilder.isAccepted(it) 
                    || ElementFilters.asyncBuilder.isAccepted(it)
                    || ElementFilters.runBlockingBuilderInvocation.isAccepted(it)
        }
        
        // If found element is function def, check if it runs in coroutine
        if (psiFunOrBuilder is KtNamedFunction) {
            // Check if function exists in graph (and therefore runs in coroutine)
            if (rbGraph.containsFun(FunctionNode.generateId(psiFunOrBuilder))) {
                val funNode = rbGraph.getFunction(FunctionNode.generateId(psiFunOrBuilder))
                // Check if node runs in coroutine TODO might be redundant and could be removed
                // Then find one of the roots of this subtree and return its file
                if (funNode.asyncContext) return rbGraph.findBuilderBFS(funNode).map{ "function ${it.fqName} at ${it.filePath}:${it.lineNr}" }
                return null
            }
        } else if (psiFunOrBuilder != null) return listOf("Direct child of ${element.containingFile.virtualFile.path}:${MyPsiUtils.getLineNumber(psiFunOrBuilder)}")
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
//        newFullAnalysis()
        wholeProject()
    }

    override fun getRunBlockingInCoroutines(file: VirtualFile): List<DetectRunBlockingService.RunBlockingProblem> {
        return checkRunBlockingsForFile(file)
    }

    override fun wholeProject(): List<DetectRunBlockingService.RunBlockingProblem> {
        return rbFiles.fold(mutableListOf()) { results, file -> results.concat(checkRunBlockingsForFile(file))!!.toMutableList() } 
    }

    private fun checkRunBlockingsForFile(file: VirtualFile): List<DetectRunBlockingService.RunBlockingProblem> {
        if (!rbFiles.contains(file)) return emptyList()
        val rbs: MutableList<DetectRunBlockingService.RunBlockingProblem> = mutableListOf()
        findRunBlockings(file).forEach { rb ->
            val stackTrace = isInAsyncContext(rb)
            if (stackTrace != null) {
                val lineNr = MyPsiUtils.getLineNumber(rb)
                rbs.add(
                    DetectRunBlockingService.RunBlockingProblem(rb, stackTrace))
                println("\n\n------------- Found runBlocking in coroutine ------------------------------")
                stackTrace.forEach { println("  $it") }
                println("---> RB in async context in file ${file.path}:$lineNr")
            }
        }
        return rbs
    }

    private fun checkAllRunBlockings(): List<PsiElement> {
        val rbs: MutableList<PsiElement> = mutableListOf()
        rbFiles.forEachIndexed() { index, file ->
            println("Checking RBs: $index / ${rbFiles.size}")
            rbs.concat(checkRunBlockingsForFile(file))
        }
        return rbs
    }
    
    private fun newFullAnalysis() {
        rbGraph.clear()
        rbFiles.clear()
        
        updateRelevantFiles()

        println("Searching for runBlocking builders")
        val rbFun = findFunction(project, "Builders.kt", ElementFilters.runBlockingDeclarationFile, ElementFilters.runBlockingBuilderDeclaration)
        val rbResults = ReferencesSearch
            .search(rbFun, GlobalSearchScope.projectScope(project))
            .filter { it.element.parent is KtCallExpression }.map {it.element}

        println("Searching for launch builders")
        // Find all launch builders in project
        val launchFun = findFunction(project, "Builders.common.kt",ElementFilters.launchAndAsyncDeclarationFile, ElementFilters.launchBuilderDeclaration)
        val launchResults = ReferencesSearch
            .search(launchFun, GlobalSearchScope.projectScope(project))
            .filter { it.element.parent is KtCallExpression }.map {it.element}

        println("Searching for async builders")
        // Find all async builders in project
        val asyncFun = findFunction(project, "Builders.common.kt",ElementFilters.launchAndAsyncDeclarationFile, ElementFilters.asyncBuilderDeclaration)
        val asyncResults = ReferencesSearch
            .search(asyncFun, GlobalSearchScope.projectScope(project))
            .filter { it.element.parent is KtCallExpression }.map {it.element}
        
        rbResults.forEach { 
            
            createSubtree(it, it.containingFile.virtualFile)
            rbFiles.add(it.containingFile.virtualFile)
        }
        
        launchResults.forEach {
            createSubtree(it, it.containingFile.virtualFile)
            rbFiles.add(it.containingFile.virtualFile)
        }

        asyncResults.forEach {
            createSubtree(it, it.containingFile.virtualFile)
            rbFiles.add(it.containingFile.virtualFile)
        }
        
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
                    exploreFunDeclaration(susFun, rbGraph.getOrCreateFunction(susFun))
            }
        }
    }

    private fun createSubtree(builder: PsiElement, file: VirtualFile) {
        //Add runBlocking root to graph
        val runBlockingNode = rbGraph.addBuilder(builder)
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
            { ElementFilters.launchBuilder.isAccepted(it) || ElementFilters.asyncBuilder.isAccepted(it) || ElementFilters.runBlockingBuilderInvocation.isAccepted(it) }).filterIsInstance<KtCallExpression>()

        for (call in methodCalls) {
            // Find method decl for call
            //TODO fix deprc call
            val psiFn = call.calleeExpression?.reference?.resolve()
            if (psiFn is KtNamedFunction) {
                // if method in non-relevant file skip call.calleeExpression.reference.resolve()
                val funFile = getFileForElement(psiFn)
                if (!relevantFiles.contains(funFile)) return

                // Get or create function node and explore
                val functionNode = rbGraph.getOrCreateFunction(psiFn)
                FunctionNode.connect(currentNode, functionNode)
                exploreFunDeclaration(psiFn, functionNode)
            }
        }
    }

    private fun findRunBlockings(file: VirtualFile): List<PsiElement> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return listOf()
        return MyPsiUtils.findAllChildren(psiFile) { ElementFilters.runBlockingBuilderInvocation.isAccepted(it) }
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