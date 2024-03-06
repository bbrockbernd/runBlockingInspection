package com.example.simplerbdetection

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.reference.RefEntity
import com.intellij.codeInspection.reference.RefGraphAnnotator
import com.intellij.codeInspection.reference.RefManager
import com.intellij.codeInspection.reference.RefMethod
import com.intellij.openapi.diagnostic.logger

class RunBlockingInspection() : GlobalInspectionTool() {
    
    private val LOG = logger<RunBlockingInspection>()

    init {
        println("Constructed inspector")
    }

    override fun getAnnotator(refManager: RefManager): RefGraphAnnotator? {
        return object : RefGraphAnnotator() {
            
        }
    }
    

    override fun checkElement(
        refEntity: RefEntity,
        scope: AnalysisScope,
        manager: InspectionManager,
        globalContext: GlobalInspectionContext
    ): Array<CommonProblemDescriptor>? {
        println("Checking element")
        if (refEntity is RefMethod && refEntity.name == "runBlocking()") {
            return arrayOf(manager.createProblemDescriptor("Runblocking here"))
        }
        
        return null
    }


    override fun isGraphNeeded(): Boolean = true
}