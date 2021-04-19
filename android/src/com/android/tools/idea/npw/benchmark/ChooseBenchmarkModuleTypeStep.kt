/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.tools.adtui.device.FormFactor.MOBILE
import com.android.tools.idea.avdmanager.AvdWizardUtils.STANDARD_FONT
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI.Borders.empty
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.JLabel
import javax.swing.JRadioButton
import javax.swing.JTextArea
import javax.swing.UIManager

class ChooseBenchmarkModuleTypeStep(
  model: NewBenchmarkModuleModel,
  title: String,
) : ConfigureModuleStep<NewBenchmarkModuleModel>(
  model = model,
  formFactor = MOBILE,
  title = title
) {
  private val microbenchmarkRadioButton = JRadioButton(BenchmarkModuleType.MICROBENCHMARK.title)
  private val macrobenchmarkRadioButton = JRadioButton(BenchmarkModuleType.MACROBENCHMARK.title)
  private val benchmarkModuleType = SelectedRadioButtonProperty(
    BenchmarkModuleType.MICROBENCHMARK,
    BenchmarkModuleType.values(),
    microbenchmarkRadioButton,
    macrobenchmarkRadioButton
  )

  init {
    bindings.bindTwoWay(benchmarkModuleType, model.benchmarkModuleType)
  }

  private val microbenchmarkDescription = JTextArea(
    message("android.wizard.module.new.benchmark.step.choose.microbenchmark.description")
  ).apply {
    isEditable = false
    isOpaque = false
    isFocusable = false
    lineWrap = true
    wrapStyleWord = true
    font = STANDARD_FONT
  }

  private val macrobenchmarkDescription = JTextArea(
    message("android.wizard.module.new.benchmark.step.choose.macrobenchmark.description")
  ).apply {
    isEditable = false
    isOpaque = false
    isFocusable = false
    lineWrap = true
    wrapStyleWord = true
    font = STANDARD_FONT
  }


  override fun createMainPanel(): DialogPanel = panel {
    buttonGroup {
      row {
        microbenchmarkRadioButton(pushX)
      }
      row {
        microbenchmarkDescription(pushX)
      }
      row {
        macrobenchmarkRadioButton(pushX)
      }
      row {
        macrobenchmarkDescription(pushX)
      }
    }
  }.withBorder(empty(6))

  override fun getPreferredFocusComponent() = moduleName

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    return listOf(
      ConfigureMicrobenchmarkModuleStep(model = model),
      ConfigureMacrobenchmarkModuleStep(model = model),
    ) + super.createDependentSteps()
  }
}