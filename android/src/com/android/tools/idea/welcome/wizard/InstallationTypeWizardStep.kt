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
package com.android.tools.idea.welcome.wizard

import com.android.tools.idea.welcome.wizard.FirstRunModel.InstallationType
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.PropertyBinding
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.withSelectedBinding
import com.intellij.uiDesigner.core.Spacer
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JRadioButton

/**
 * Wizard step for selecting installation types
 */
class InstallationTypeWizardStep(model: FirstRunModel) : ModelWizardStep<FirstRunModel>(model, "Install Type") {
  private lateinit var standardRadioBtn: JRadioButton
  private lateinit var customRadioBtn : JRadioButton

  private val panel = panel {
    row {
      label("Choose the type of setup you want for Android Studio:")
    }
    row {
      Spacer()()
    }
    row {
      standardRadioBtn = installationTypeRadioButton(
        "Standard",
        "Android Studio will be installed with the most common settings and options.\nRecommended for most users.",
        InstallationType.STANDARD
      ).component
    }
    row {
      Spacer()()
    }
    row {
      customRadioBtn = installationTypeRadioButton(
        "Custom",
        "You can customize installation settings and components installed.",
        InstallationType.CUSTOM
      ).component
    }

    ButtonGroup().apply {
      add(standardRadioBtn)
      add(customRadioBtn)
    }
  }

  private val rootPanel = wrapWithVScroll(panel)

  override fun onProceeding() {
    panel.apply()
  }

  override fun getPreferredFocusComponent(): JComponent? = standardRadioBtn

  override fun getComponent(): JComponent = rootPanel

  private fun Cell.installationTypeRadioButton(text: String, comment: String, installationType: InstallationType) =
    radioButton(text, comment).withSelectedBinding(PropertyBinding(
      get = { model.installationType.get() == installationType },
      set = { if (it) model.installationType.set(installationType) }
    ))
}
