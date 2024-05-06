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
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.ui.SelectedListValueProperty
import com.android.tools.idea.wizard.model.ModelWizard.Facade
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.common.base.Suppliers
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ModalityUiUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.util.function.Supplier
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener

const val TABLE_CELL_WIDTH = 260
const val TABLE_CELL_HEIGHT = 32
const val TABLE_CELL_LEFT_PADDING = 20
const val TABLE_TITLE_PADDING = 20

/**
 * First page in the New Project wizard that allows user to select the [FormFactor] (Mobile, Wear, TV, etc.) and its
 * template ("Empty Activity", "Basic", "Navigation Drawer", etc.)
 */
class ChooseAndroidProjectStep(model: NewProjectModel) : ModelWizardStep<NewProjectModel>(
  model, message("android.wizard.project.new.choose")
) {
  private var loadingPanel = JBLoadingPanel(BorderLayout(), this)
  private val leftList = JBList<FormFactorInfo>()
  private val rightPanel = JPanel(BorderLayout())
  private val listEntriesListeners = ListenerManager()
  private val formFactors: Supplier<List<FormFactorInfo>> = Suppliers.memoize { createFormFactors(title) }
  private val canGoForward = BoolValueProperty()
  private var newProjectModuleModel: NewProjectModuleModel? = null

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    newProjectModuleModel = NewProjectModuleModel(model)
    val renderModel = newProjectModuleModel!!.extraRenderTemplateModel
    return listOf(
      ConfigureAndroidProjectStep(newProjectModuleModel!!, model),
      ConfigureTemplateParametersStep(renderModel, message("android.wizard.config.activity.title"), listOf())
    )
  }

  override fun onWizardStarting(wizard: Facade) {
    loadingPanel.startLoading()
    // Constructing FormFactors performs disk access and XML parsing, so let's do it in background thread.
    BackgroundTaskUtil.executeOnPooledThread(this, {
      val formFactors = formFactors.get()

      // Update UI with the loaded formFactors. Switch back to UI thread.
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any()) { updateUi(wizard, formFactors) }
    })
  }

  /**
   * Updates UI with a given form factors. This method must be executed on event dispatch thread.
   */
  private fun updateUi(wizard: Facade, formFactors: List<FormFactorInfo>) {
    ThreadingAssertions.assertEventDispatchThread()

    formFactors.forEach {
      with(it.tabPanel) {
        myGallery.setDefaultAction(object : AbstractAction() {
          override fun actionPerformed(actionEvent: ActionEvent?) {
            wizard.goForward()
          }
        })
        val activitySelectedListener = ListSelectionListener {
          myGallery.selectedElement?.let { renderer ->
            myTemplateName.isVisible = false
            myTemplateDesc.parent.isVisible = false // Hides both myTemplateDesc/myDocumentationLink and removes panel padding

            canGoForward.set(true)
          } ?: canGoForward.set(false)
        }
        myGallery.addListSelectionListener(activitySelectedListener)
        activitySelectedListener.valueChanged(null)
      }
    }

    leftList.setCellRenderer { _, value, _, isSelected, cellHasFocus ->
      JBLabel(value.formFactor.toString()).apply {
        isOpaque = true
        background = UIUtil.getListBackground(isSelected, cellHasFocus)
        foreground = UIUtil.getListForeground(isSelected, cellHasFocus)
        border = JBUI.Borders.emptyLeft(TABLE_CELL_LEFT_PADDING)

        val size = JBUI.size(TABLE_CELL_WIDTH, TABLE_CELL_HEIGHT)
        preferredSize = size
      }
    }
    AccessibleContextUtil.setName(leftList, message("android.wizard.project.new.choose"))
    leftList.selectionMode = ListSelectionModel.SINGLE_SELECTION
    leftList.setListData(formFactors.toTypedArray())
    leftList.selectedIndex = 0
    listEntriesListeners.listenAndFire(SelectedListValueProperty(leftList)) { formFactorInfo ->
      rightPanel.removeAll()
      rightPanel.add(formFactorInfo.get().tabPanel.myRootPanel, BorderLayout.CENTER)
      rightPanel.revalidate()
      rightPanel.repaint()
    }

    val leftPanel = JPanel(BorderLayout()).apply {
      add(createTitle(), BorderLayout.NORTH)
      add(leftList, BorderLayout.CENTER)
    }

    val mainPanel = JPanel(BorderLayout()).apply {
      add(leftPanel, BorderLayout.WEST)
      add(rightPanel, BorderLayout.CENTER)
    }

    loadingPanel.add(mainPanel)

    FormScalingUtil.scaleComponentTree(this.javaClass, loadingPanel)
    loadingPanel.apply {
      revalidate() // We may have called add(component) after being displayed
      stopLoading()
    }
  }

  override fun onProceeding() {
    val selectedIndex = leftList.selectedIndex
    val selectedFormFactorInfo = formFactors.get()[selectedIndex]
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

  override fun getPreferredFocusComponent(): JComponent = loadingPanel

  override fun dispose() {
    listEntriesListeners.releaseAll()
  }

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
    fun createTitle(): JBLabel {
      return JBLabel("Templates").apply {
        isOpaque = true
        background = UIUtil.getListBackground()
        foreground = JBColor(0x999999, 0x787878)
        border = JBUI.Borders.empty(TABLE_TITLE_PADDING, TABLE_CELL_LEFT_PADDING, TABLE_TITLE_PADDING, TABLE_TITLE_PADDING)
      }
    }

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
