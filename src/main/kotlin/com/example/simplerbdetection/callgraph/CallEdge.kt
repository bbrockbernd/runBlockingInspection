package com.example.simplerbdetection.callgraph

data class CallEdge(
    val parent: FunctionNode,
    val child: FunctionNode,
    val callSite: String,
    val strongCall: Boolean = true
) 
    
