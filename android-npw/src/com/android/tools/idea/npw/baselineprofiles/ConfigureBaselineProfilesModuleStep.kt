/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.npw.baselineprofiles

import com.android.AndroidProjectTypes
import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.FormFactor
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.createValidator
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.npw.contextLabel
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.template.components.ModuleComboProvider
import com.android.tools.idea.npw.validator.ModuleSelectedValidator
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.SelectedProperty
import com.android.tools.idea.project.AndroidProjectInfo
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.module.Module
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.jetbrains.android.util.AndroidBundle
import javax.swing.JComboBox
import javax.swing.JPanel


private const val BASELINE_PROFILES_AGP_MIN_VERSION = "8.1.0"
private const val GMD_LINK = "https://d.android.com/r/studio-ui/testing/gradle-managed-devices"

class ConfigureBaselineProfilesModuleStep(
  model: NewBaselineProfilesModuleModel
) : ConfigureModuleStep<NewBaselineProfilesModuleModel>(
  model = model,
  formFactor = FormFactor.MOBILE,
  minSdkLevel = SdkVersionInfo.LOWEST_PROFILE_GUIDED_OPTIMIZATIONS_SDK_VERSION,
  basePackage = getSuggestedProjectPackage(),
  title = AndroidBundle.message("android.wizard.module.new.baselineprofiles.module.app")
) {
  private val targetModuleCombo: JComboBox<Module> = ModuleComboProvider().createComponent()

  @Suppress("DialogTitleCapitalization")
  private val useGmdCheck = JBCheckBox("Use Gradle Managed Device")

  init {
    bindTargetModule()
    validateMinAgpVersion()

    bindings.bindTwoWay(SelectedProperty(useGmdCheck), model.useGmd)
  }

  private fun bindTargetModule() {
    val appModules = AndroidProjectInfo.getInstance(model.project)
      .getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_APP)

    appModules.forEach { targetModuleCombo.addItem(it) }
    if (appModules.isNotEmpty()) {
      model.targetModule.value = appModules.first()
    }

    bindings.bindTwoWay(SelectedItemProperty(targetModuleCombo), model.targetModule)

    val targetModuleValidator = ModuleSelectedValidator()
    validatorPanel.registerValidator(model.targetModule, createValidator { value ->
      targetModuleValidator.validate(value)
    })
  }

  private fun validateMinAgpVersion() {
    val minAgpVersion = AgpVersion.parse(BASELINE_PROFILES_AGP_MIN_VERSION)

    validatorPanel.registerValidator(agpVersion, createValidator { version ->
      if (version.isPresent && version.get().compareIgnoringQualifiers(minAgpVersion) < 0) {
        Validator.Result.fromNullableMessage(
          AndroidBundle.message("android.wizard.validate.module.needs.new.agp.baseline.profiles", BASELINE_PROFILES_AGP_MIN_VERSION)
        )
      }
      else {
        Validator.Result.OK
      }
    })
  }

  override fun createMainPanel(): JPanel = panel {
    row(
      contextLabel("Target application", AndroidBundle.message("android.wizard.module.help.baselineprofiles.target.module.description"))) {
      cell(targetModuleCombo).horizontalAlign(HorizontalAlign.FILL)
    }

    row(contextLabel("Module name", AndroidBundle.message("android.wizard.module.help.name"))) {
      cell(moduleName).horizontalAlign(HorizontalAlign.FILL)
    }

    row("Package name") {
      cell(packageName).horizontalAlign(HorizontalAlign.FILL)
    }

    row("Language") {
      cell(languageCombo).horizontalAlign(HorizontalAlign.FILL)
    }

    row("Minimum SDK") {
      cell(apiLevelCombo).horizontalAlign(HorizontalAlign.FILL)
    }

    if (StudioFlags.NPW_SHOW_KTS_GRADLE_COMBO_BOX.get()) {
      row("Build configuration language") {
        cell(buildConfigurationLanguageCombo).horizontalAlign(HorizontalAlign.FILL)
      }
    }

    row {
      cell(useGmdCheck)
      cell(
        ContextHelpLabel.createWithLink(
          null,
          AndroidBundle.message("android.wizard.module.help.baselineprofiles.usegmd.description"),
          "Learn more"
        ) { BrowserUtil.browse(GMD_LINK) }
      ).horizontalAlign(HorizontalAlign.LEFT)
    }
  }
}
