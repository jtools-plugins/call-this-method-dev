package com.lhstack.extension

import com.intellij.execution.Executor
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.JavaProgramPatcher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import java.net.ServerSocket

class StarterJavaProgramPatcher : JavaProgramPatcher() {


    companion object {

        private val javaProgramPatcher: JavaProgramPatcher = StarterJavaProgramPatcher()

        fun registry() {
            val extensionPoint = ApplicationManager.getApplication().extensionArea.getExtensionPoint(EP_NAME)
            extensionPoint.registerExtension(javaProgramPatcher) {

            }
        }

        fun unRegistry() {
            val extensionPoint = ApplicationManager.getApplication().extensionArea.getExtensionPoint(EP_NAME)
            extensionPoint.unregisterExtension(StarterJavaProgramPatcher::class.java)
        }
    }

    override fun patchJavaParameters(executor: Executor, configuration: RunProfile, javaParameters: JavaParameters) {
        if(configuration is ApplicationConfiguration){
            ServerSocket(0).use {
                javaParameters.vmParametersList.add("-Didea.tools.plugin.run.server.port=${it.localPort}")
                configuration.putUserData(ModulePortKey,it.localPort)
            }

            javaParameters.classPath.add(System.getProperty("user.home") + "/.ideaTools/CallThisMethod/invoke.jar")
        }
    }
}