package com.example.simplerbdetection.Actions

import com.example.simplerbdetection.Services.DetectRunBlockingService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

/**
 * Manual rb trigger for testing purposes
 */
class RBDetectionAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {   
        e.project?.service<DetectRunBlockingService>()?.analyseProject()
        
        println("Analysis done")
    }
}