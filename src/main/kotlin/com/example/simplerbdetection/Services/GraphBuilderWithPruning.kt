package com.example.simplerbdetection.Services

import com.example.simplerbdetection.CallGraph.CallEdge
import com.example.simplerbdetection.MyPsiUtils
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import java.util.*

class GraphBuilderWithPruning(project: Project) : GraphBuilder(project) {

    fun pruneGraph(): GraphBuilder {
        // while until graph is unchanged for now only one call
        pruneEdgesRound()
        pruneNodesRound()


        return this
    }

    private fun pruneEdgesRound() {
        val weakEdges = rbGraph.edges.filter {!it.strongCall}
        weakEdges.forEach {
            if (!verifyEdge(it)) {
                it.parent.childEdges.remove(it)
                it.child.parentEdges.remove(it)
                rbGraph.edges.remove(it)
            }
        }
    }

    // returns false if edge should be removed
    private fun verifyEdge(edge: CallEdge): Boolean {
//        val (url, offset) = edge.child.declarationSite.split("#")
//        val vFile = urlToVirtualFileMap[url]!!
//        val psiFile =  PsiManager.getInstance(project).findFile(vFile)
//        val psiElement = psiFile?.findElementAt(offset.toInt())!!
//        val psiFun = MyPsiUtils.findParentFunPsiElement(psiElement)!!

        val classId = edge.child.classFqName
        val (url, offset) = edge.callSite.split("#")
        val vFile = urlToVirtualFileMap[url]!!
        val psiFile =  PsiManager.getInstance(project).findFile(vFile)
        val psiElement = psiFile?.findElementAt(offset.toInt())!!


        val queue: Queue<KtNameReferenceExpression> = LinkedList()
        MyPsiUtils.findParentDotQualified(psiElement)?.let{
            if (it.receiverExpression is KtNameReferenceExpression){
                queue.add(it.receiverExpression as KtNameReferenceExpression)
            }
        }

        while (queue.isNotEmpty()) {
            val currentExpr = queue.poll()
            val origin = currentExpr.mainReference.resolve()
            println()
        }

        return false
    }


    private fun pruneNodesRound() {

    }
}