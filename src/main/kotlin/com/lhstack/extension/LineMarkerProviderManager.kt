package com.lhstack.extension

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.messages.Topic
import javax.swing.SwingUtilities

interface MarkClickListener {
    companion object {
        val TOPIC = Topic.create("Tools@CallThisMethod@MarkClickListener", MarkClickListener::class.java)
    }

    fun onMessage(psiMethod: PsiMethod, psiClass: PsiClass?, originPsiClass: PsiClass?)
}


class LineMarkerProviderManager {

    companion object {

        private val javaLineMarkerProvider = LineMarkerProvider { it ->
            if (it is PsiIdentifier && it.parent is PsiMethod) {
                val psiMethod = it.parent as PsiMethod
                if (!psiMethod.needMark()) {
                    null
                } else {
                    LineMarkerInfo(it, it.textRange, this.findIcon("icons/call.svg")!!, {
                        "call this method"
                    }, { e, _ ->
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            ApplicationManager.getApplication().invokeLater {
                                val psiClass = psiMethod.containingClass!!
                                val project = psiMethod.project
                                if (psiClass.hasAnno("org.springframework.stereotype.Component") && !psiClass.isInterface && !PsiUtil.isAbstractClass(psiClass)) {
                                    project.openThisPage()
                                    project.syncPublish(MarkClickListener.TOPIC) {
                                        it.onMessage(psiMethod, psiClass, psiClass)
                                    }
                                } else if(psiMethod.isStatic()){
                                    project.openThisPage()
                                    project.syncPublish(MarkClickListener.TOPIC) {
                                        it.onMessage(psiMethod, psiClass, psiClass)
                                    }
                                }
                                else {
                                    val classes = ClassInheritorsSearch.search(
                                        psiClass,
                                        GlobalSearchScope.allScope(psiMethod.project),
                                        true
                                    )
                                        .filter { !PsiUtil.isAbstractClass(it) && !it.isInterface && it.hasAnno("org.springframework.stereotype.Component") }
                                        .toList()
                                    if (classes.isNotEmpty()) {
                                        val listPopupStep = object : BaseListPopupStep<PsiClass>("实现类", classes) {
                                            override fun onChosen(
                                                selectedValue: PsiClass,
                                                finalChoice: Boolean,
                                            ): PopupStep<*>? {
                                                val psiClass = selectedValue
                                                var psiMethod = psiMethod
                                                project.openThisPage()
                                                project.syncPublish(MarkClickListener.TOPIC) {
                                                    it.onMessage(psiMethod, psiClass, psiMethod.containingClass)
                                                }
                                                return super.onChosen(selectedValue, finalChoice)
                                            }

                                            override fun getTextFor(value: PsiClass) = value.qualifiedName!!

                                        }
                                        val popup = JBPopupFactory.getInstance().createListPopup(listPopupStep, 10)
                                        popup.show(RelativePoint(e.component, e.point))
                                    }
                                }
                            }
                        }
                    }, GutterIconRenderer.Alignment.CENTER) {
                        "call this method"
                    }
                }
            } else {
                null
            }
        }

        fun registry() {
            LineMarkerProviders.getInstance().addExplicitExtension(JavaLanguage.INSTANCE, javaLineMarkerProvider)
        }

        fun unRegistry() {
            LineMarkerProviders.getInstance().removeExplicitExtension(JavaLanguage.INSTANCE, javaLineMarkerProvider)
        }
    }
}