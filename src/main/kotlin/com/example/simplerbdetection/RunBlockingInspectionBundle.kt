package com.example.simplerbdetection

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.RunBlockingInspectionBundle"

object RunBlockingInspectionBundle {
    private val bundle = DynamicBundle(RunBlockingInspectionBundle::class.java, BUNDLE)
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = bundle.getMessage(key, *params)
}