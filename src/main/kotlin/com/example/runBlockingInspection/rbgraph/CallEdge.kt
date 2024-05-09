package com.example.runBlockingInspection.rbgraph

data class CallEdge(
    val parent: FunctionNode,
    val child: FunctionNode,
    val callSite: String,
    val strongCall: Boolean = true
) 
    
