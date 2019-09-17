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
package com.android.tools.idea.welcome.wizard

import com.android.tools.idea.npw.platform.*
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel.wrappedWithVScroll
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.uiDesigner.core.Spacer

import javax.swing.*

/**
 * Welcome page for the first run wizard
 */
class FirstRunWelcomeStep(sdkExists: Boolean) : ModelWizardStep.WithoutModel("Welcome") {
  // TODO(qumeric): should we use HTML instead?
  private val newSdkMessage = VerticalPanel(1, 1) {
    html("""
      Welcome! This wizard will set up your development environment for Android Studio.
      Additionally, the wizard will help port existing Android apps into Android Studio
      or create a new Android application project.
      """.trimIndent()
    )
  }.build()

  private val existingSdkMessage = VerticalPanel(1, 1) {
    html("""Welcome back! This setup wizard will validate your current Android SDK and
    development environment setup. You will have the option to download a new Android
    SDK or use an existing installation. Once the setup wizard completes, you can
    import an existing Android app into Android Studio or start a new Android project.
    """.trimIndent()
    )
  }.build()

  private val icons = JBLabel("")
  private val panel = VerticalPanel(6, 1) {
    elem(Spacer(), 0, 2, 1, 6)
    elem(newSdkMessage, 0, 3, 3, 3)
    elem(existingSdkMessage, 0, 3, 3, 3)
    elem(Spacer(), 0, 2, 1, 0)
    elem(icons, 0, 0, 0, 0)
    elem(Spacer(), 0, 2, 1, 6)
  }.build()

  private val root  = wrappedWithVScroll(panel)

  init {
    icons.icon = getFormFactorsImage(icons, false)
    existingSdkMessage.isVisible = sdkExists
    newSdkMessage.isVisible = !sdkExists
  }

  override fun getComponent(): JComponent = root

  // Doesn't matter
  override fun getPreferredFocusComponent(): JComponent? = null
}
