package com.example.runBlockingInspection.CallGraph

data class CallEdge(
    val parent: FunctionNode,
    val child: FunctionNode,
    val callSite: String,
    val strongCall: Boolean = true
) 
    
