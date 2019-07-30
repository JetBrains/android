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
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.FormFactor.Companion.get
import com.android.tools.idea.npw.cpp.ConfigureCppSupportStep
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateDescription
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateIcon
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateImageLabel
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.wizard.model.ModelWizard.Facade
import com.android.tools.idea.wizard.model.ModelWizardStep
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
import java.io.File
import java.util.function.Supplier
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.ListSelectionListener

/**
 * First page in the New Project wizard that allows user to select the Form Factor (Mobile, Wear, TV, etc) and its
 * Template ("Empty Activity", "Basic", "Nav Drawer", etc)
 */
class ChooseAndroidProjectStep(model: NewProjectModel) : ModelWizardStep<NewProjectModel>(model, message("android.wizard.project.new.choose")) {
  private var loadingPanel = JBLoadingPanel(BorderLayout(), this)
  private val tabsPanel = CommonTabbedPane()
  private val rootPanel = JPanel(GridLayoutManager(1, 1))
  private val formFactors: Supplier<List<FormFactorInfo>>? = Suppliers.memoize { createFormFactors(title) }
  private val canGoForward = BoolValueProperty()
  private var newProjectModuleModel: NewProjectModuleModel? = null

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
        {
          updateUi(wizard, formFactors)
        },
        ModalityState.any())
    })
  }

  /**
   * Updates UI with a given form factors. This method must be executed on event dispatch thread.
   */
  private fun updateUi(wizard: Facade, formFactors: List<FormFactorInfo>) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    for (formFactorInfo in formFactors) {
      val tabPanel = formFactorInfo.tabPanel
      tabsPanel.addTab(formFactorInfo.formFactor.toString(), tabPanel.myRootPanel)
      tabPanel.myGallery.setDefaultAction(object : AbstractAction() {
        override fun actionPerformed(actionEvent: ActionEvent?) {
          wizard.goForward()
        }
      })
      val activitySelectedListener = ListSelectionListener {
        val selectedTemplate = tabPanel.myGallery.selectedElement
        if (selectedTemplate != null) {
          tabPanel.myTemplateName.text = selectedTemplate.imageLabel
          tabPanel.myTemplateDesc.text = "<html>" + selectedTemplate.templateDescription + "</html>"
          tabPanel.myDocumentationLink.isVisible = selectedTemplate.isCppTemplate
        }
        canGoForward.set(selectedTemplate != null)
      }
      tabPanel.myGallery.addListSelectionListener(activitySelectedListener)
      activitySelectedListener.valueChanged(null)
    }
    FormScalingUtil.scaleComponentTree(this.javaClass, rootPanel)
    loadingPanel.stopLoading()
  }

  override fun onProceeding() {
    val formFactorInfo = formFactors!!.get()[tabsPanel.selectedIndex]
    val selectedTemplate = formFactorInfo.tabPanel.myGallery.selectedElement!!
    model.enableCppSupport.set(selectedTemplate.isCppTemplate)
    newProjectModuleModel!!.formFactor.set(formFactorInfo.formFactor)
    newProjectModuleModel!!.moduleTemplateFile().setNullableValue(formFactorInfo.templateFile)
    newProjectModuleModel!!.renderTemplateHandle.setNullableValue(selectedTemplate.template)
    val extraStepTemplateHandle = if (formFactorInfo.formFactor === FormFactor.THINGS) selectedTemplate.template else null
    newProjectModuleModel!!.extraRenderTemplateModel.templateHandle = extraStepTemplateHandle
  }

  override fun canGoForward(): ObservableBool = canGoForward

  override fun getComponent(): JComponent = rootPanel

  override fun getPreferredFocusComponent(): JComponent = tabsPanel

  private class FormFactorInfo internal constructor(
    internal var templateFile: File,
    internal val formFactor: FormFactor,
    internal var minSdk: Int,
    internal val tabPanel: ChooseAndroidProjectPanel<TemplateRenderer>)

  private class TemplateRenderer(internal val template: TemplateHandle?, internal val isCppTemplate: Boolean) {
    internal val imageLabel: String get() = getTemplateImageLabel(template, isCppTemplate)
    internal val templateDescription: String get() = getTemplateDescription(template, isCppTemplate)
    override fun toString(): String = imageLabel
    /**
     * Return the image associated with the current template, if it specifies one, or null otherwise.
     */
    internal val icon: Icon? get() = getTemplateIcon(template, isCppTemplate)
  }

  companion object {
    // To have the sequence specified by design, we hardcode the sequence.
    private val ORDERED_ACTIVITY_NAMES = arrayOf(
      "Basic Activity", "Empty Activity", "Bottom Navigation Activity", "Fragment + ViewModel", "Fullscreen Activity", "Master/Detail Flow",
      "Navigation Drawer Activity", "Google Maps Activity", "Login Activity", "Scrolling Activity", "Tabbed Activity"
    )

    private fun createFormFactors(wizardTitle: String): List<FormFactorInfo> {
      val formFactorInfoMap = mutableMapOf<FormFactor, FormFactorInfo>()
      val manager = TemplateManager.getInstance()
      val applicationTemplates: List<File> = manager!!.getTemplatesInCategory(CATEGORY_APPLICATION)
      for (templateFile in applicationTemplates) {
        val metadata = manager.getTemplateMetadata(templateFile)
        metadata?.formFactor ?: continue
        val formFactor = get(metadata.formFactor!!)
        val prevFormFactorInfo = formFactorInfoMap[formFactor]
        val templateMinSdk = metadata.minSdk
        if (prevFormFactorInfo == null) {
          // TODO(qumeric) validate templates with minimum api instead
          val minSdk = templateMinSdk.coerceAtLeast(formFactor.minOfflineApiLevel)
          val tabPanel = ChooseAndroidProjectPanel(createGallery(wizardTitle, formFactor))
          formFactorInfoMap[formFactor] = FormFactorInfo(templateFile, formFactor, minSdk, tabPanel)
        }
        else if (templateMinSdk > prevFormFactorInfo.minSdk) {
          prevFormFactorInfo.minSdk = templateMinSdk
          prevFormFactorInfo.templateFile = templateFile
        }
      }
      return formFactorInfoMap.values.sortedBy(FormFactorInfo::formFactor)
    }

    private fun getFilteredTemplateHandles(formFactor: FormFactor): List<TemplateHandle?> {
      val templateHandles = TemplateManager.getInstance().getTemplateList(formFactor)
      if (formFactor === FormFactor.MOBILE) {
        val entryMap = templateHandles.associateBy { it!!.metadata.title!! }
        return ORDERED_ACTIVITY_NAMES.mapNotNull { entryMap[it] }
      }
      return templateHandles
    }

    private fun createGallery(title: String, formFactor: FormFactor): ASGallery<TemplateRenderer> {
      val templateHandles = getFilteredTemplateHandles(formFactor)
      val templateRenderers = mutableListOf<TemplateRenderer>()
      templateRenderers.add(TemplateRenderer(null, false)) // "No Activity" entry

      templateHandles.forEach {
        templateRenderers.add(TemplateRenderer(it, false))
      }
      if (formFactor === FormFactor.MOBILE) {
        templateRenderers.add(TemplateRenderer(null, true)) // "Native C++" entry
      }
      val listItems = templateRenderers.toTypedArray()
      return WizardGallery<TemplateRenderer>(title, { it!!.icon }, { it!!.imageLabel }).apply {
        model = JBList.createDefaultListModel<Any?>(*listItems as Array<Any>)
        selectedIndex = getDefaultSelectedTemplateIndex(listItems)
      }
    }

    private fun getDefaultSelectedTemplateIndex(templateRenderers: Array<TemplateRenderer>): Int = templateRenderers.indices.run {
      val defaultTemplateIndex = firstOrNull { templateRenderers[it].imageLabel == "Empty Activity" }
      val firstValidTemplateIndex = firstOrNull { templateRenderers[it].template != null }

      defaultTemplateIndex ?: firstValidTemplateIndex ?: throw IllegalArgumentException("No valid Template found")
    }
  }
}