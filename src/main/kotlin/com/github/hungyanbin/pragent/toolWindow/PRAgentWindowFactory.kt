package com.github.hungyanbin.pragent.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*


class PRAgentWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        content.setDisposer(myToolWindow)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val project: Project, private val toolWindow: ToolWindow) : Disposable {

        private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val configPanel = ConfigPanel()
        private val prEditorPanel = PREditorPanel(project)
        private val debugPanel = DebugPanel()

        fun getContent() = JBTabbedPane().apply {
            addTab("PR Editor", prEditorPanel)
            addTab("Config", configPanel)
            addTab("Debug", debugPanel)
        }

        override fun dispose() {
            coroutineScope.cancel()
            configPanel.cleanup()
            prEditorPanel.cleanup()
            debugPanel.cleanup()
        }
    }

}
