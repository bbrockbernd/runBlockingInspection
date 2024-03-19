package com.example.simplerbdetection.Actions

import com.example.simplerbdetection.ElementFilters
import com.example.simplerbdetection.MyPsiUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class TestButton : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val currentFile = e.getDataContext().getData(CommonDataKeys.PSI_FILE) ?: return
        
        val rbs = MyPsiUtils.findAllChildren(currentFile) { ElementFilters.runBlockingBuilder.isAccepted(it) }
        for (rb in rbs) {
//            rb.putUserData()
        }
        println("bl")
    }
}