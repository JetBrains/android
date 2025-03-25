/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** Step to show a message that the SDK is missing. */
class MissingSdkAlertStep(private val tracker: FirstRunWizardTracker) :
  ModelWizardStep.WithoutModel("Missing SDK") {
  private val panel = createPanel()

  override fun getComponent(): JComponent = panel

  companion object {
    fun createPanel(): JPanel {
      val panel = JPanel(BorderLayout())
      panel.add(
        JBLabel(
          "<html><b>No Android SDK found.</b><br><br>Before continuing, you must download the necessary " +
            "components or select an existing SDK."
        ),
        BorderLayout.NORTH,
      )
      return panel
    }
  }

  override fun onShowing() {
    super.onShowing()
    tracker.trackStepShowing(SetupWizardEvent.WizardStep.WizardStepKind.MISSING_SDK_ALERT)
  }
}
