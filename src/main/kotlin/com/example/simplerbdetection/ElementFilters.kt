package com.example.simplerbdetection

import com.intellij.psi.util.PsiElementFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

class ElementFilters {
    companion object {
        val runBlockingFilter = PsiElementFilter {el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression)
                    return@PsiElementFilter callee.getReferencedName() == "runBlocking"
            }
            false
        }
        
        val suspendFun = PsiElementFilter { el -> 
            if (el is KtNamedFunction) {
                return@PsiElementFilter el.modifierList?.text?.contains("suspend") ?: false
            }
            return@PsiElementFilter false
        }
        
    }
}