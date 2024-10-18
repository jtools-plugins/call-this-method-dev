package com.lhstack

import com.intellij.ide.AppLifecycleListener
import com.lhstack.extension.InvokeStarterLibraryInstall
import com.lhstack.extension.LineMarkerProviderManager
import com.lhstack.extension.StarterJavaProgramPatcher

class PluginAppLifecycleListener: AppLifecycleListener {

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        InvokeStarterLibraryInstall.install()
        StarterJavaProgramPatcher.registry()
        LineMarkerProviderManager.registry()
    }
}