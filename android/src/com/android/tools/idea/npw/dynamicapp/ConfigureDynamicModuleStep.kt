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
import com.android.tools.idea.npw.labelFor
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.template.components.ModuleComboProvider
import com.android.tools.idea.npw.validator.ModuleSelectedValidator
import com.android.tools.idea.npw.verticalGap
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.SelectedProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI.Borders.empty
import org.jetbrains.android.util.AndroidBundle
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField

class ConfigureDynamicModuleStep(
  model: DynamicFeatureModel, basePackage: String
) : ConfigureModuleStep<DynamicFeatureModel>(
  model, FormFactor.MOBILE, SdkVersionInfo.LOWEST_ACTIVE_API, basePackage,
  AndroidBundle.message("android.wizard.module.config.title")
) {
  private val baseApplication: JComboBox<Module> = ModuleComboProvider().createComponent()

  // TODO(qumeric): unify with ConfigureModuleDownloadOptionsStep?
  private val moduleTitle: JTextField = JBTextField()
  private val fusingCheckbox: JCheckBox = JBCheckBox("Fusing (install module on pre-Lollipop devices)")

  override fun createMainPanel(): DialogPanel = panel {
    row {
      labelFor("Base Application Module", baseApplication)
      baseApplication(growX)
    }

    row {
      labelFor("Module name", moduleName, AndroidBundle.message("android.wizard.module.help.name"))
      moduleName(pushX)
    }

    row {
      labelFor("Package name", packageName)
      packageName(pushX)
    }

    row {
      labelFor("Language", languageCombo)
      languageCombo(growX)
    }

    row {
      labelFor("Minimum SDK", apiLevelCombo)
      apiLevelCombo(growX)
    }

    if (model.isInstant) {
      row {
        labelFor("Module title", moduleTitle, "This may be visible to users")
        moduleTitle()
      }
    }

    verticalGap()

    row {
      fusingCheckbox()
    }

    if (StudioFlags.NPW_SHOW_GRADLE_KTS_OPTION.get()) {
      row {
        gradleKtsCheck()
      }
    }
  }.withBorder(empty(6))

  init {
    AndroidProjectInfo.getInstance(model.project)
      .getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_APP)
      .forEach { module: Module -> baseApplication.addItem(module) }
    val baseApplication: OptionalProperty<Module> = model.baseApplication
    bindings.bind(baseApplication, SelectedItemProperty(this.baseApplication))

    validatorPanel.registerValidator(baseApplication, ModuleSelectedValidator())

    if (model.isInstant) {
      bindings.bind(model.featureFusing, SelectedProperty(fusingCheckbox))
      bindings.bindTwoWay(TextProperty(moduleTitle), getModel().featureTitle)
    }
    else {
      fusingCheckbox.isVisible = false
    }
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> =
    listOf(ConfigureModuleDownloadOptionsStep(model)) + super.createDependentSteps()

  override fun getPreferredFocusComponent(): JComponent? = moduleName
}