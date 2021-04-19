/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.benchmark

import com.android.AndroidProjectTypes
import com.android.tools.adtui.device.FormFactor.MOBILE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.benchmark.BenchmarkModuleType.MACROBENCHMARK
import com.android.tools.idea.npw.labelFor
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.template.components.ModuleComboProvider
import com.android.tools.idea.npw.validator.ModuleSelectedValidator
import com.android.tools.idea.npw.verticalGap
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.project.AndroidProjectInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI.Borders.empty
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.JComboBox

class ConfigureMacrobenchmarkModuleStep(
  model: NewBenchmarkModuleModel
) : ConfigureModuleStep<NewBenchmarkModuleModel>(
  model = model,
  formFactor = MOBILE,
  minSdkLevel = 21,
  basePackage = getSuggestedProjectPackage(),
  title = message("android.wizard.module.new.macrobenchmark.module.app")
) {
  private val targetModuleCombo: JComboBox<Module> = ModuleComboProvider().createComponent()

  init {
    listeners.listenAndFire(model.benchmarkModuleType) {
      setShouldShow(model.benchmarkModuleType.get() == MACROBENCHMARK)
    }

    val appModules = AndroidProjectInfo.getInstance(model.project)
      .getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_APP)
    appModules.forEach { targetModuleCombo.addItem(it) }
    if (appModules.isNotEmpty()) {
      model.targetModule.value = appModules.first()
    }

    bindings.bindTwoWay(SelectedItemProperty(targetModuleCombo), model.targetModule)
    validatorPanel.registerValidator(model.targetModule, ModuleSelectedValidator())
  }

  override fun createMainPanel(): DialogPanel = panel {
    row {
      labelFor("Module name", moduleName, message("android.wizard.module.help.name"))
      moduleName(pushX)
    }

    row {
      labelFor("Package name", packageName)
      packageName(pushX)
    }

    row {
      labelFor("Target application module", targetModuleCombo, message("android.wizard.module.help.macrobenchmark.target.module"))
      targetModuleCombo(pushX)
    }

    row {
      labelFor("Language", languageCombo)
      languageCombo(growX)
    }

    row {
      labelFor("Minimum SDK", apiLevelCombo)
      apiLevelCombo(growX)
    }

    if (StudioFlags.NPW_SHOW_GRADLE_KTS_OPTION.get()) {
      verticalGap()

      row {
        gradleKtsCheck()
      }
    }
  }.withBorder(empty(6))

  override fun getPreferredFocusComponent() = moduleName
}
