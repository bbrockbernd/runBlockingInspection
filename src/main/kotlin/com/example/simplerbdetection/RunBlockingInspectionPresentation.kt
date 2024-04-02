package com.example.simplerbdetection

import com.example.simplerbdetection.Services.DetectRunBlockingService
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ui.DefaultInspectionToolPresentation
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.profile.codeInspection.ui.readHTML
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.StartupUiUtil.labelFont
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

class RunBlockingInspectionPresentation(
    toolWrapper: InspectionToolWrapper<*, *>,
    context: GlobalInspectionContextImpl
) : DefaultInspectionToolPresentation(toolWrapper, context) {

    override fun getCustomPreviewPanel(descriptor: CommonProblemDescriptor, parent: Disposable): JComponent? {
        if (descriptor is ProblemDescriptor) {
            val stackTrace = descriptor.psiElement.project.service<DetectRunBlockingService>().isInAsyncContext(descriptor.psiElement.parent)
            if (stackTrace != null) {
                val htmlView = DescriptionEditorPane()
                val css = (htmlView.editorKit as HTMLEditorKit).styleSheet
                css.addRule("p.problem-description-group {text-indent: " + scale(9) + "px;font-weight:bold;}")
                css.addRule("div.problem-description {margin-left: " + scale(9) + "px;}")
                css.addRule("ul {margin-left:" + scale(10) + "px;text-indent: 0}")
                css.addRule("code {font-family:" + labelFont.family + "}")
                htmlView.addHyperlinkListener(object : HyperlinkAdapter() {
                    override fun hyperlinkActivated(e: HyperlinkEvent) {
                        val url = e.url ?: return
                        val ref: @NonNls String? = url.ref
                        val offset = ref!!.toInt()
                        var fileURL = url.toExternalForm()
                        fileURL = fileURL.substring(0, fileURL.indexOf('#'))
                        var vFile = VirtualFileManager.getInstance().findFileByUrl(fileURL)
                        if (vFile == null) {
                            vFile = VfsUtil.findFileByURL(url)
                        }
                        if (vFile != null) {
                            val descriptor =
                                PsiNavigationSupport.getInstance().createNavigatable(project, vFile, offset)
                            descriptor.navigate(true)
                        }
                    }
                })
                htmlView.readHTML(getDescription(stackTrace))
                return htmlView
            }
        }
        return null
    }


    private fun getDescription(callStack: List<Pair<String, String>>): String {
        return buildString {
            append("<div class=\"problem-description\"><h>RunBlocking in coroutine</h><p>")
            callStack.forEach { append("<a HREF=${it.second}>${it.first}</a><br>") }
            append("</p></div>")
        }
    }

}