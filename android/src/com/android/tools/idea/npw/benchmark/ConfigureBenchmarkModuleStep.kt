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
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.FormFactor.MOBILE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.benchmark.BenchmarkModuleType.MACROBENCHMARK
import com.android.tools.idea.npw.benchmark.BenchmarkModuleType.MICROBENCHMARK
import com.android.tools.adtui.validation.Validator
import com.android.tools.idea.avdmanager.AvdWizardUtils
import com.android.tools.idea.npw.labelFor
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.components.ModuleComboProvider
import com.android.tools.idea.npw.validator.ModuleSelectedValidator
import com.android.tools.idea.npw.verticalGap
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty
import com.android.tools.idea.project.AndroidProjectInfo
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI.Borders.empty
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.Component
import java.util.Optional
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextArea
import javax.swing.SwingConstants

class ConfigureBenchmarkModuleStep(
  model: NewBenchmarkModuleModel
) : ConfigureModuleStep<NewBenchmarkModuleModel>(
  model = model,
  formFactor = MOBILE,
  minSdkLevel = SdkVersionInfo.LOWEST_ACTIVE_API,
  basePackage = getSuggestedProjectPackage(),
  title = message("android.wizard.module.new.benchmark.module.app")
) {
  private val microbenchmarkRadioButton = JRadioButton(MICROBENCHMARK.title)
  private val macrobenchmarkRadioButton = JRadioButton(MACROBENCHMARK.title)
  private val benchmarkModuleType = SelectedRadioButtonProperty(
    MACROBENCHMARK,
    BenchmarkModuleType.values(),
    macrobenchmarkRadioButton,
    microbenchmarkRadioButton,
  )
  private var targetModuleLabel: JLabel? = null
  private val targetModuleCombo: JComboBox<Module> = ModuleComboProvider().createComponent()

  init {
    val appModules = AndroidProjectInfo.getInstance(model.project)
      .getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_APP)
    appModules.forEach { targetModuleCombo.addItem(it) }
    if (appModules.isNotEmpty()) {
      model.targetModule.value = appModules.first()
    }

    bindings.bindTwoWay(benchmarkModuleType, model.benchmarkModuleType)
    listeners.listenAndFire(model.benchmarkModuleType) {
      val isMacrobenchmark = model.benchmarkModuleType.get() == MACROBENCHMARK
      targetModuleLabel?.isVisible = isMacrobenchmark
      targetModuleCombo.isVisible = isMacrobenchmark
    }

    // Only allow min SDK > 29 for macrobenchmarks.
    validatorPanel.registerValidator(model.androidSdkInfo, object : Validator<Optional<AndroidVersionsInfo.VersionItem>> {
      override fun validate(value: Optional<AndroidVersionsInfo.VersionItem>): Validator.Result {
        return when (model.benchmarkModuleType.get()) {
          MACROBENCHMARK -> if (value.isPresent && value.get().minApiLevel < 29) {
            Validator.Result.fromNullableMessage("Macrobenchmark requires minimum SDK > 29")
          } else {
            Validator.Result.OK
          }
          MICROBENCHMARK -> Validator.Result.OK
        }
      }
    }, model.benchmarkModuleType)

    // Only validate target app module for macrobenchmarks
    bindings.bindTwoWay(SelectedItemProperty(targetModuleCombo), model.targetModule)
    val targetModuleValidator = ModuleSelectedValidator()
    validatorPanel.registerValidator(model.targetModule, object : Validator<Optional<Module>> {
      override fun validate(value: Optional<Module>): Validator.Result {
        return if (model.benchmarkModuleType.get() == MACROBENCHMARK) {
          targetModuleValidator.validate(value)
        } else {
          Validator.Result.OK
        }
      }
    }, model.benchmarkModuleType)
  }

  override fun createMainPanel(): DialogPanel = panel {
    row {
      labelFor("Benchmark module type", microbenchmarkRadioButton, message("android.wizard.module.help.benchmark.module.type"))
        buttonGroup {
          macrobenchmarkRadioButton(pushX)
          microbenchmarkRadioButton(pushX)
      }
    }

    row {
      labelFor("Target application", targetModuleCombo, message("android.wizard.module.help.benchmark.target.module")).also {
        it.isVisible = benchmarkModuleType.get() == MACROBENCHMARK
        targetModuleLabel = it
      }
      targetModuleCombo(pushX)
    }

    row {
      labelFor("Module name", moduleName, message("android.wizard.module.help.name"))
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

    if (StudioFlags.NPW_SHOW_GRADLE_KTS_OPTION.get()) {
      verticalGap()

      row {
        gradleKtsCheck()
      }
    }
  }.withBorder(empty(6))

  override fun getPreferredFocusComponent() = moduleName
}
