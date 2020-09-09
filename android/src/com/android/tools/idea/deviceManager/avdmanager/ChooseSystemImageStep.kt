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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.ui.wizard.deprecated.StudioWizardStepPanel
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.panel
import java.util.Optional
import javax.swing.JComponent

/**
 * Wizard step for selecting a [SystemImage] from the installed images in the SDK.
 */
class ChooseSystemImageStep(model: AvdOptionsModel, project: Project?) : ModelWizardStep<AvdOptionsModel>(model, "System Image") {
  private var chooseImagePanel = ChooseSystemImagePanel(project, model.device().valueOrNull, model.systemImage().valueOrNull).apply {
    addSystemImageListener { model.systemImage().setNullableValue(it) }
  }
  private val panel = panel(LCFlags.fill) {
    row {
      chooseImagePanel(grow)
    }
  }
  private val validatorPanel: ValidatorPanel = ValidatorPanel(this, panel)
  private val studioWizardStepPanel: StudioWizardStepPanel = StudioWizardStepPanel(validatorPanel, "Select a system image")

  init {
    FormScalingUtil.scaleComponentTree(this.javaClass, studioWizardStepPanel)
  }

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    validatorPanel.registerValidator(
      model.systemImage(),
      object : Validator<Optional<SystemImageDescription>> {
        override fun validate(value: Optional<SystemImageDescription>): Validator.Result =
          if (value.isPresent) Validator.Result.OK
          else Validator.Result(Validator.Severity.ERROR, "A system image must be selected to continue.")
      }
    )
  }

  override fun onEntering() {
    chooseImagePanel.setDevice(model.device().valueOrNull)
  }

  override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  override fun getComponent(): JComponent = studioWizardStepPanel
}