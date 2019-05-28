/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.tools.idea.npw.assetstudio.wizard.GenerateImageAssetPanel
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.StringEvaluator
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.android.tools.idea.wizard.model.ModelWizardStep
import org.jetbrains.android.facet.AndroidFacet

import javax.swing.*

/**
 * Step for supporting a template.xml's `<icon>` tag if one exists (it tells the template to generate icons in addition to regular files).
 */
class GenerateIconsStep(facet: AndroidFacet, model: RenderTemplateModel) : ModelWizardStep<RenderTemplateModel>(model, "Generate Icons") {
  private val generateIconsPanel: GenerateImageAssetPanel =
    GenerateImageAssetPanel(this, facet, model.template.get().paths, getModel().templateHandle!!.metadata.iconType!!)

  private val listeners = ListenerManager().apply {
    listenAndFire<NamedModuleTemplate>(model.template) { generateIconsPanel.setProjectPaths(it.paths) }
  }
  private val studioPanel: StudioWizardStepPanel = StudioWizardStepPanel(generateIconsPanel)

  override fun getComponent(): JComponent = studioPanel

  override fun onEntering() {
    val iconNameExpression = model.templateHandle!!.metadata.iconName ?: ""
    val iconName: String = if (iconNameExpression.isNotEmpty())
      StringEvaluator().evaluate(iconNameExpression, model.templateValues) ?: ""
    else
      ""

    generateIconsPanel.setOutputName(iconName)
  }

  override fun canGoForward(): ObservableBool = generateIconsPanel.hasErrors().not()

  override fun onProceeding() {
    model.setIconGenerator(generateIconsPanel.iconGenerator)
  }

  override fun dispose() {
    listeners.releaseAll()
  }
}
