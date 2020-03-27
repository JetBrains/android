/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.project

import com.android.tools.adtui.ASGallery
import com.android.tools.adtui.stdui.CommonTabbedPane
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.idea.device.FormFactor
import com.android.tools.idea.npw.cpp.ConfigureCppSupportStep
import com.android.tools.idea.npw.model.EMPTY_ACTIVITY
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.template.ChooseGalleryItemStep
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.npw.template.getDefaultSelectedTemplateIndex
import com.android.tools.idea.npw.toTemplateFormFactor
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.npw.ui.cppIcon
import com.android.tools.idea.npw.ui.getTemplateIcon
import com.android.tools.idea.npw.ui.getTemplateTitle
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.wizard.model.ModelWizard.Facade
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.common.base.Suppliers
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.ui.GuiUtils
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER
import com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH
import com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW
import com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK
import com.intellij.uiDesigner.core.GridLayoutManager
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.util.function.Supplier
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ListSelectionListener

/**
 * First page in the New Project wizard that allows user to select the [FormFactor] (Mobile, Wear, TV, etc.) and its
 * template ("Empty Activity", "Basic", "Navigation Drawer", etc.)
 */
class ChooseAndroidProjectStep(model: NewProjectModel) : ModelWizardStep<NewProjectModel>(
  model, message("android.wizard.project.new.choose")
) {
  private var loadingPanel = JBLoadingPanel(BorderLayout(), this)
  private val tabsPanel = CommonTabbedPane()
  private val rootPanel = JPanel(GridLayoutManager(1, 1))
  private val formFactors: Supplier<List<FormFactorInfo>>? = Suppliers.memoize { createFormFactors(title) }
  private val canGoForward = BoolValueProperty()
  private var newProjectModuleModel: NewProjectModuleModel? = null
  private val selectedFormFactorInfo: FormFactorInfo get() = formFactors!!.get()[tabsPanel.selectedIndex]

  init {
    loadingPanel.add(tabsPanel)

    val d = Dimension(-1, -1)
    val sp = SIZEPOLICY_CAN_GROW or SIZEPOLICY_CAN_SHRINK
    val gc = GridConstraints(0, 0, 1, 1, ANCHOR_CENTER, FILL_BOTH, sp, sp, d, d, d, 0, false)
    rootPanel.add(loadingPanel, gc)
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    newProjectModuleModel = NewProjectModuleModel(model)
    val renderModel = newProjectModuleModel!!.extraRenderTemplateModel
    return listOf(
      ConfigureAndroidProjectStep(newProjectModuleModel!!, model),
      ConfigureCppSupportStep(model),
      ConfigureTemplateParametersStep(renderModel, message("android.wizard.config.activity.title"), listOf()))
  }

  private fun createUIComponents() {
    loadingPanel = object : JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        super.setBounds(x, y, width, height)

        // Work-around for IDEA-205343 issue.
        components.forEach {
          it!!.setBounds(x, y, width, height)
        }
      }
    }
    loadingPanel.setLoadingText("Loading Android project template files")
  }

  override fun onWizardStarting(wizard: Facade) {
    loadingPanel.startLoading()
    // Constructing FormFactors performs disk access and XML parsing, so let's do it in background thread.
    BackgroundTaskUtil.executeOnPooledThread(this, Runnable {
      val formFactors = formFactors!!.get()

      // Update UI with the loaded formFactors. Switch back to UI thread.
      GuiUtils.invokeLaterIfNeeded(
        { updateUi(wizard, formFactors) },
        ModalityState.any())
    })
  }

  /**
   * Updates UI with a given form factors. This method must be executed on event dispatch thread.
   */
  private fun updateUi(wizard: Facade, formFactors: List<FormFactorInfo>) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    formFactors.forEach {
      with(it.tabPanel) {
        tabsPanel.addTab(it.formFactor.toString(), myRootPanel)
        myGallery.setDefaultAction(object : AbstractAction() {
          override fun actionPerformed(actionEvent: ActionEvent?) {
            wizard.goForward()
          }
        })
        val activitySelectedListener = ListSelectionListener {
          myGallery.selectedElement?.let { renderer ->
            myTemplateName.text = renderer.label
            myTemplateDesc.text = "<html>" + renderer.description + "</html>"
            myDocumentationLink.isVisible = renderer is CppTemplateRendererWithDescription
            canGoForward.set(true)
          } ?: canGoForward.set(false)
        }
        myGallery.addListSelectionListener(activitySelectedListener)
        activitySelectedListener.valueChanged(null)
      }
    }

    FormScalingUtil.scaleComponentTree(this.javaClass, rootPanel)
    loadingPanel.stopLoading()
  }

  override fun onProceeding() {
    val selectedTemplate =  selectedFormFactorInfo.tabPanel.myGallery.selectedElement!!
    model.enableCppSupport.set(selectedTemplate is CppTemplateRendererWithDescription)
    with(newProjectModuleModel!!) {
      formFactor.set(selectedFormFactorInfo.formFactor)
      when (selectedTemplate) {
        is NewTemplateRendererWithDescription -> {
          newRenderTemplate.setNullableValue(selectedTemplate.template)
          if (selectedFormFactorInfo.formFactor == FormFactor.THINGS) {
            newProjectModuleModel!!.extraRenderTemplateModel.newTemplate = selectedTemplate.template
          }
        }
        is CppTemplateRendererWithDescription -> {
          newRenderTemplate.value = TemplateResolver.getAllTemplates().first { it.name == EMPTY_ACTIVITY }.apply {
            val p = parameters.find { it.name == "C++ support" } as BooleanParameter
            p.value = true
          }
        }
        else -> throw IllegalArgumentException("Add support for additional template renderer")
      }
    }
  }

  override fun canGoForward(): ObservableBool = canGoForward

  override fun getComponent(): JComponent = rootPanel

  override fun getPreferredFocusComponent(): JComponent = tabsPanel

  interface FormFactorInfo {
    val formFactor: FormFactor
    val tabPanel: ChooseAndroidProjectPanel<TemplateRendererWithDescription>
  }

  private class NewFormFactorInfo(
    override val formFactor: FormFactor,
    override val tabPanel: ChooseAndroidProjectPanel<TemplateRendererWithDescription>
  ): FormFactorInfo

  interface TemplateRendererWithDescription : ChooseGalleryItemStep.TemplateRenderer {
    val description: String
  }

  data class CppTemplateRendererWithDescription(
    override val description: String = message("android.wizard.gallery.item.add.cpp.Desc"),
    override val label: String = message("android.wizard.gallery.item.add.cpp"),
    override val icon: Icon? = cppIcon,
    override val exists: Boolean = true
  ) : TemplateRendererWithDescription {
    override fun toString() = label
  }

  private class NewTemplateRendererWithDescription(
    template: Template
  ) : TemplateRendererWithDescription, ChooseGalleryItemStep.NewTemplateRenderer(template) {
    override val label: String get() = getTemplateTitle(template)
    override val icon: Icon? get() = getTemplateIcon(template)
    override val description: String get() = template.description
  }

  companion object {
    private fun createFormFactors(wizardTitle: String): List<FormFactorInfo> =
        FormFactor.values().map { NewFormFactorInfo(it, ChooseAndroidProjectPanel(createGallery(wizardTitle, it))) }

    private fun createGallery(title: String, formFactor: FormFactor): ASGallery<TemplateRendererWithDescription> {
      val listItems = sequence {
        yield(NewTemplateRendererWithDescription(Template.NoActivity))

        TemplateResolver.getAllTemplates()
            .filter { WizardUiContext.NewProject in it.uiContexts && it.formFactor == formFactor.toTemplateFormFactor()}
            .forEach { yield(NewTemplateRendererWithDescription(it)) }

        if (formFactor === FormFactor.MOBILE) {
          yield(CppTemplateRendererWithDescription())
        }
      }.toList()

      return WizardGallery<TemplateRendererWithDescription>(title, { it!!.icon }, { it!!.label }).apply {
        model = JBList.createDefaultListModel(listItems)
        selectedIndex = getDefaultSelectedTemplateIndex(listItems)
      }
    }
  }
}

