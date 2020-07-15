/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.java

import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.labelFor
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.validator.ClassNameValidator
import com.android.tools.idea.npw.verticalGap
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import org.jetbrains.android.util.AndroidBundle
import javax.swing.JTextField

class ConfigureLibraryModuleStep(
  model: NewLibraryModuleModel, title: String
) : ConfigureModuleStep<NewLibraryModuleModel>(
  model, FormFactor.MOBILE, SdkVersionInfo.LOWEST_ACTIVE_API, title = title
) {
  private val className: JTextField = JBTextField()

  private val panel = panel {
    row {
      cell {
        labelFor("Library name:", moduleName, AndroidBundle.message("android.wizard.module.help.name"))
      }
      moduleName()
    }
    row {
      labelFor("Package name:", packageName.textField)
      packageName()
    }
    row {
      labelFor("Class name:", className)
      className()
    }
    row {
      labelFor("Language:", languageCombo)
      languageCombo()
    }
    if (StudioFlags.NPW_SHOW_GRADLE_KTS_OPTION.get()) {
      verticalGap()

      row {
        gradleKtsCheck()
      }
    }
  }

  override val validatorPanel: ValidatorPanel = ValidatorPanel(this, StudioWizardStepPanel.wrappedWithVScroll(panel))

  init {
    bindings.bindTwoWay(TextProperty(className), model.className)

    validatorPanel.apply {
      registerValidator(TextProperty(className), ClassNameValidator())
    }
  }

  override fun getPreferredFocusComponent() = moduleName
}
