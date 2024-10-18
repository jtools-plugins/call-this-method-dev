package com.lhstack

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.lhstack.extension.*
import com.lhstack.tools.plugins.IPlugin
import com.lhstack.tools.plugins.Logger
import com.lhstack.tools.plugins.PluginType
import com.lhstack.view.CallThisMethodView
import javax.swing.Icon
import javax.swing.JComponent

class PluginImpl : IPlugin {

    private val cache = hashMapOf<String, Disposable>()

    private val logCache = hashMapOf<String,Logger>()

    override fun install() {
        InvokeStarterLibraryInstall.install()
        StarterJavaProgramPatcher.registry()
        LineMarkerProviderManager.registry()
    }

    override fun unInstall() {
        StarterJavaProgramPatcher.unRegistry()
        LineMarkerProviderManager.unRegistry()
        ProjectManager.getInstance().openProjects.forEach {
            it.disposeMessageConnection()
        }
        cache.values.forEach { Disposer.dispose(it) }
        InvokeStarterLibraryInstall.unInstall()
        logCache.clear()
    }

    override fun closeProject(project:Project) {
        cache.remove(project.locationHash)?.let { Disposer.dispose(it) }
        project.disposeMessageConnection()
        logCache.remove(project.locationHash)
    }

    override fun installRestart(): Boolean {
        return true
    }

    override fun openProject(project: Project, logger: Logger, openThisPage: Runnable) {
        project.registryRunProfileListener()
        project.registryOpenThisPage(openThisPage)
        logCache[project.locationHash] = logger
    }

    override fun createPanel(project: Project): JComponent {
        return cache.computeIfAbsent(project.locationHash) {
            CallThisMethodView(project,logCache[project.locationHash]!!)
        } as JComponent
    }

    override fun pluginType(): PluginType {
        return PluginType.JAVA
    }

    override fun pluginIcon(): Icon? {
        return this.findIcon("icons/logo.svg")
    }

    override fun pluginTabIcon(): Icon? {
        return this.findIcon("icons/logo_tab.svg")
    }

    override fun pluginName(): String {
        return "Call This Method"
    }

    override fun pluginDesc(): String {
        return "Call This Method"
    }

    override fun pluginVersion(): String {
        return "v1.0.2"
    }

}