package com.lhstack.view

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.lhstack.extension.findIcon
import com.lhstack.state.CallThisMethodState
import org.apache.commons.lang3.time.DateUtils
import org.jdesktop.swingx.VerticalLayout
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class SettingDialog(var project: Project) : DialogWrapper(project, false) {
    var state = CallThisMethodState.getInstance(project)
    init {
        this.title = "设置"
        this.setSize(800, 600)
        this.init()
    }

    override fun createActions(): Array<out Action?> {
        return arrayOf()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel().apply {
            this.layout = VerticalLayout()
            //历史设置
            this.add(JPanel(BorderLayout()).apply {
                this.add(JLabel("历史设置", JLabel.LEFT), BorderLayout.NORTH)
                this.add(JPanel().apply {
                    this.layout = VerticalLayout()
                    val historyNumLabel = JLabel("${state.history.size}", JLabel.LEFT)
                    this.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        this.add(JLabel("保留数量: ", JLabel.LEFT).apply {
                            this.toolTipText = "当数据超过此参数,则会删除最先添加的历史数据,使其已经存储的历史条数稳定保持在小于或等于此参数"
                        })
                        var model = SpinnerNumberModel(state.historyTotal, 0, 1000, 1); // 初始值 0，最小值 0，最大值 100，步长 1

                        var jSpinner = JSpinner(model).apply {
                            val that = this
                            this.editor.let {
                                if(it is JSpinner.NumberEditor){
                                    it.textField?.addKeyListener(object :KeyAdapter(){
                                        override fun keyPressed(e: KeyEvent) {
                                           that.commitEdit()
                                        }
                                    })
                                }
                            }
                            this.addChangeListener(object : ChangeListener {
                                override fun stateChanged(e: ChangeEvent) {
                                    val value = model.value as Int
                                    state.historyTotal = value
                                    if (state.history.size > value) {
                                        val step = state.history.size - value
                                        state.history = ArrayList(state.history.subList(step, state.history.size))
                                        historyNumLabel.text = "${state.history.size}"
                                        historyNumLabel.revalidate()
                                        historyNumLabel.updateUI()
                                    }
                                    Notifications.Bus.notify(Notification("","通知","更新历史保留数量成功,当前历史保留数量: ${state.historyTotal}",NotificationType.INFORMATION),project)
                                }
                            })
                        }
                        this.add(jSpinner)
                    })

                    this.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        this.add(JLabel("当前数量: ", JLabel.LEFT).apply {
                            this.toolTipText = "当前已经存储的历史数据条数"
                        })
                        this.add(historyNumLabel)
                    })

                    this.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        this.add(JLabel("删除数据: "))
                        var model = SpinnerDateModel(DateUtils.addDays(Date(),-7), null, null, Calendar.DAY_OF_MONTH)
                        var jSpinner = JSpinner(model).apply {
                            this.editor = JSpinner.DateEditor(this, "yyyy-MM-dd")
                        }
                        this.add(jSpinner)
                        val clearBtn = object:AnAction({"清除指定时间之前缓存的历史数据"},this.findIcon("icons/clear.svg")){
                            override fun actionPerformed(e: AnActionEvent) {
                                jSpinner.commitEdit()
                                var format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                val removeList = state.history.filter {
                                    var date = format.parse(it.date)
                                    date == null || date.before(model.date)
                                }.toMutableList()
                                if(removeList.isNotEmpty()){
                                    state.history.removeAll(removeList)
                                    historyNumLabel.text = "${state.history.size}"
                                    historyNumLabel.revalidate()
                                    historyNumLabel.updateUI()
                                    format = SimpleDateFormat("yyyy-MM-dd")
                                    Notifications.Bus.notify(Notification("","通知","清除${format.format(model.date)}之前的历史数据成功",NotificationType.INFORMATION),project)
                                }
                            }
                        }
                        var presentation = PresentationFactory().getPresentation(clearBtn)
                        this.add(ActionButton(clearBtn,presentation,"CallThisMethod@ClearData", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE))
                    })
                }, BorderLayout.CENTER)
            })
            this.add(JSeparator(JSeparator.HORIZONTAL))

            this.add(JPanel(BorderLayout()).apply {
                this.add(JLabel("方法缓存"), BorderLayout.NORTH)

                var methodCacheNumber = JLabel("${state.methodCache.size}", JLabel.CENTER)
                this.add(JPanel(VerticalLayout()).apply {
                    this.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        this.add(JLabel("已缓存数: ", JLabel.CENTER).apply {
                            this.toolTipText = "当前已经缓存的方法数量"
                        })
                        this.add(methodCacheNumber)
                    })

                    this.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                        this.add(JLabel("清除缓存: "))
                        val model = SpinnerDateModel(DateUtils.addDays(Date(),-7), null, null, Calendar.DAY_OF_MONTH)
                        var jSpinner = JSpinner(model).apply {
                            this.editor = JSpinner.DateEditor(this, "yyyy-MM-dd")
                        }
                        this.add(jSpinner)
                        val clearBtn = object:AnAction({"清除指定时间之前缓存的方法数量"},this.findIcon("icons/clear.svg")){
                            override fun actionPerformed(e: AnActionEvent) {
                                jSpinner.commitEdit()
                                var format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                val removeKeys = hashSetOf<String>()
                                for (entry in state.methodCache.entries) {
                                    var date = format.parse(entry.value.date)
                                    if(date == null){
                                        removeKeys.add(entry.key)
                                    }else if(date.before(model.date)) {
                                        removeKeys.add(entry.key)
                                    }
                                }
                                if(removeKeys.isNotEmpty()){
                                    removeKeys.forEach { key -> state.methodCache.remove(key) }
                                    methodCacheNumber.text = "${state.methodCache.size}"
                                    methodCacheNumber.revalidate()
                                    methodCacheNumber.updateUI()
                                    format = SimpleDateFormat("yyyy-MM-dd")
                                    Notifications.Bus.notify(Notification("","通知","清除${format.format(model.date)}之前的方法缓存成功",NotificationType.INFORMATION),project)
                                }
                            }
                        }
                        var presentation = PresentationFactory().getPresentation(clearBtn)
                        this.add(ActionButton(clearBtn,presentation,"CallThisMethod@ClearMethodCacheData", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE))
                    })
                })
            })
        }
    }
}