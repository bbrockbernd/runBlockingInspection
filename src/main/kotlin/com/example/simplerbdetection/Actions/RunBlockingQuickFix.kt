package com.example.simplerbdetection.Actions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class RunBlockingQuickFix : LocalQuickFix{
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        Messages.showMessageDialog(
            project,
            "Bla bla3\n blabla4",
            "Stack Trace",
            Messages.getInformationIcon()
        )
    }

    override fun getName(): String = "Show details"
    
    
}