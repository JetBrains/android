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
import com.android.tools.idea.welcome.wizard.FirstRunModel.InstallationType
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.ui.layout.panel
import com.intellij.uiDesigner.core.Spacer
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JRadioButton

/**
 * Wizard step for selecting installation types
 */
class InstallationTypeWizardStep(model: FirstRunModel) : ModelWizardStep<FirstRunModel>(model, "Install Type") {
  private val standardRadioBtn = JRadioButton("Standard")
  private val customRadioBtn = JRadioButton("Custom")
  private val rootPanel = panel {
    row {
      Spacer()()
    }
    row {
      label("Choose the type of setup you want for Android Studio:")
    }
    row {
      Spacer()()
    }
    row {
      standardRadioBtn()
    }
    row {
      label("Android Studio will be installed with the most common settings and options.")
    }
    row {
      label("Recommended for most users.")
    }
    row {
      customRadioBtn()
    }
    row {
      label("You can customize installation settings and components installed.")
    }
    row {
      Spacer()()
    }

    ButtonGroup().apply {
      add(standardRadioBtn)
      add(customRadioBtn)
    }
  }

  private val root = StudioWizardStepPanel.wrappedWithVScroll(rootPanel)

  private val bindings = BindingsManager()

  override fun onEntering() {
    bindings.bindTwoWay(
      SelectedRadioButtonProperty(InstallationType.STANDARD, InstallationType.values(), standardRadioBtn, customRadioBtn),
      model.installationType
    )
  }

  override fun getPreferredFocusComponent(): JComponent? = standardRadioBtn

  override fun getComponent(): JComponent = root

  override fun dispose() {
    bindings.releaseAll()
  }
}
