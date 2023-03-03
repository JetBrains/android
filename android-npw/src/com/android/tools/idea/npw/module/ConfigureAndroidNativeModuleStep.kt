/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.contextLabel
import com.android.tools.idea.npw.model.NewAndroidNativeModuleModel
import com.android.tools.idea.npw.template.components.CppStandardComboProvider
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.wizard.template.CppStandardType
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class ConfigureAndroidNativeModuleStep(val model: NewAndroidNativeModuleModel,
                                       minSdkLevel: Int,
                                       basePackage: String?,
                                       title: String) :
  ConfigureModuleStep<NewAndroidNativeModuleModel>(model, FormFactor.MOBILE, minSdkLevel, basePackage, title) {

  private val appName: JTextField = JBTextField(model.applicationName.get())
  private val cppStandard: JComboBox<CppStandardType> = CppStandardComboProvider().createComponent()

  init {
    bindings.bindTwoWay(TextProperty(appName), model.applicationName)
    bindings.bindTwoWay(SelectedItemProperty(cppStandard), model.cppStandard)
  }

  override fun getPreferredFocusComponent(): JComponent? = appName

  override fun createMainPanel(): JPanel = panel {
    row(contextLabel("Module name", message("android.wizard.module.help.name"))) {
      cell(moduleName).align(AlignX.FILL)
    }

    row("Package name") {
      cell(packageName).align(AlignX.FILL)
    }

    row("Language") {
      cell(languageCombo).align(AlignX.FILL)
    }

    row("C++ Standard") {
      cell(cppStandard).align(AlignX.FILL)
    }

    row("Minimum SDK") {
      cell(apiLevelCombo).align(AlignX.FILL)
    }

    if (StudioFlags.NPW_SHOW_KTS_GRADLE_COMBO_BOX.get()) {
      row("Build configuration language") {
        cell(buildConfigurationLanguageCombo).align(AlignX.FILL)
      }
    }
  }
}
