package com.example.simplerbdetection

import com.intellij.psi.util.PsiElementFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

class ElementFilters {
    companion object {
        val runBlockingBuilder = PsiElementFilter { el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression){
                    if (callee.getReferencedName() == "runBlocking") {
                        val funDef = callee.reference?.resolve()
                        if (funDef is KtNamedFunction) {
                            return@PsiElementFilter funDef.fqName?.toString() == "kotlinx.coroutines.runBlocking"
                        }
                    }
                }
            }
            false
        }
        
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
                        if (funDef is KtNamedFunction) {
                            return@PsiElementFilter funDef.fqName?.toString() == "kotlinx.coroutines.launch"
                        }
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
                        if (funDef is KtNamedFunction) {
                            return@PsiElementFilter funDef.fqName?.toString() == "kotlinx.coroutines.async"
                        }
                    }
                }
            }
            false
        }
    }
}