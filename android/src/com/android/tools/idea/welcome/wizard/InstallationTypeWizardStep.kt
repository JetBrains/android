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

import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.android.tools.idea.welcome.wizard.ConfigureInstallationModel.InstallationType
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.ui.components.JBScrollPane

import javax.swing.*

import com.android.tools.idea.welcome.wizard.ConfigureInstallationModel.InstallationType.STANDARD
import com.intellij.uiDesigner.core.Spacer

/**
 * Wizard step for selecting installation types
 */
class InstallationTypeWizardStep(model: ConfigureInstallationModel) : ModelWizardStep<ConfigureInstallationModel>(model, "Install Type") {
  private val standardRadioBtn = JRadioButton("Standard")
  private val customRadioBtn = JRadioButton("Custom")
  private val rootPanel = VerticalPanel(9, 1) {
    spacer(0, 2, 1, 0)
    label("Choose the type of setup you want for Android Studio:", 8, 0, 0, 0)
    spacer( 0, 2, 1, 0)
    elem(standardRadioBtn, 8, 0, 3, 0)
    label("Android Studio will be installed with the most common settings and options.", 8, 0, 0, 0)
    label("Recommended for most users.", 8, 0, 0, 0)
    elem(customRadioBtn, 8, 0, 3, 0)
    label("You can customize installation settings and components installed.", 8, 0, 0, 0)
    spacer(0, 2, 1,6)

    ButtonGroup().apply {
      add(standardRadioBtn)
      add(customRadioBtn)
    }
  }.build()
  private val root = StudioWizardStepPanel.wrappedWithVScroll(rootPanel)

  private val bindings = BindingsManager()

  override fun onEntering() {
    bindings.bindTwoWay(
      SelectedRadioButtonProperty(STANDARD, InstallationType.values(), standardRadioBtn, customRadioBtn),
      model.installationType
    )
  }

  override fun getPreferredFocusComponent(): JComponent? = standardRadioBtn

  override fun getComponent(): JComponent = root

  override fun dispose() {
    bindings.releaseAll()
  }
}
