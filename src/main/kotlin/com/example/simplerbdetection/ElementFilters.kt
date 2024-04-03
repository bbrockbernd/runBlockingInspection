package com.example.simplerbdetection

import com.intellij.psi.util.PsiElementFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Custom PsiElementFilters used for runBlocking inspections
 */
class ElementFilters {
    companion object {
        val runBlockingBuilderInvocation = PsiElementFilter { el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression){
                    if (callee.getReferencedName() == "runBlocking") {
                        val funDef = callee.reference?.resolve()
                        return@PsiElementFilter funDef?.let { runBlockingBuilderDeclaration.isAccepted(it) } == true
                    }
                }
            }
            false
        }
        
        val runBlockingBuilderDeclaration = PsiElementFilter { it is KtNamedFunction && it.fqName?.toString() == "kotlinx.coroutines.runBlocking" }
        val launchBuilderDeclaration = PsiElementFilter { it is KtNamedFunction && it.fqName?.toString() == "kotlinx.coroutines.launch" }
        val asyncBuilderDeclaration = PsiElementFilter { it is KtNamedFunction && it.fqName?.toString() == "kotlinx.coroutines.async" }
        
        val suspendFun = PsiElementFilter { el -> 
            if (el is KtNamedFunction) {
                return@PsiElementFilter el.modifierList?.text?.contains("suspend") ?: false
            }
            return@PsiElementFilter false
        }
        
        val launchBuilder = PsiElementFilter { el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression){
                    if (callee.getReferencedName() == "launch") {
                        val funDef = callee.reference?.resolve()
                        return@PsiElementFilter funDef?.let { launchBuilderDeclaration.isAccepted(it) } == true
                    }
                }
            }
            false
        }
        
        val asyncBuilder = PsiElementFilter { el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression){
                    if (callee.getReferencedName() == "async") {
                        val funDef = callee.reference?.resolve()
                        return@PsiElementFilter funDef?.let { asyncBuilderDeclaration.isAccepted(it) } == true
                    }
                }
            }
            false
        }
        
        val runBlockingDeclarationFile = PsiElementFilter { it is KtFile && it.name == "Builders.kt" && it.packageFqName.toString() == "kotlinx.coroutines"}
        val launchAndAsyncDeclarationFile = PsiElementFilter { it is KtFile && it.name == "Builders.common.kt" && it.packageFqName.toString() == "kotlinx.coroutines"}
    }
}