package com.lhstack.view

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.designer.actions.AbstractComboBoxAction
import com.intellij.icons.AllIcons
import com.intellij.json.json5.Json5FileType
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.ActiveIcon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.JBSplitter
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.SortedListModel
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.TabLabel
import com.lhstack.api.*
import com.lhstack.components.MultiLanguageTextField
import com.lhstack.extension.*
import com.lhstack.state.AppState
import com.lhstack.state.CallThisMethodState
import com.lhstack.tools.plugins.Logger
import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.groovy.GroovyFileType
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import javax.swing.*


/**
 * type: 0: primitive 1: normal 2: object 3: array
 */
class PsiParameterItem(var name:String,var presentableText:String,var qualifiedName:String,var type: Int)


/**
 * type: 0: primitive 1: normal 2: object 3: array
 */
class PsiReturnItem(var presentableText:String,var qualifiedName:String,var type: Int,var isVoid: Boolean)

class TabObj(
    val psiClass: String?,
    var preScript: String = "",
    var postScript: String = "",
    var psiParameters: MutableList<PsiParameterItem> = mutableListOf(), //1: parameterName 2.1: presentableText 2.2: type
    var psiResultType: PsiReturnItem? = null,//1: presentableText 2: type
    var psiMethodName: String = "",
    var static: Boolean = false,
    var parameters: MutableMap<String, String> = mutableMapOf(),
    var resp: MultiLanguageTextField? = null,
    //调用类型 method: 方法 script: 脚本
    var type: String? = "method",
    var originPsiClass: PsiClass? = null,
    //调用代理对象
    var invokeProxy: Boolean = true,
    val disposable: Disposable,
) {
    fun findMethod(project: Project): PsiMethod? {
        return this.psiClass?.let {
            JavaPsiFacade.getInstance(project).findClass(it, GlobalSearchScope.allScope(project))?.let { clazz ->
                clazz.allMethods.firstOrNull { it.parameterList.parameters.joinToString { p -> p.name } == psiParameters.joinToString { p -> p.name } && it.name == this.psiMethodName }
            }
        }
    }
}

class TabObjData(
    val psiClass: String,
    val psiMethod: String,
    var preScript: String,
    var postScript: String,
    var parameters: MutableMap<String, String> = mutableMapOf(),
    var parameterTypes: MutableList<String> = mutableListOf(),
    var parameterNames: MutableList<String> = mutableListOf(),
    var parameterKey: String = "",
    var parameterTypeKey: String = "",
    var type: String? = "method",
    var resp: String? = "",
    var date: String = "",
    var moduleName: String = "",
    var parameterStr: String = "",
    var originPsiClass: String = "",
    var invokeProxy: Boolean = true,
) {


    companion object {
        fun from(tabObj: TabObj): TabObjData {
            return this.readAction {
                TabObjData(
                    psiClass = tabObj.psiClass?: "",
                    psiMethod = tabObj.psiMethodName,
                    preScript = tabObj.preScript,
                    postScript = tabObj.postScript,
                    parameters = hashMapOf<String, String>().apply { this.putAll(tabObj.parameters) },
                    parameterTypes = tabObj.psiParameters.map { it.qualifiedName }.toMutableList(),
                    parameterNames = tabObj.psiParameters.map { it.name }.toMutableList(),
                    parameterKey = tabObj.parameters.entries.joinToString(",") { "${it.key} = ${it.value}" },
                    parameterTypeKey = tabObj.psiParameters.joinToString(",") { it.qualifiedName },
                    type = tabObj.type,
                    resp = tabObj.resp?.text,
                    date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    parameterStr = tabObj.psiParameters.joinToString(",") {
                        "${it.presentableText} ${it.name}"
                    },
                    originPsiClass = tabObj.originPsiClass?.qualifiedName ?: "",
                    invokeProxy = tabObj.invokeProxy
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TabObjData

        if (psiClass != other.psiClass) return false
        if (psiMethod != other.psiMethod) return false
        if (preScript != other.preScript) return false
        if (postScript != other.postScript) return false
        if (parameterKey != other.parameterKey) return false
        if (parameterTypeKey != other.parameterTypeKey) return false
        if (type != other.type) return false
        if (moduleName != other.moduleName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = psiClass.hashCode()
        result = 31 * result + psiMethod.hashCode()
        result = 31 * result + preScript.hashCode()
        result = 31 * result + postScript.hashCode()
        result = 31 * result + parameterKey.hashCode()
        result = 31 * result + parameterTypeKey.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + moduleName.hashCode()
        return result
    }
}

class CallThisMethodView(private val project: Project, private val logger: Logger) : SimpleToolWindowPanel(true),
    Disposable {
    val tabs = JBTabsFactory.createEditorTabs(project, this)
    val moduleSelectAction = object : AbstractComboBoxAction<String>() {

        init {
            project.messageBusConnection().subscribe(ProcessListener.TOPIC, object : ProcessListener {
                override fun processStarted(module: Module, port: Int) {
                    tabs.selectedInfo?.let {
                        val tabObj = it.`object` as TabObj
                        refreshSelect(tabObj)
                    }
                }

                override fun processTerminating(module: Module, port: Int) {
                    tabs.selectedInfo?.let {
                        val tabObj = it.`object` as TabObj
                        refreshSelect(tabObj)
                    }
                }

            })
            setItems(mutableListOf(), "")
        }

        override fun update(item: String, presentation: Presentation, popup: Boolean) {
            if (StringUtils.isEmpty(item)) {
                presentation.text = "当前没有可用模块"
            } else {
                presentation.text = item
            }
        }

        override fun selectionChanged(item: String): Boolean {
            return !StringUtils.equals(item, selection)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }

    }

    init {

        tabs.presentation.setTabDraggingEnabled(true)
        tabs.addListener(object : TabsListener {
            override fun tabRemoved(info: TabInfo) {
                if (info.`object` is TabObj) {
                    (info.`object` as TabObj).disposable.dispose()
                }
                if (tabs.tabCount == 0) {
                    moduleSelectAction.setItems(arrayListOf(), "")
                }
            }

            override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
                if (newSelection == null) {
                    moduleSelectAction.setItems(arrayListOf(), "")
                    return
                }
                newSelection.let {
                    val tabObj = it.`object` as TabObj
                    refreshSelect(tabObj)
                }
            }
        }, project)


        val toolbar = ActionManager.getInstance()
            .createActionToolbar("Tools@CallThisMethod@ViewActionToolbar", DefaultActionGroup().apply {

                this.add(object : AnAction({ "新增脚本" }, this.findIcon("icons/groovy.svg")) {
                    override fun actionPerformed(e: AnActionEvent) {
                        tabs.select(tabs.addTab(createScriptTab("")), true)
                    }

                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }
                })

                this.add(object : AnAction({ "查看历史记录" }, this.findIcon("icons/history.svg")) {
                    override fun actionPerformed(e: AnActionEvent) {
                        showHistoryDialog()
                    }

                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }
                })

                this.add(object : AnAction({ "设置" }, this.findIcon("icons/setting.svg")) {
                    override fun actionPerformed(e: AnActionEvent) {
                        SettingDialog(project).showAndGet()
                    }

                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }
                })

                this.add(moduleSelectAction)

                this.add(object : AnAction({ "运行" }, this.findIcon("icons/execute.svg")) {
                    override fun actionPerformed(e: AnActionEvent) {
                        if (StringUtils.isNotBlank(moduleSelectAction.selection)) {
                            onlineRun()
                        } else {
                            //离线执行
                            offlineRun()
                        }
                    }

                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }
                })
            }, true)
        toolbar.targetComponent = this
        this.toolbar = JPanel(BorderLayout()).apply { this.add(toolbar.component, BorderLayout.EAST) }
        this.setContent(tabs.component)
        tabs.setPopupGroup(DefaultActionGroup().apply {
            this.add(object : AnAction({ "关闭其他标签" }, this.findIcon("icons/close_other.svg")) {
                override fun actionPerformed(e: AnActionEvent) {
                    e.getData(LangDataKeys.CONTEXT_COMPONENT)?.let {
                        if (it is TabLabel) {
                            val info = it.info
                            tabs.tabs.forEach { tab ->
                                if (tab != info) {
                                    tabs.removeTab(tab)
                                }
                            }
                        }
                    }
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })


            this.add(object : AnAction({ "关闭所有标签" }, this.findIcon("icons/close_all.svg")) {
                override fun actionPerformed(e: AnActionEvent) {
                    tabs.removeAllTabs()
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })

            this.add(object : AnAction({ "定位到方法" }, this.findIcon("icons/position.svg")) {

                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.getData(LangDataKeys.CONTEXT_COMPONENT)?.let {
                        if (it is TabLabel) {
                            val tabObj = it.info.`object` as TabObj
                            if (tabObj.type != "method") {
                                e.presentation.isEnabled = false
                                e.presentation.isVisible = false
                            } else {
                                e.presentation.isEnabled = true
                                e.presentation.isVisible = true
                            }
                        }
                    }
                }

                override fun actionPerformed(e: AnActionEvent) {
                    e.getData(LangDataKeys.CONTEXT_COMPONENT)?.let {
                        if (it is TabLabel) {
                            val tabObj = it.info.`object` as TabObj
                            ApplicationManager.getApplication().invokeLater {
                                tabObj.originPsiClass?.let { psiClass ->
                                    psiClass.containingFile?.let { psiFile ->
                                        tabObj.findMethod(project)?.let {
                                            val fileEditorManager = FileEditorManager.getInstance(project)
                                            val fileDescriptor = OpenFileDescriptor(
                                                project,
                                                psiFile.virtualFile,
                                                it.nameIdentifier?.textOffset ?: it.textOffset
                                            )
                                            fileEditorManager.openFileEditor(fileDescriptor, true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })

            this.add(object : AnAction({ "复制标签" }, this.findIcon("icons/copy.svg")) {
                override fun actionPerformed(e: AnActionEvent) {
                    e.getData(LangDataKeys.CONTEXT_COMPONENT)?.let {
                        if (it is TabLabel) {
                            val tabObj = it.info.`object` as TabObj
                            if (tabObj.type == "method") {
                                if (tabObj.findMethod(project) == null) {
                                    Messages.showErrorDialog(null, "方法不存在,请检查你的方法是否被删除")
                                    return
                                }
                                tabs.addTab(
                                    createInvokeTab(
                                        tabObj
                                    )
                                )
                            } else {
                                tabs.addTab(createScriptTab(tabObj.preScript))
                            }
                        }
                    }
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })

        }, "Tools@CallThisMethod@ViewTabGroup", true)
        project.messageBusConnection().subscribe(MarkClickListener.TOPIC, object : MarkClickListener {
            override fun onMessage(psiMethod: PsiMethod, psiClass: PsiClass?, originPsiClass: PsiClass?) {

                val tabObj = TabObj(
                    psiClass?.qualifiedName,
                    "",
                    "",
                    psiMethod.getPsiParameters(psiClass),
                    psiMethod.getPsiReturnType(psiClass),
                    psiMethod.name,
                    psiMethod.isStatic(),
                    originPsiClass = originPsiClass,
                ) {}
                var tabObjData = TabObjData.from(tabObj)
                var cacheData =
                    CallThisMethodState.getInstance(project).methodCache["${tabObjData.originPsiClass}#${tabObjData.psiClass}#${tabObjData.psiMethod}#${tabObjData.parameterTypeKey}"]
                if (cacheData != null) {
                    val tabObj = TabObj(
                        psiClass?.qualifiedName,
                        cacheData.preScript,
                        cacheData.postScript,
                        tabObj.psiParameters,
                        tabObj.psiResultType,
                        psiMethod.name,
                        psiMethod.isStatic(),
                        mutableMapOf<String, String>().apply { this.putAll(cacheData.parameters) },
                        originPsiClass = originPsiClass,
                    ) {}
                    val tab = createInvokeTab(tabObj)
                    tabs.addTab(tab)
                    tabs.select(tab, true)
                } else {
                    val tab = createInvokeTab(tabObj)
                    tabs.addTab(tab)
                    tabs.select(tab, true)
                }
            }
        })
    }

    private fun onlineRun() {
        if (tabs.selectedInfo != null) {
            val tabObj = tabs.selectedInfo!!.`object` as TabObj

            tabObj.resp?.let {
                this.invokeLater {
                    it.text = ""
                }
            }
            //脚本调用
            if (tabObj.type == "script") {
                val script = tabObj.preScript
                val port = moduleSelectAction.selection.split(":")[1]
                ProgressManager.getInstance()
                    .run(object : Task.Backgroundable(project, "InvokeScript") {
                        override fun run(indicator: ProgressIndicator) {
                            try {
                                indicator.text = "开始执行"
                                var result: String
                                val resp = Api.invokeScript(script, port)
                                if (StringUtils.isBlank(resp.result)) {
                                    result = "执行成功"
                                } else {
                                    result = resp.result!!
                                }
                                this.invokeLater {
                                    if (tabObj.resp != null) {
                                        tabObj.resp?.let {
                                            it.text = result
                                            reformat(it) {
                                                addHistory(
                                                    tabObj,
                                                    moduleSelectAction.selection.split(":")[0]
                                                )
                                            }
                                        }
                                    } else {
                                        addHistory(
                                            tabObj,
                                            moduleSelectAction.selection.split(":")[0]
                                        )
                                    }
                                    indicator.text =
                                        "结束执行"
                                }
                                SwingUtilities.invokeLater {
                                    resp.logEntities?.let {
                                        if (it.isNotEmpty()) {
                                            it.forEach { log ->
                                                when (log.type) {
                                                    "debug" -> logger.debug(log.value)
                                                    "info" -> logger.info(log.value)
                                                    "warn" -> logger.warn(log.value)
                                                    "error" -> logger.error(log.value)
                                                }
                                            }
                                        }
                                    }

                                }
                            } catch (e: Throwable) {
                                val err =
                                    e.toString() + e.stackTrace.joinToString("\r\n") { it.toString() }
                                this.invokeLater {
                                    tabObj.resp?.let {
                                        it.text = err
                                        addHistory(
                                            tabObj,
                                            moduleSelectAction.selection.split(":")[0]
                                        )
                                    }
                                    Notifications.Bus.notify(
                                        Notification(
                                            "",
                                            "执行方法失败",
                                            err,
                                            NotificationType.ERROR
                                        ), project
                                    )
                                }
                            }
                        }

                    })
            }
            //执行方法反射调用
            if (tabObj.type == "method") {
                val port = moduleSelectAction.selection.split(":")[1]
                var modulePreScript = ""
                var modulePostScript = ""
                val selection = moduleSelectAction.selection
                if (StringUtils.isNotBlank(selection)) {
                    modulePreScript =
                        CallThisMethodState.getInstance(project).modulePreScript[selection.split(
                            ":"
                        )[0]]
                            ?: ""

                    modulePostScript =
                        CallThisMethodState.getInstance(project).modulePostScript[selection.split(
                            ":"
                        )[0]]
                            ?: ""
                }
                ProgressManager.getInstance().run(object : Task.Backgroundable(
                    project,
                    this.readAction { "invokeMethod: ${tabObj.psiClass}#${tabObj.psiMethodName}" }
                ) {

                    override fun run(indicator: ProgressIndicator) {
                        try {
                            this.readAction {
                                indicator.text =
                                    "开始执行: ${tabObj.psiClass}#${tabObj.psiMethodName}"
                            }

                            val result = Api.invokeMethod(
                                port.toInt(),
                                ApplicationManager.getApplication()
                                    .runReadAction<MethodInvokeEntity> {
                                        MethodInvokeEntity(
                                            tabObj.psiClass,
                                            tabObj.psiMethodName,
                                            if (tabObj.static) {
                                                "static"
                                            } else {
                                                "spring"
                                            },
                                            tabObj.psiParameters.map {
                                                InvokeParameter(
                                                    it.name,
                                                    it.qualifiedName,
                                                    tabObj.parameters[it.name] ?: ""
                                                )
                                            }.toTypedArray(),
                                            arrayOf(
                                                CallThisMethodState.getInstance().preScript,
                                                CallThisMethodState.getInstance(project).preScript,
                                                modulePreScript,
                                                tabObj.preScript
                                            ),
                                            arrayOf(
                                                CallThisMethodState.getInstance().postScript,
                                                CallThisMethodState.getInstance(project).postScript,
                                                modulePostScript,
                                                tabObj.postScript
                                            ),
                                            tabObj.invokeProxy
                                        )
                                    }
                            )
                            this.invokeLater {
                                if (tabObj.resp != null) {
                                    tabObj.resp?.let {
                                        it.text = result
                                        reformat(it) {
                                            addHistory(
                                                tabObj,
                                                moduleSelectAction.selection.split(":")[0]
                                            )
                                        }
                                    }
                                } else {
                                    addHistory(tabObj, moduleSelectAction.selection.split(":")[0])
                                }
                                indicator.text =
                                    "结束执行: ${tabObj.psiClass}#${tabObj.psiMethodName}"
                            }
                        } catch (e: Throwable) {
                            val err =
                                e.toString() + e.stackTrace.joinToString("\r\n") { it.toString() }
                            this.invokeLater {
                                tabObj.resp?.let {
                                    it.text = err
                                    addHistory(tabObj, moduleSelectAction.selection.split(":")[0])
                                }
                                Notifications.Bus.notify(
                                    Notification(
                                        "",
                                        "执行方法失败",
                                        err,
                                        NotificationType.ERROR
                                    ), project
                                )
                            }
                        }
                    }

                })
            }
        } else {
            SwingUtilities.invokeLater {
                Messages.showErrorDialog("请先在编辑器中右键添加一个函数吧", "警告")
            }
        }
    }

    private fun offlineRun() {
        tabs.selectedInfo?.apply {
            val obj = this.`object` as TabObj
            if (obj.type == "method") {
                Messages.showInfoMessage("离线模式仅可执行脚本", "提示")
            } else {
                obj.resp?.let {
                    this.invokeLater {
                        it.text = ""
                    }
                }
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "离线执行脚本") {
                    override fun run(indicator: ProgressIndicator) {
                        try {
                            val binds = Binding()
                            binds.setVariable("log", logger)
                            val groovyShell = GroovyShell(binds)
                            val libraries = project.getAllModuleLibraries(project)
                            libraries.forEach {
                                groovyShell.classLoader.addURL(it.toURI().toURL())
                            }
                            val script = groovyShell.parse(obj.preScript)
                            val task = FutureTask<String>(Callable {
                                return@Callable script.run()?.let {
                                    if (it is String) {
                                        it
                                    } else {
                                        gson.toJson(it)
                                    }
                                } ?: ""
                            })
                            Thread(task).start()
                            val result = task.get(30, TimeUnit.SECONDS)
                            if (StringUtils.isNotBlank(result)) {
                                obj.resp?.let {
                                    this.invokeLater {
                                        it.text = result
                                        reformat(it) {
                                            addHistory(obj, "")
                                        }
                                    }
                                }
                            } else {
                                addHistory(obj, "")
                            }
                        } catch (e: Throwable) {
                            this.invokeLater {
                                obj.resp?.let {
                                    it.text = e.toString() + e.stackTrace.joinToString("\r\n") { s -> s.toString() }
                                    addHistory(obj, "")
                                }
                            }
                        }
                    }
                })
            }
        }
    }

    private fun addHistory(tabObj: TabObj, module: String) {
        val historyTotal = CallThisMethodState.getInstance(project).historyTotal
        if(historyTotal == 0){
            return
        }
        val history = CallThisMethodState.getInstance(project).history
        val tabObjData = TabObjData.from(tabObj)
        tabObjData.moduleName = module
        if("method" == tabObj.type){
            CallThisMethodState.getInstance(project).methodCache["${tabObjData.originPsiClass}#${tabObjData.psiClass}#${tabObjData.psiMethod}#${tabObjData.parameterTypeKey}"] =
                tabObjData
        }

        if (history.contains(tabObjData)) {
            return
        }
        if (history.size >= historyTotal) {
            history.removeAt(0)
        }
        history.add(tabObjData)
    }

    private fun showHistoryDialog() {
        val dialog = object : DialogWrapper(project, false) {
            val jbList: JBList<TabObjData>
            val listModel: SortedListModel<TabObjData>

            init {
                this.title = "历史记录"
                this.setSize(1000, 600)
                listModel = SortedListModel(CallThisMethodState.getInstance(project).history) { o1, o2 ->
                    if (LocalDateTime.parse(o2.date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).isAfter(
                            LocalDateTime.parse(
                                o1.date,
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            )
                        )
                    ) {
                        1
                    } else {
                        -1
                    }
                }
                jbList = JBList(listModel)
                object : ListSpeedSearch<TabObjData>(jbList,
                    { "${it.psiClass}#${it.psiMethod}(${it.parameterStr})${it.preScript}${it.postScript}" }) {
                    override fun compare(text: String, pattern: String?): Boolean {
                        return StringUtils.containsIgnoreCase(text, pattern)
                    }

                    override fun findElement(s: String): Any? {
                        val result = super.findElement(s)
                        if (result == null) {
                            super.myComponent.clearSelection()
                            return null
                        } else {
                            return result
                        }
                    }
                }
                jbList.selectionMode = ListSelectionModel.SINGLE_SELECTION
                jbList.installCellRenderer {
                    JPanel().apply {
                        this.layout = BoxLayout(this, BoxLayout.X_AXIS)
                        this.add(if (it.type == "script") {
                            val scriptText = it.preScript.replace("\n", "\\n").let { str ->
                                if (str.length <= 90) {
                                    str
                                } else {
                                    str.substring(0, 87) + "..."
                                }
                            }
                            JLabel(scriptText, this.findIcon("icons/script.svg"), JLabel.LEFT)
                        } else {
                            JLabel(
                                "${it.psiClass}#${it.psiMethod}(${it.parameterStr})",
                                this.findIcon("icons/method.svg"),
                                JLabel.LEFT
                            )
                        })
                        this.add(Box.createHorizontalGlue())
                        this.add(JLabel(it.date, JLabel.RIGHT))
                    }
                }
                jbList.addMouseListener(object : MouseAdapter() {

                    override fun mouseClicked(e: MouseEvent) {
                        // 获取点击位置的索引
                        val index = jbList.locationToIndex(e.point)
                        // 检查点击是否位于项目上
                        if (index != -1) {
                            // 获取项目的范围
                            val cellBounds = jbList.getCellBounds(index, index)
                            // 检查点击点是否在项目范围内
                            if (cellBounds.contains(e.point)) {
                                // 点击的有效区域，允许正常选择
                                jbList.setSelectedIndex(index)
                            } else {
                                // 点击空白区域，取消选择
                                jbList.clearSelection()
                            }
                        } else {
                            // 点击空白区域，取消选择
                            jbList.clearSelection()
                        }
                        if (SwingUtilities.isRightMouseButton(e)) {
                            jbList.selectedValue?.let { _ ->
                                val popup = JBPopupFactory.getInstance()
                                    .createActionGroupPopup("操作", DefaultActionGroup().apply {
                                        this.add(object : AnAction({ "添加到调用栈" }) {
                                            override fun actionPerformed(e: AnActionEvent) {
                                                addToInvoke()
                                            }

                                            override fun getActionUpdateThread(): ActionUpdateThread {
                                                return ActionUpdateThread.EDT
                                            }
                                        })

                                        this.add(object : AnAction({ "删除选中数据" }) {
                                            override fun actionPerformed(e: AnActionEvent) {
                                                removeSelect()
                                            }

                                            override fun getActionUpdateThread(): ActionUpdateThread {
                                                return ActionUpdateThread.EDT
                                            }
                                        })
                                    }, DataContext.EMPTY_CONTEXT, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)

                                popup.show(RelativePoint(e.component, e.point))
                            }
                        }
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            jbList.selectedValue?.let { tabObjData ->
                                if (tabObjData.type == "script") {
                                    val disposable = Disposer.newDisposable()
                                    val code = MultiLanguageTextField(
                                        GroovyFileType.GROOVY_FILE_TYPE,
                                        project,
                                        tabObjData.preScript,
                                        viewer = true
                                    ).apply { Disposer.register(disposable, this) }
                                    val popup = JBPopupFactory.getInstance()
                                        .createComponentPopupBuilder(JBSplitter(true).apply {
                                            this.firstComponent = createTitlePanel("脚本", JBScrollPane(code))
                                            tabObjData.resp?.let {
                                                if (StringUtils.isNotBlank(it)) {
                                                    this.secondComponent = createTitlePanel(
                                                        "响应内容",
                                                        JBScrollPane(
                                                            MultiLanguageTextField(
                                                                Json5FileType.INSTANCE,
                                                                project,
                                                                tabObjData.resp ?: "",
                                                                viewer = true
                                                            ).apply {
                                                                Disposer.register(disposable, this)
                                                            })
                                                    )
                                                    this.proportion = 0.5f;
                                                    this.setHonorComponentsMinimumSize(true)
                                                    this.dividerWidth = 7
                                                }
                                            }
                                        }, jbList)
                                        .setMinSize(Dimension(600, 500))
                                        .setRequestFocus(true)
                                        .setFocusable(true)
                                        .setTitle("脚本: ${tabObjData.date}")
                                        .setTitleIcon(ActiveIcon(this.findIcon("icons/script.svg")))
                                        .setResizable(true)
                                        .setMovable(true)
                                        .setCancelOnClickOutside(true)
                                        .createPopup()
                                    popup.size = Dimension(600, 500)
                                    Disposer.register(popup, disposable)
                                    val xOffset = 20 // X轴上的偏移（右侧）
                                    val yOffset = 20 // Y轴上的偏移（下方）
                                    val point: Point = e.point
                                    val adjustedPoint = Point(point.x + xOffset, point.y + yOffset) // 调整后的位置
                                    popup.show(RelativePoint(e.component, adjustedPoint))
                                } else {
                                    val parent = Disposer.newDisposable()

                                    val codeText = ApplicationManager.getApplication().runReadAction<String> {
                                        val psiClass = JavaPsiFacade.getInstance(project).findClass(
                                            tabObjData.psiClass,
                                            GlobalSearchScope.allScope(project)
                                        )
                                        if (psiClass != null) {
                                            psiClass.allMethods.firstOrNull {
                                                val methodParameter =
                                                    it.parameterList.parameters.joinToString(",") { p -> p.name }
                                                val historyMethodParameter = tabObjData.parameterNames.joinToString(",")
                                                methodParameter == historyMethodParameter && it.name == tabObjData.psiMethod
                                            }?.text ?: ""
                                        } else {
                                            ""
                                        }
                                    }

                                    val code = MultiLanguageTextField.groovy(project, codeText, parent, isViewer = true)

                                    WriteCommandAction.runWriteCommandAction(project) {
                                        PsiDocumentManager.getInstance(project).getPsiFile(code.document)?.let {
                                            PsiDocumentManager.getInstance(project).commitDocument(code.document)
                                            ReformatCodeProcessor(it, false).run()
                                        }
                                    }
                                    val parameterText = tabObjData.parameterNames.joinToString("\n") {
                                        "$it = ${tabObjData.parameters[it]}"
                                    }
                                    val parameter =
                                        MultiLanguageTextField(
                                            PropertiesFileType.INSTANCE,
                                            project,
                                            parameterText,
                                            viewer = true
                                        ).apply {
                                            Disposer.register(parent, this)
                                        }

                                    val panel = JPanel(BorderLayout()).apply {
                                        this.add(JBSplitter(true).apply {
                                            this.firstComponent = JBSplitter(false).apply {
                                                this.secondComponent = createTitlePanel("代码", JBScrollPane(code))
                                                if (StringUtils.isNotBlank(parameterText)) {
                                                    this.firstComponent =
                                                        createTitlePanel("参数", JBScrollPane(parameter))
                                                }
                                            }
                                            if (StringUtils.isNotBlank(tabObjData.preScript) || StringUtils.isNotBlank(
                                                    tabObjData.postScript
                                                ) || StringUtils.isNotBlank(tabObjData.resp)
                                            ) {
                                                this.secondComponent = JBSplitter(true).apply {
                                                    if (StringUtils.isNotBlank(tabObjData.preScript) || StringUtils.isNotBlank(
                                                            tabObjData.postScript
                                                        )
                                                    ) {
                                                        this.firstComponent = JBSplitter(false).apply {
                                                            tabObjData.preScript.let {
                                                                if (StringUtils.isNotBlank(it)) {
                                                                    this.firstComponent = createTitlePanel(
                                                                        "前置脚本",
                                                                        JBScrollPane(
                                                                            MultiLanguageTextField.groovy(
                                                                                project,
                                                                                it,
                                                                                parent,
                                                                                isViewer = true
                                                                            )
                                                                        )
                                                                    )
                                                                }
                                                            }

                                                            tabObjData.postScript.let {
                                                                if (StringUtils.isNotBlank(it)) {
                                                                    this.secondComponent = createTitlePanel(
                                                                        "后置脚本",
                                                                        JBScrollPane(
                                                                            MultiLanguageTextField.groovy(
                                                                                project,
                                                                                it,
                                                                                parent,
                                                                                isViewer = true
                                                                            )
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    tabObjData.resp?.let {
                                                        if (StringUtils.isNoneBlank(it)) {
                                                            this.secondComponent = createTitlePanel(
                                                                "响应", JBScrollPane(MultiLanguageTextField(
                                                                    Json5FileType.INSTANCE,
                                                                    project,
                                                                    it,
                                                                    false,
                                                                    viewer = true
                                                                ).apply {
                                                                    Disposer.register(parent, this)
                                                                })
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            this.proportion = 0.5f;
                                            this.setHonorComponentsMinimumSize(true)
                                            this.dividerWidth = 7
                                        }, BorderLayout.CENTER)
                                    }
                                    val popup = JBPopupFactory.getInstance()
                                        .createComponentPopupBuilder(panel, jbList)
                                        .setMinSize(Dimension(600, 500))
                                        .setRequestFocus(true)
                                        .setFocusable(true)
                                        .setResizable(true)
                                        .setTitle("${tabObjData.psiMethod}: ${tabObjData.date}")
                                        .setTitleIcon(ActiveIcon(this.findIcon("icons/method.svg")))
                                        .setMovable(true)
                                        .setCancelOnClickOutside(true)
                                        .createPopup()
                                    popup.size = Dimension(600, 500)
                                    Disposer.register(popup, parent)
                                    val xOffset = 20 // X轴上的偏移（右侧）
                                    val yOffset = 20 // Y轴上的偏移（下方）
                                    val point: Point = e.point
                                    val adjustedPoint = Point(point.x + xOffset, point.y + yOffset) // 调整后的位置
                                    popup.show(RelativePoint(e.component, adjustedPoint))
                                }
                            }
                        }
                    }

                })
                this.init()
            }

            private fun addToInvoke() {
                if (jbList.selectedValue == null) {
                    Messages.showWarningDialog("请先选中需要添加到调用栈的函数或者方法", "警告")
                } else {
                    val objData = jbList.selectedValue!!
                    if (objData.type == "script") {
                        tabs.addTab(createScriptTab(objData.preScript))
                    } else {
                        this.readAction {
                            val psiClass = JavaPsiFacade.getInstance(project).findClass(
                                objData.psiClass,
                                GlobalSearchScope.allScope(project)
                            )

                            val oriPsiClass = JavaPsiFacade.getInstance(project).findClass(
                                objData.originPsiClass,
                                GlobalSearchScope.allScope(project)
                            )

                            if (oriPsiClass == null) {
                                Messages.showWarningDialog("类不存在,请检查你的类是否被删除", "警告")
                                return@readAction
                            }

                            if (psiClass == null) {
                                Messages.showWarningDialog("类不存在,请检查你的类是否被删除", "警告")
                            } else {
                                val psiMethod = psiClass.allMethods.firstOrNull {
                                    val methodParameter =
                                        it.parameterList.parameters.joinToString(",") { p -> p.name }
                                    val historyMethodParameter = objData.parameterNames.joinToString(",")
                                    methodParameter == historyMethodParameter && it.name == objData.psiMethod
                                }
                                if (psiMethod == null) {
                                    Messages.showWarningDialog("方法不存在,请检查你的方法在类中是否被删除", "警告")
                                } else {
                                    val tabObj = TabObj(
                                        psiClass.qualifiedName,
                                        objData.preScript,
                                        objData.postScript,
                                        psiMethod.getPsiParameters(psiClass),
                                        psiMethod.getPsiReturnType(psiClass),
                                        psiMethod.name,
                                        psiMethod.isStatic(),
                                        mutableMapOf<String, String>().apply { this.putAll(objData.parameters) },
                                        originPsiClass = oriPsiClass,

                                        ) {}
                                    tabs.addTab(createInvokeTab(tabObj))
                                }
                            }

                        }

                    }
                }
            }


            override fun createCenterPanel(): JComponent {
                return JBScrollPane(jbList)
            }

            override fun createActions(): Array<Action> {
                return arrayOf()
            }

            private fun removeSelect() {
                jbList.selectedValue?.let {
                    listModel.remove(jbList.selectedIndex)
                    CallThisMethodState.getInstance(project).history.remove(it)
                }
            }
        }
        dialog.show()
    }


    private fun createTitlePanel(title: String, component: JComponent): JComponent {
        return JPanel(BorderLayout()).apply {
            this.add(JLabel(title, JLabel.LEFT), BorderLayout.NORTH)
            this.add(component, BorderLayout.CENTER)
        }
    }


    private fun createScriptTab(scriptText: String): TabInfo {
        val jbSplitter = JBSplitter(true)
        val disposable = Disposer.newDisposable()
        val tabObj = TabObj(null, type = "script", preScript = scriptText) {
            Disposer.dispose(disposable)
        }
        val script = MultiLanguageTextField(GroovyFileType.GROOVY_FILE_TYPE, project, scriptText)
        script.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                tabObj.preScript = event.document.text
            }
        })
        val response = MultiLanguageTextField(Json5FileType.INSTANCE, project, "")
        tabObj.resp = response
        Disposer.register(disposable, response)
        Disposer.register(disposable, script)
        jbSplitter.firstComponent = script
        jbSplitter.secondComponent = response
        return TabInfo(jbSplitter).apply {
            val that = this
            this.`object` = tabObj
            this.text = "脚本"
            this.tooltipText = "脚本"
            this.icon = this.findIcon("icons/groovy.svg")
            val group = DefaultActionGroup()
            group.add(object : AnAction({ "关闭" }, AllIcons.Actions.Close) {
                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.presentation.icon = AllIcons.Actions.Close
                    e.presentation.hoveredIcon = AllIcons.Actions.CloseHovered
                }

                override fun actionPerformed(e: AnActionEvent) {
                    tabs.removeTab(that)
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })
            this.setTabLabelActions(group, "Tools@CallThisMethod@View@TabLabelActions")
            refreshSelect(tabObj)
        }
    }

    private fun reformat(resp: MultiLanguageTextField, block: () -> Unit = {}) {
        this.project.bgTask("响应结果格式化中...") {
            ApplicationManager.getApplication().invokeLater {
                val psiDocumentManager = PsiDocumentManager.getInstance(project)
                val psiFile = psiDocumentManager.getPsiFile(resp.document)
                psiFile?.apply {
                    //提交文件
                    psiDocumentManager.commitDocument(resp.document)
                    val codeProcessor = ReformatCodeProcessor(psiFile, false)
                    codeProcessor.setPostRunnable {
                        ApplicationManager.getApplication().invokeLater {
                            block()
                        }
                    }
                    codeProcessor.run()
                }
            }
        }
    }

    /**
     * 反射调用tab
     */
    private fun createInvokeTab(
        originTabObj: TabObj
    ): TabInfo {
        val jbSplitter = JBSplitter(true)
        val disposable = Disposer.newDisposable()
        val tabObj = TabObj(
            originTabObj.psiClass,
            originTabObj.preScript.groovyScript(),
            originTabObj.postScript.groovyScript(),
            originTabObj.psiParameters,
            originTabObj.psiResultType,
            originTabObj.psiMethodName,
            originTabObj.static,
            mutableMapOf<String, String>().apply {
                this.putAll(originTabObj.parameters)
            },
            type = originTabObj.type,
            originPsiClass = originTabObj.originPsiClass,
            invokeProxy = originTabObj.invokeProxy
        ) {
            Disposer.dispose(disposable)
        }
        val tabTabs = JBTabsFactory.createEditorTabs(project, disposable)
        //前置脚本
        val preScript = MultiLanguageTextField(GroovyFileType.GROOVY_FILE_TYPE, project, tabObj.preScript)
        val preScriptTypeAction = object : AbstractComboBoxAction<String>() {
            init {
                setItems(listOf("全局脚本", "项目脚本", "模块脚本", "函数脚本"), "函数脚本")
            }

            override fun update(item: String, presentation: Presentation, popup: Boolean) {
                presentation.text = item
                when (item) {
                    "全局脚本" -> presentation.icon = this.findIcon("icons/global.svg")
                    "项目脚本" -> presentation.icon = this.findIcon("icons/project.svg")
                    "模块脚本" -> presentation.icon = this.findIcon("icons/module.svg")
                    "函数脚本" -> presentation.icon = this.findIcon("icons/method.svg")
                }

            }

            override fun selectionChanged(item: String?): Boolean {
                return if (!StringUtils.equals(item, selection)) {
                    when (item) {
                        "全局脚本" -> {
                            preScript.text = CallThisMethodState.getInstance().preScript.groovyScript()
                        }

                        "项目脚本" -> {
                            preScript.text = CallThisMethodState.getInstance(project).preScript.groovyScript()
                        }

                        "模块脚本" -> {
                            val selection = moduleSelectAction.selection
                            if (StringUtils.isNotBlank(selection)) {
                                preScript.text =
                                    CallThisMethodState.getInstance(project).modulePreScript[selection.split(":")[0]]
                                        .groovyScript()
                            } else {
                                preScript.text = "".groovyScript()
                            }
                        }

                        "函数脚本" -> {
                            preScript.text = tabObj.preScript.groovyScript()
                        }
                    }
                    true
                } else {
                    false
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }
        }

        tabTabs.addTab(TabInfo(createEmptyBorderScrollPanel(preScript)).apply {
            this.icon = this.findIcon("icons/script.svg")
            this.text = "前置脚本"
            this.setTabPaneActions(DefaultActionGroup().apply {
                this.add(preScriptTypeAction)
                this.add(object : AnAction({ "保存" }, this.findIcon("icons/save.svg")) {
                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }

                    override fun actionPerformed(e: AnActionEvent) {
                        when (preScriptTypeAction.selection) {
                            "全局脚本" -> {
                                CallThisMethodState.getInstance().preScript = preScript.text
                            }

                            "项目脚本" -> {
                                CallThisMethodState.getInstance(project).preScript = preScript.text
                            }

                            "模块脚本" -> {
                                val selection = moduleSelectAction.selection
                                if (StringUtils.isNotBlank(selection)) {
                                    CallThisMethodState.getInstance(project).modulePreScript[selection.split(":")[0]] =
                                        preScript.text
                                } else {
                                    SwingUtilities.invokeLater {
                                        Messages.showInfoMessage("当前无可用模块,无法保存模块脚本", "警告")
                                    }
                                }
                            }

                            "函数脚本" -> {
                                tabObj.preScript = preScript.text
                            }
                        }
                        Notifications.Bus.notify(
                            Notification(
                                "",
                                "脚本保存通知",
                                "${preScriptTypeAction.selection}: 脚本保存成功",
                                NotificationType.INFORMATION
                            )
                        )
                    }
                })
            })
        })

        //后置脚本
        val postScript = MultiLanguageTextField(GroovyFileType.GROOVY_FILE_TYPE, project, tabObj.postScript)

        val postScriptTypeAction = object : AbstractComboBoxAction<String>() {
            init {
                setItems(listOf("全局脚本", "项目脚本", "模块脚本", "函数脚本"), "函数脚本")
            }

            override fun update(item: String, presentation: Presentation, popup: Boolean) {
                presentation.text = item
                when (item) {
                    "全局脚本" -> presentation.icon = this.findIcon("icons/global.svg")
                    "项目脚本" -> presentation.icon = this.findIcon("icons/project.svg")
                    "模块脚本" -> presentation.icon = this.findIcon("icons/module.svg")
                    "函数脚本" -> presentation.icon = this.findIcon("icons/method.svg")
                }

            }

            override fun selectionChanged(item: String?): Boolean {
                return if (!StringUtils.equals(item, selection)) {
                    when (item) {
                        "全局脚本" -> {
                            postScript.text = CallThisMethodState.getInstance().postScript.groovyScript()
                        }

                        "项目脚本" -> {
                            postScript.text = CallThisMethodState.getInstance(project).postScript.groovyScript()
                        }

                        "模块脚本" -> {
                            val selection = moduleSelectAction.selection
                            if (StringUtils.isNotBlank(selection)) {
                                postScript.text =
                                    CallThisMethodState.getInstance(project).modulePostScript[selection.split(":")[0]].groovyScript()
                            } else {
                                postScript.text = "".groovyScript()
                            }
                        }

                        "函数脚本" -> {
                            postScript.text = tabObj.postScript.groovyScript()
                        }
                    }
                    true
                } else {
                    false
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }
        }


        tabTabs.addTab(TabInfo(createEmptyBorderScrollPanel(postScript)).apply {
            this.icon = this.findIcon("icons/script.svg")
            this.text = "后置脚本"
            this.setTabPaneActions(DefaultActionGroup().apply {
                this.add(postScriptTypeAction)
                this.add(object : AnAction({ "保存" }, this.findIcon("icons/save.svg")) {
                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }

                    override fun actionPerformed(e: AnActionEvent) {
                        when (postScriptTypeAction.selection) {
                            "全局脚本" -> {
                                CallThisMethodState.getInstance().postScript = postScript.text
                            }

                            "项目脚本" -> {
                                CallThisMethodState.getInstance(project).postScript = postScript.text
                            }

                            "模块脚本" -> {
                                val selection = moduleSelectAction.selection
                                if (StringUtils.isNotBlank(selection)) {
                                    CallThisMethodState.getInstance(project).modulePostScript[selection.split(":")[0]] =
                                        postScript.text
                                } else {
                                    SwingUtilities.invokeLater {
                                        Messages.showInfoMessage("当前无可用模块,无法保存模块脚本", "警告")
                                    }
                                }
                            }

                            "函数脚本" -> {
                                tabObj.postScript = postScript.text
                            }
                        }
                        Notifications.Bus.notify(
                            Notification(
                                "",
                                "脚本保存通知",
                                "${preScriptTypeAction.selection}: 脚本保存成功",
                                NotificationType.INFORMATION
                            )
                        )
                    }
                })
            })
        })
        for (parameter in tabObj.psiParameters) {
            val parameterTextField =
                MultiLanguageTextField(
                    parameter.getMatchFileType(),
                    project,
                    tabObj.parameters[parameter.name] ?: ""
                )
            parameterTextField.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    tabObj.parameters[parameter.name] = event.document.text
                }
            })
            Disposer.register(disposable, parameterTextField)
            tabTabs.addTab(
                TabInfo(createEmptyBorderScrollPanel(parameterTextField)).apply {
                    val type = parameter.type
                    this.text = parameter.presentableText + " " + parameter.name
                    if (type == 0) {
                        this.icon = this.findIcon("icons/primitive.svg")
                    } else if (type == 3) {
                        this.icon = this.findIcon("icons/array.svg")
                    } else if(type == 2){
                        this.icon = this.findIcon("icons/object.svg")
                    }else {
                        this.icon = this.findIcon("icons/normal.svg")
                    }

                    this.setTabPaneActions(
                        createParameterActionGroup(
                            parameter,
                            tabObj.findMethod(project)!!,
                            parameterTextField
                        )
                    )
                })
        }
        tabObj.psiResultType?.let {
            if (!it.isVoid) {
                val response = MultiLanguageTextField(it.getMatchFileType(), project, "")
                tabObj.resp = response
                Disposer.register(disposable, response)
                jbSplitter.secondComponent = response
            }
        }

        Disposer.register(disposable, preScript)
        Disposer.register(disposable, postScript)
        jbSplitter.firstComponent = tabTabs.component

        jbSplitter.proportion = 0.5f;
        jbSplitter.setHonorComponentsMinimumSize(true)
        jbSplitter.dividerWidth = 7
        return TabInfo(jbSplitter).apply {
            val that = this
            this.`object` = tabObj
            this.text = tabObj.psiMethodName
            this.tooltipText = "${tabObj.psiClass}#${tabObj.psiMethodName}(${
                tabObj.psiParameters.joinToString(",") { it.presentableText + " " + it.name }
            })"
            if (tabObj.static) {
                this.icon = this.findIcon("icons/static_method.svg")
            } else {
                this.icon = this.findIcon("icons/method.svg")
            }
            val group = DefaultActionGroup()
            group.add(object : AnAction({ "关闭" }, AllIcons.Actions.Close) {
                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.presentation.icon = AllIcons.Actions.Close
                    e.presentation.hoveredIcon = AllIcons.Actions.CloseHovered
                }

                override fun actionPerformed(e: AnActionEvent) {
                    tabs.removeTab(that)
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.EDT
                }
            })
            this.setTabPaneActions(DefaultActionGroup().apply {

                this.add(object : ToggleAction({ "调用代理对象" }, this.findIcon("icons/proxy.svg")) {
                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }

                    override fun update(e: AnActionEvent) {
                        super.update(e)
                        if (tabObj.static) {
                            e.presentation.isVisible = false
                        }
                    }

                    override fun isSelected(e: AnActionEvent): Boolean = tabObj.invokeProxy

                    override fun setSelected(
                        e: AnActionEvent,
                        state: Boolean
                    ) {
                        if (tabObj.invokeProxy != state) {
                            tabObj.invokeProxy = state
                        }
                    }
                })

                this.add(object : AnAction({ "定位到方法" }, this.findIcon("icons/position.svg")) {
                    override fun actionPerformed(e: AnActionEvent) {
                        ApplicationManager.getApplication().invokeLater {
                            tabObj.originPsiClass?.let { psiClass ->
                                psiClass.containingFile?.let { psiFile ->
                                    tabObj.findMethod(project)?.let {
                                        val fileEditorManager = FileEditorManager.getInstance(project)
                                        val fileDescriptor = OpenFileDescriptor(
                                            project,
                                            psiFile.virtualFile,
                                            it.nameIdentifier?.textOffset ?: it.textOffset
                                        )
                                        fileEditorManager.openFileEditor(fileDescriptor, true)
                                    }
                                }
                            }
                        }
                    }

                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }
                })


            })
            this.setTabLabelActions(group, "Tools@CallThisMethod@View@TabLabelActions")
            refreshSelect(tabObj)
        }
    }

    private fun createParameterActionGroup(
        parameter: PsiParameterItem,
        psiMethod: PsiMethod,
        textField: MultiLanguageTextField,
    ): DefaultActionGroup {
        val defaultActionGroup = DefaultActionGroup()
        defaultActionGroup.add(object : AnAction({ "模拟参数" }, this.findIcon("icons/mock.svg")) {
            override fun actionPerformed(e: AnActionEvent) {
                val port = moduleSelectAction.selection.split(":")[1]
                val tags =
                    psiMethod.docComment?.findTagsByName("mock")?.let { it.map { i -> i.dataElements } }
                        ?.filter { it.size > 1 } ?: arrayListOf()
                val tagMaps = tags.toList().stream()
                    .collect(Collectors.toMap({ it[0].text.trim() }, { it[1].text.trim() }, { _, o2 -> o2 }))
                if (parameter.type == 1) {
                    textField.text = Api.getMockParameters(
                        port,
                        MockNormalParameterReq(tagMaps[parameter.name] ?: "", parameter.qualifiedName)
                    )
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }
        })
        return defaultActionGroup
    }

    private fun refreshSelect(tabObj: TabObj) {
        val selection = moduleSelectAction.selection
        val list: MutableList<String>
        if (tabObj.type == "method") {
            val psiMethod = tabObj.findMethod(project)
            if (psiMethod == null) {
                return
            } else {
                val modulePorts = psiMethod.findModulesAndPort()
                list = modulePorts.entries.flatMap { it.value.map { port -> it.key + ":" + port } }.toMutableList()
            }
        } else {
            list = ApplicationManager.getApplication().runReadAction<MutableList<String>> {
                ModuleManager.getInstance(project).modules.filter {
                    AppState.INSTANCE.contains(it.project.name, it.name) == true
                }.flatMap {
                    AppState.INSTANCE.getPorts(it.project.name, it.name)!!.map { port -> it.name + ":" + port }
                }.toMutableList()
            }
        }

        if (list.isNotEmpty()) {
            moduleSelectAction.setItems(
                list, if (list.contains(selection)) {
                    selection
                } else {
                    list[0]
                }
            )
        } else {
            moduleSelectAction.setItems(arrayListOf(), "")
            moduleSelectAction.update()
        }
    }

    override fun dispose() {
        tabs.removeAllTabs()
    }
}