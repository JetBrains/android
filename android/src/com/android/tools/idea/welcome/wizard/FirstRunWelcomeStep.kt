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

import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import icons.StudioIllustrations
import javax.swing.JComponent

/**
 * Welcome page for the first run wizard
 */
class FirstRunWelcomeStep(model: FirstRunModel) : ModelWizardStep<FirstRunModel>(model, "Welcome") {
  private val ideName = ApplicationNamesInfo.getInstance().fullProductName

  private val newSdkMessage = """Welcome! This wizard will set up your development environment for $ideName.<br>
Additionally, the wizard will help port existing Android apps into $ideName<br>
or create a new Android application project.
"""

  private val existingSdkMessage = """Welcome back! This setup wizard will validate your current Android SDK and<br>
development environment setup. You will have the option to download a new Android<br>
SDK or use an existing installation. Once the setup wizard completes, you can<br>
import an existing Android app into $ideName or start a new Android project.
"""

  private val myPanel = panel {
    row {
      text("<center>${if (model.sdkExists) existingSdkMessage else newSdkMessage}</center>")
    }.bottomGap(BottomGap.MEDIUM).topGap(TopGap.MEDIUM)

    row {
      icon(StudioIllustrations.Common.DEVICES_LINEUP_LARGE).align(AlignX.CENTER)
    }
  }.let { wrapWithVScroll(it) }

  override fun getComponent() = myPanel
  override fun getPreferredFocusComponent(): JComponent? = null
}

