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

import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.npw.template.WizardPluginPromotionTemplateProvider
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.wizard.model.ModelWizard.Facade
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateFlag
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.common.base.Suppliers
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.util.function.Supplier
import javax.swing.JComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.android.util.AndroidBundle.message

/**
 * First page in the New Project wizard that allows user to select the [FormFactor] (Mobile, Wear, TV, etc.) and its template ("Empty
 * Activity", "Basic", "Navigation Drawer", etc.)
 */
class ChooseAndroidProjectStep(model: NewProjectModel, private val initialTarget: String? = null) :
  ModelWizardStep<NewProjectModel>(model, message("android.wizard.project.new.choose")) {
  private val formFactors: Supplier<List<FormFactor>> = Suppliers.memoize { createFormFactors() }
  private val uiModel = ChooseAndroidProjectStepModel(formFactors, initialTarget)
  private val rootView = StudioComposePanel { ChooseAndroidProjectStepUI(model = uiModel) }
  private val canGoForward = BoolValueProperty()
  private var newProjectModuleModel: NewProjectModuleModel? = null
  private val coroutineScope = this.createCoroutineScope(Dispatchers.Main)

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    newProjectModuleModel = NewProjectModuleModel(model)
    val renderModel = newProjectModuleModel!!.extraRenderTemplateModel
    return listOf(
      ConfigureAndroidProjectStep(newProjectModuleModel!!, model),
      ConfigureTemplateParametersStep(renderModel, message("android.wizard.config.activity.title"), listOf()),
    )
  }

  override fun onWizardStarting(wizard: Facade) {
    coroutineScope.launch { uiModel.canGoForward.collect { canGoForward.set(it) } }

    coroutineScope.launch { uiModel.getAndroidProjectEntries() }

    uiModel.onGridItemDoubleClick = { wizard.goForward() }

    // Might not be needed
    FormScalingUtil.scaleComponentTree(this.javaClass, rootView)
  }

  override fun onProceeding() {
    val entry = uiModel.selectedAndroidProjectEntry ?: return
    entry.onProceeding(newProjectModuleModel!!, model)
  }

  override fun canGoForward(): ObservableBool = canGoForward

  override fun onShowing() {
    uiModel.selectedAndroidProjectEntry?.onShowing(model)
  }

  override fun getComponent(): JComponent = rootView

  override fun getPreferredFocusComponent(): JComponent = rootView

  companion object {
    fun FormFactor.getProjectTemplates(): List<GridItem> {
      val newProjectTemplates =
        if (includeNoActivity) {
          listOf(Template.NoActivity) + this.getNewProjectTemplates()
        } else {
          this.getNewProjectTemplates()
        }

      val pluginPromotionTemplates = this.getPluginPromotionTemplates()

      return newProjectTemplates.map { TemplateGridItem(it) } + pluginPromotionTemplates.map { PluginPromotionGridItem(it) }
    }

    /** Indicates which form factor in the Project Chooser this form factor should be grouped under. */
    private fun FormFactor.projectChooserCategory() =
      when (this) {
        FormFactor.AiGlasses -> FormFactor.XR
        else -> this
      }

    private fun FormFactor.getNewProjectTemplates() =
      TemplateResolver.getAllTemplates().filter {
        WizardUiContext.NewProject in it.uiContexts &&
          it.formFactor.projectChooserCategory() == this &&
          (!it.flags.contains(TemplateFlag.NewProjectAgent) || StudioFlags.NPW_SHOW_NPA_TEMPLATES.get())
      }

    private fun FormFactor.getPluginPromotionTemplates() =
      WizardPluginPromotionTemplateProvider.getAllPromotionTemplates()
        .filter { it.formFactor == this }
        .filterNot { PluginManagerCore.isPluginInstalled(PluginId.getId(it.pluginId)) }

    private fun createFormFactors(): List<FormFactor> = FormFactor.values().filterNot { it.getProjectTemplates().isEmpty() }
  }
}
