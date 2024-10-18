package com.lhstack.extension

import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files

class InvokeStarterLibraryInstall {

    companion object {
        fun install() {
            val jar =
                InvokeStarterLibraryInstall::class.java.classLoader.getResourceAsStream("META-INF/invoke.jar")
            jar?.use {
                val bytes = it.readAllBytes()
                val dir = System.getProperty("user.home") + "/.ideaTools/CallThisMethod"
                File(dir).apply {
                    if (!this.exists()) {
                        this.mkdirs()
                    }
                    File(this, "invoke.jar").apply {
                        if (this.exists()) {
                            val existBytes = Files.readAllBytes(this.toPath())
                            if (existBytes.size != bytes.size) {
                                Files.write(this.toPath(), bytes)
                            }
                        } else {
                            Files.write(this.toPath(), bytes)
                        }
                    }
                }
                bytes
            }
        }

        fun unInstall() {
            val file = File(System.getProperty("user.home") + "/.ideaTools/CallThisMethod/invoke.jar")
            file.runCatching {
                FileUtils.delete(file)
            }
        }
    }
}