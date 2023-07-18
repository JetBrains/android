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
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Wizard step for selecting installation types
 */
class InstallationTypeWizardStep(model: FirstRunModel) : ModelWizardStep<FirstRunModel>(model, "Install Type") {

  private val panel = panel {
    buttonsGroup("Choose the type of setup you want for ${ApplicationNamesInfo.getInstance().fullProductName}:") {
      row {
        radioButton("Standard", InstallationType.STANDARD)
          .comment("${ApplicationNamesInfo.getInstance().fullProductName} will be installed with the most common settings and options.<br>Recommended for most users.")
          .focused()
      }.topGap(TopGap.MEDIUM)
      row {
        radioButton("Custom", InstallationType.CUSTOM)
          .comment("You can customize installation settings and components installed.")
      }.topGap(TopGap.MEDIUM)
    }.bind({ model.installationType.get() }, { model.installationType.set(it) })
  }

  private val rootPanel = wrapWithVScroll(panel)

  override fun onProceeding() {
    panel.apply()
  }

  override fun getComponent(): JComponent = rootPanel
}
