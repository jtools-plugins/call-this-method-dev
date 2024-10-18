package com.lhstack

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.lhstack.extension.disposeMessageConnection
import com.lhstack.extension.findIcon
import com.lhstack.extension.registryRunProfileListener
import com.lhstack.tools.plugins.Logger
import com.lhstack.view.CallThisMethodView

class ToolsMainWindowFactory : com.intellij.openapi.wm.ToolWindowFactory {

    override fun init(toolWindow: ToolWindow) {
        toolWindow.project.registryRunProfileListener()
        toolWindow.setIcon(this.findIcon("icons/logo_tab.svg")!!)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val view = CallThisMethodView(project,object:Logger{
            override fun info(p0: Any?) {
            }

            override fun warn(p0: Any?) {
            }

            override fun debug(p0: Any?) {
            }

            override fun error(p0: Any?) {
            }

            override fun activeConsolePanel(): Logger? {
                return this
            }

        })
        Disposer.register(project) {
            project.disposeMessageConnection()
        }
        Disposer.register(project, view)
        val factory = toolWindow.contentManager.factory
        toolWindow.contentManager.addContent(factory.createContent(view, "", true))
    }
}