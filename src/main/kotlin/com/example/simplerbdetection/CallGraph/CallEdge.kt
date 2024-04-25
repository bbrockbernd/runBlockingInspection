package com.example.simplerbdetection.CallGraph

data class CallEdge(
    val parent: FunctionNode,
    val child: FunctionNode,
    val callSite: String,
    val strongCall: Boolean = true
) 
    
