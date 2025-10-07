package com.github.hungyanbin.intellijpluginpragent.toolWindow

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val project: Project, private val toolWindow: ToolWindow) {

        private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val configPanel = ConfigPanel()
        private val prNotesPanel = PRNotesPanel(project)

        fun getContent() = JBTabbedPane().apply {
            addTab("PR Notes", prNotesPanel)
            addTab("Config", configPanel)
        }

        fun cleanup() {
            coroutineScope.cancel()
            configPanel.cleanup()
            prNotesPanel.cleanup()
        }
    }


}
