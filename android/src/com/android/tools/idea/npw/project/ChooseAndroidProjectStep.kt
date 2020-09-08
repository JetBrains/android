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
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.template.ChooseGalleryItemStep
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.npw.template.getDefaultSelectedTemplateIndex
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.npw.ui.getTemplateIcon
import com.android.tools.idea.npw.ui.getTemplateTitle
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.wizard.model.ModelWizard.Facade
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.template.FormFactor
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
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.util.function.Supplier
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent
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
  private val formFactors: Supplier<List<FormFactorInfo>> = Suppliers.memoize { createFormFactors(title) }
  private val canGoForward = BoolValueProperty()
  private var newProjectModuleModel: NewProjectModuleModel? = null

  init {
    loadingPanel.add(tabsPanel)
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    newProjectModuleModel = NewProjectModuleModel(model)
    val renderModel = newProjectModuleModel!!.extraRenderTemplateModel
    return listOf(
      ConfigureAndroidProjectStep(newProjectModuleModel!!, model),
      ConfigureTemplateParametersStep(renderModel, message("android.wizard.config.activity.title"), listOf()))
  }

  private fun createUIComponents() {
    loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
    loadingPanel.setLoadingText("Loading Android project template files")
  }

  override fun onWizardStarting(wizard: Facade) {
    loadingPanel.startLoading()
    // Constructing FormFactors performs disk access and XML parsing, so let's do it in background thread.
    BackgroundTaskUtil.executeOnPooledThread(this, Runnable {
      val formFactors = formFactors.get()

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
            myDocumentationLink.isVisible = renderer.documentationUrl != null
            myDocumentationLink.setHyperlinkTarget(renderer.documentationUrl)

            canGoForward.set(true)
          } ?: canGoForward.set(false)
        }
        myGallery.addListSelectionListener(activitySelectedListener)
        activitySelectedListener.valueChanged(null)
      }
    }

    FormScalingUtil.scaleComponentTree(this.javaClass, loadingPanel)
    loadingPanel.stopLoading()
  }

  override fun onProceeding() {
    val selectedFormFactorInfo = formFactors.get()[tabsPanel.selectedIndex]
    val selectedTemplate =  selectedFormFactorInfo.tabPanel.myGallery.selectedElement!!
    with(newProjectModuleModel!!) {
      formFactor.set(selectedFormFactorInfo.formFactor)
      when (selectedTemplate) {
        is NewTemplateRendererWithDescription -> {
          newRenderTemplate.setNullableValue(selectedTemplate.template)
          val hasExtraDetailStep = selectedTemplate.template.uiContexts.contains(WizardUiContext.NewProjectExtraDetail)
          newProjectModuleModel!!.extraRenderTemplateModel.newTemplate =
            if (hasExtraDetailStep) selectedTemplate.template else Template.NoActivity
        }
        else -> throw IllegalArgumentException("Add support for additional template renderer")
      }
    }
  }

  override fun canGoForward(): ObservableBool = canGoForward

  override fun getComponent(): JComponent = loadingPanel

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
    val documentationUrl: String?
  }

  private class NewTemplateRendererWithDescription(
    template: Template
  ) : TemplateRendererWithDescription, ChooseGalleryItemStep.NewTemplateRenderer(template) {
    override val label: String get() = getTemplateTitle(template)
    override val icon: Icon? get() = getTemplateIcon(template)
    override val description: String get() = template.description
    override val documentationUrl: String? = template.documentationUrl
  }

  companion object {
    private fun FormFactor.getProjectTemplates() = TemplateResolver.getAllTemplates()
        .filter { WizardUiContext.NewProject in it.uiContexts && it.formFactor == this }

    private fun createFormFactors(wizardTitle: String): List<FormFactorInfo> = FormFactor.values()
        .filterNot { it.getProjectTemplates().isEmpty() }
        .map { NewFormFactorInfo(it, ChooseAndroidProjectPanel(createGallery(wizardTitle, it))) }

    private fun createGallery(title: String, formFactor: FormFactor): ASGallery<TemplateRendererWithDescription> {
      val listItems = sequence {
        yield(NewTemplateRendererWithDescription(Template.NoActivity))
        formFactor.getProjectTemplates().forEach { yield(NewTemplateRendererWithDescription(it)) }
      }.toList()

      return WizardGallery<TemplateRendererWithDescription>(title, { it!!.icon }, { it!!.label }).apply {
        model = JBList.createDefaultListModel(listItems)
        selectedIndex = getDefaultSelectedTemplateIndex(listItems)
      }
    }
  }
}
