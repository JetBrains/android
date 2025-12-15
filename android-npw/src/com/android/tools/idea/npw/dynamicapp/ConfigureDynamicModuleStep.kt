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
package com.android.tools.idea.npw.dynamicapp

import com.android.AndroidProjectTypes
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.contextLabel
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.module.generateBuildConfigurationLanguageRow
import com.android.tools.idea.npw.template.components.ModuleComboProvider
import com.android.tools.idea.npw.validator.ModuleSelectedValidator
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI.Borders.empty
import javax.swing.JComboBox
import javax.swing.JComponent
import org.jetbrains.android.util.AndroidBundle

class ConfigureDynamicModuleStep(model: DynamicFeatureModel, basePackage: String) :
  ConfigureModuleStep<DynamicFeatureModel>(
    model,
    FormFactor.MOBILE,
    SdkVersionInfo.LOWEST_ACTIVE_API,
    basePackage,
    AndroidBundle.message("android.wizard.module.config.title"),
  ) {
  private val baseApplication: JComboBox<Module> = ModuleComboProvider().createComponent()

  override fun createMainPanel(): DialogPanel =
    panel {
        row("Base Application Module") { cell(baseApplication).align(AlignX.FILL) }

        row(contextLabel("Module name", AndroidBundle.message("android.wizard.module.help.name"))) {
          cell(moduleName).align(AlignX.FILL)
        }

        row("Package name") { cell(packageName).align(AlignX.FILL) }

        row("Language") { cell(languageCombo).align(AlignX.FILL) }

        row("Minimum SDK") { cell(apiLevelCombo).align(AlignX.FILL) }

        if (StudioFlags.NPW_SHOW_KTS_GRADLE_COMBO_BOX.get()) {
          generateBuildConfigurationLanguageRow(buildConfigurationLanguageCombo)
        }
      }
      .withBorder(empty(6))

  init {
    AndroidProjectInfo.getInstance(model.project)
      .getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_APP)
      .forEach { module: Module -> baseApplication.addItem(module) }
    val baseApplication: OptionalProperty<Module> = model.baseApplication
    bindings.bind(baseApplication, SelectedItemProperty(this.baseApplication))

    validatorPanel.registerValidator(baseApplication, ModuleSelectedValidator())
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> =
    listOf(ConfigureModuleDownloadOptionsStep(model)) + super.createDependentSteps()

  override fun getPreferredFocusComponent(): JComponent? = moduleName
}
