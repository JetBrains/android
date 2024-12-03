/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.tools.idea.welcome.wizard.deprecated.InstallationTypeWizardStepForm
import com.android.tools.idea.wizard.model.ModelWizardStep
import javax.swing.JComponent

/** Wizard step for selecting installation types */
class InstallationTypeWizardStep(model: FirstRunWizardModel) :
  ModelWizardStep<FirstRunWizardModel?>(model, "Install Type") {
  private val myForm = InstallationTypeWizardStepForm()

  override fun getComponent(): JComponent {
    return myForm.contents
  }

  override fun getPreferredFocusComponent(): JComponent? {
    if (myForm.standardRadioButton.isSelected) {
      return myForm.standardRadioButton
    } else if (myForm.customRadioButton.isSelected) {
      return myForm.customRadioButton
    }
    return myForm.standardRadioButton
  }

  override fun onEntering() {
    super.onEntering()

    val installationType = model.installationType ?: FirstRunWizardModel.InstallationType.STANDARD
    myForm.standardRadioButton.isSelected =
      installationType == FirstRunWizardModel.InstallationType.STANDARD
    myForm.customRadioButton.isSelected =
      installationType == FirstRunWizardModel.InstallationType.CUSTOM
  }

  override fun onProceeding() {
    super.onProceeding()

    model.installationType =
      if (myForm.standardRadioButton.isSelected) FirstRunWizardModel.InstallationType.STANDARD
      else FirstRunWizardModel.InstallationType.CUSTOM
  }
}
