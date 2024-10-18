package com.lhstack.extension

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.json.json5.Json5FileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import com.lhstack.PluginImpl
import com.lhstack.state.AppState
import com.lhstack.view.PsiParameterItem
import com.lhstack.view.PsiReturnItem
import org.apache.commons.lang3.StringUtils
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.util.removeUserData
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent


val ModulePortKey = Key.create<Int>("Tools@CallThisMethod@ModulePortKey")

val ProjectPageKey = Key.create<Runnable>("Tools@CallThisMethod@ProjectPageKey")

val ProjectMessageConnectionKey = Key.create<MessageBusConnection>("Tools@CallThisMethod@MessageConnectionKey")

fun Project.registryOpenThisPage(runnable: Runnable) {
    this.putUserData(ProjectPageKey, runnable)
}

fun Project.openThisPage() {
    this.getUserData(ProjectPageKey)?.run()
}

fun Project.disposeMessageConnection() {
    this.getUserData(ProjectMessageConnectionKey)?.dispose()
}

fun Project.messageBusConnection(): MessageBusConnection {
    val messageBusConnection = this.getUserData(ProjectMessageConnectionKey)
    if (messageBusConnection == null) {
        val connect = this.messageBus.connect()
        this.putUserData(ProjectMessageConnectionKey, connect)
        return connect
    } else {
        return messageBusConnection
    }
}

fun <T> Project.syncPublish(topic: Topic<T>, consumer: (T) -> Unit) {
    consumer.invoke(this.messageBus.syncPublisher(topic))
}

fun Project.registryRunProfileListener() {
    this.messageBusConnection().apply {
        val portCache = hashMapOf<Long, Int>()
        this.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processStarted(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler,
            ) {
                if (env.runProfile is ApplicationConfiguration) {
                    ApplicationManager.getApplication().invokeLater {
                        val configuration = env.runProfile as ApplicationConfiguration
                        val p = configuration.project
                        val module = configuration.defaultModule
                        val port = configuration.getUserData(ModulePortKey)!!
                        AppState.INSTANCE.addPort(
                            p.name, module.name,
                            port
                        )
                        portCache[env.executionId] = port
                        p.syncPublish(ProcessListener.TOPIC) {
                            it.processStarted(module, configuration.getUserData(ModulePortKey)!!)
                        }
                    }
                }
            }

            override fun processTerminating(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler,
            ) {
                if (env.runProfile is ApplicationConfiguration) {
                    ApplicationManager.getApplication().invokeLater {
                        val configuration = env.runProfile as ApplicationConfiguration
                        val p = configuration.project
                        val module = configuration.defaultModule
                        portCache.remove(env.executionId)?.let { port ->
                            AppState.INSTANCE.removePort(
                                p.name, module.name,
                                port
                            )
                            configuration.removeUserData(ModulePortKey)
                            p.syncPublish(ProcessListener.TOPIC) {
                                it.processTerminating(module, port)
                            }
                        }
                    }
                }
            }
        })
    }
}

interface ProcessListener {
    companion object {
        val TOPIC = Topic.create("ToolsPlugin@CallThisMethod@ProcessListener", ProcessListener::class.java)
    }

    fun processStarted(module: Module, port: Int)

    fun processTerminating(module: Module, port: Int)
}

fun createEmptyBorderScrollPanel(component: JComponent): JBScrollPane {
    return JBScrollPane(component).apply { this.border = null }
}

fun PsiMethod.findModulesAndPort(): Map<String, ArrayList<Int>> {
    val modules = this.findModules()
    val result = mutableMapOf<String, ArrayList<Int>>()
    modules.filter { AppState.INSTANCE.contains(it.project.name, it.name) == true }
        .forEach { result[it.name] = AppState.INSTANCE.getPorts(it.project.name, it.name)!! }
    return result
}

fun PsiMethod.findModules(): MutableList<Module> {
    val psiClass = this.containingClass!!
    val module = ModuleUtilCore.findModuleForFile(psiClass.containingFile)
    if (module != null) {
        if (AppState.INSTANCE.contains(module.project.name, module.name) == true) {
            return arrayListOf(module)
        } else {
            //这里找不到的话,就不找了,返回所有满足条件的module,让使用者自己选择
            //此时这个module是一个被其他module依赖的
            val allDependentModules = ModuleUtilCore.getAllDependentModules(module)
            return allDependentModules.filter {
                AppState.INSTANCE.contains(it.project.name, it.name) == true
            }.toMutableList()
        }
    } else {
        return ModuleManager.getInstance(this.project).modules.filter {
            AppState.INSTANCE.contains(it.project.name, it.name) == true
        }.toMutableList()
    }
}

fun PsiParameter.isNormalType(): Boolean {
    val type = this.type
    if (type is PsiPrimitiveType) {
        return true
    }
    val psiClass = type.resolve()
    if (psiClass != null) {
        return psiClass.isNormalType()
    }
    return false
}

fun PsiType.qualifiedName(): String {
    return ApplicationManager.getApplication().runReadAction<String> {
        if (this is PsiPrimitiveType) {
            this.name
        } else if (this is PsiArrayType) {
            this.getDescriptor()
        } else {
            val psiClass = this.resolve()
            if (psiClass is PsiTypeParameter) {
                "java.lang.Object"
            } else {
                psiClass!!.qualifiedName!!
            }
        }
    }
}

fun PsiParameter.typeStr(): String {
    return this.type.qualifiedName()
}


/**
 * 普通类型
 */
fun PsiClass.isNormalType(): Boolean {
    return when (this.qualifiedName) {
        "java.lang.Integer" -> true
        "java.lang.Boolean" -> true
        "java.lang.Double" -> true
        "java.lang.Float" -> true
        "java.lang.Long" -> true
        "java.lang.Character" -> true
        "java.lang.Byte" -> true
        "java.lang.Short" -> true
        "java.lang.Void" -> true
        "java.lang.String" -> true
        else -> false
    }
}

fun PsiReturnItem.getMatchFileType(): LanguageFileType {
    if (this.type == 1 || this.type == 0) {
        return PlainTextFileType.INSTANCE
    }
    if (this.type == 2 || this.type == 3) {
        return Json5FileType.INSTANCE
    }
    return Json5FileType.INSTANCE
}

fun PsiParameterItem.getMatchFileType(): LanguageFileType {
    if (this.type == 1 || this.type == 0) {
        return PlainTextFileType.INSTANCE
    }
    if (this.type == 2 || this.type == 3) {
        return Json5FileType.INSTANCE
    }
    return Json5FileType.INSTANCE
}

fun PsiType.isVoid(): Boolean {
    if (this is PsiPrimitiveType) {
        return this == PsiType.VOID
    }
    return this.resolve()?.qualifiedName == "java.lang.Void"
}

fun PsiType.getMatchFiletype(): LanguageFileType {
    if (this is PsiPrimitiveType) {
        return PlainTextFileType.INSTANCE
    }
    if (this is PsiClassType) {
        return if (this.resolve()?.isNormalType() == true) {
            PlainTextFileType.INSTANCE
        } else {
            Json5FileType.INSTANCE
        }
    }
    return Json5FileType.INSTANCE
}

/**
 * @param [psiClass]
 * @return [Pair<String(presentableText),String(qualifiedName)>]
 */
fun PsiMethod.getPsiReturnType(psiClass: PsiClass?): PsiReturnItem? {
    val objectType =
        JavaPsiFacade.getInstance(project).elementFactory.createTypeByFQClassName(
            CommonClassNames.JAVA_LANG_OBJECT
        )
    val substitutors = psiClass!!.superTypes
        .map { it.resolveGenerics().substitutor }.filter { it !is EmptySubstitutor }.toList()
    var returnType = this.returnType
    if (substitutors.isNotEmpty()) {
        for (substitutor in substitutors) {
            val type = substitutor.substitute(returnType)
            if (type != null) {
                if (type is PsiClassType || type is PsiPrimitiveType || type is PsiArrayType) {
                    return PsiReturnItem(type.presentableText, type.qualifiedName(), type.getType(),type.isVoid())
                }
            }
            continue
        }
    } else {
        return returnType?.let { PsiReturnItem(it.presentableText, it.qualifiedName(), it.getType(),it.isVoid()) }
    }
    return PsiReturnItem(objectType.presentableText, objectType.qualifiedName(), 2,false)
}

/**
 * @param [psiClass]
 * @return [MutableList<Pair<String(parameter name), Pair<String(presentableText),String(qualifiedName)>>>]
 */
fun PsiMethod.getPsiParameters(psiClass: PsiClass?): MutableList<PsiParameterItem> {
    val objectType =
        JavaPsiFacade.getInstance(project).elementFactory.createTypeByFQClassName(
            CommonClassNames.JAVA_LANG_OBJECT
        )
    val substitutors = psiClass!!.superTypes
        .map { it.resolveGenerics().substitutor }.filter { it !is EmptySubstitutor }.toList()
    val parameterTypes = mutableListOf<Pair<PsiParameter, PsiType>>()
    val parameters = this.parameterList.parameters.toList()
    for (parameter in parameters) {
        var parameterType: PsiType? = null
        if (substitutors.isNotEmpty()) {
            for (substitutor in substitutors) {
                parameterType = substitutor.substitute(parameter.type)
                if (parameterType.resolve() !is PsiTypeParameter) {
                    parameterType = substitutor.substitute(parameterType)
                    parameterTypes.add(Pair(parameter, parameterType))
                    break
                }
            }
        } else {
            parameterType = parameter.type
            parameterTypes.add(Pair(parameter, parameter.type))
        }
        if (parameterType == null) {
            parameterTypes.add(Pair(parameter, objectType))
        }
    }
    return parameterTypes.map {
        PsiParameterItem(
            it.first.name,
            it.second.presentableText,
            it.second.qualifiedName(),
            it.second.getType()
        )
    }.toMutableList()
}

fun PsiType.getType(): Int {
    if (this is PsiPrimitiveType) {
        return 0
    }
    if (this is PsiArrayType) {
        return 3
    }
    var typePsiClass = this.resolve()
    if (typePsiClass != null) {
        return if (typePsiClass.isNormalType()) {
            0
        } else {
            2
        }
    }
    return return 2
}

fun PsiMethod.isStatic() = this.modifierList.hasModifierProperty("static")

fun PsiMethod.needMark(): Boolean {
    val psiClass = this.containingClass ?: return false
    val project = this.project

    if (this.isConstructor) {
        return false
    }
    if (this.isStatic()) {
        return true
    }

    if (!psiClass.hasAnno("org.springframework.stereotype.Component")) {
        //如果是抽象类或者接口
        return ClassInheritorsSearch.search(psiClass, GlobalSearchScope.allScope(project), true)
            .anyMatch { it.hasAnno("org.springframework.stereotype.Component") }
    }

    return true
}

fun PsiClass.hasAnno(anno: String, num: Int = 1): Boolean {
    if (this.hasAnnotation(anno)) {
        return true
    }
    if (num > 10) {
        return false
    }
    for (annotation in this.annotations) {
        val nameReferenceElement = annotation.nameReferenceElement
        if (nameReferenceElement != null) {
            val resolve = nameReferenceElement.resolve()
            if (resolve != null && resolve is PsiClass) {
                val hasAnno = resolve.hasAnno(anno, num + 1)
                if (hasAnno) {
                    return true
                }
            }
        }
    }
    return false
}

fun PsiArrayType.getDescriptor(): String {
    val descriptor = StringBuilder()
    var psiType: PsiType = this
    // 处理数组维度，逐级递归获取组件类型
    while (psiType is PsiArrayType) {
        descriptor.append("[")
        psiType = psiType.componentType
    }

    // 处理基本类型
    if (psiType is PsiPrimitiveType) {
        descriptor.append(getPrimitiveTypeDescriptor(psiType))
    } else if (psiType is PsiClassType) {
        val psiClass = psiType.resolve()
        if (psiClass != null) {
            val qualifiedName = psiClass.qualifiedName
            if (qualifiedName != null) {
                descriptor.append("L").append(qualifiedName).append(";")
            }
        }
    }

    return descriptor.toString()
}

/**
 * 获取基本类型的描述符。
 *
 * @param psiType 基本类型的 PsiType 对象
 * @return 基本类型的描述符
 */
private fun getPrimitiveTypeDescriptor(psiType: PsiType): String? {
    if (PsiType.INT == psiType) {
        return "I"
    } else if (PsiType.BOOLEAN == psiType) {
        return "Z"
    } else if (PsiType.BYTE == psiType) {
        return "B"
    } else if (PsiType.CHAR == psiType) {
        return "C"
    } else if (PsiType.DOUBLE == psiType) {
        return "D"
    } else if (PsiType.FLOAT == psiType) {
        return "F"
    } else if (PsiType.LONG == psiType) {
        return "J"
    } else if (PsiType.SHORT == psiType) {
        return "S"
    }
    return null // 未知类型
}

fun Any.findIcon(icon: String): Icon? {
    return IconLoader.findIcon(icon, PluginImpl::class.java)
}


fun Project.getAllModuleLibraries(project: Project): List<File> {
    return ApplicationManager.getApplication().runReadAction<List<File>> {
        val map: MutableMap<String, File> = HashMap()
        val moduleManager = ModuleManager.getInstance(project)
        for (module in moduleManager.modules) {
            val moduleRootManager = ModuleRootManager.getInstance(module)
            for (resource in moduleRootManager.getSourceRoots(JavaResourceRootType.RESOURCE)) {
                map[resource.presentableUrl] = File(resource.presentableUrl)
            }
            val outputPath = CompilerPaths.getModuleOutputPath(module, false)
            outputPath?.let {
                if (StringUtils.isNotBlank(it)) {
                    map[it] = File(outputPath)
                }
            }

            val orderEntries = moduleRootManager.orderEntries
            for (orderEntry in orderEntries) {
                if (orderEntry is LibraryOrderEntry) {
                    if (orderEntry.scope == DependencyScope.COMPILE || DependencyScope.RUNTIME == orderEntry.scope) {
                        val library = orderEntry.library
                        if (library != null) {
                            for (file in library.getFiles(OrderRootType.CLASSES)) {
                                map[file.name] = File(file.presentableUrl)
                            }
                        }
                    }
                }
            }
        }

        java.util.ArrayList(map.values)
    }
}

fun <T, R> T.readAction(block: T.() -> R): R {
    return ApplicationManager.getApplication().runReadAction<R> { block() }
}

fun <T, R> T.invokeLater(block: T.() -> R) {
    return ApplicationManager.getApplication().invokeLater { block() }
}

fun Project.bgTask(title: String, block: () -> Unit) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(this, title, false) {
        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = true
            block()
        }
    })
}

fun String?.groovyScript(): String {
    if (this == null || this.isBlank()) {
        return """
            import org.springframework.context.ApplicationContext
            def attributes = ctx.attributes as Map<String,Object>
            def parameterMap = ctx.parameterMap as Map<String,Object>
            def context = ctx.context as ApplicationContext
        """.trimIndent()
    }
    return this
}