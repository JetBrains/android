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

import com.android.tools.idea.ui.wizard.StudioWizardStepPanel.wrappedWithVScroll
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBLabel
import com.intellij.uiDesigner.core.Spacer
import javax.swing.JComponent

/**
 * This is to be shown as the first HAXM Wizard step just to inform the user that HAXM uninstallation is about to start.
 * It is here just to make sure we don't run uninstallation operations straight away as the first wizard step,
 * as this would not be in line with common wizard conventions.
 */
class HaxmUninstallInfoStep : ModelWizardStep.WithoutModel("Uninstalling HAXM") {
  private val infoLabel = JBLabel(
    "This wizard will execute HAXM stand-alone uninstaller. This is an additional step required to remove this package."
  )
  private val nextLabel = JBLabel("Click 'Next' to proceed")

  private val panel = VerticalPanel(3, 1) {
    elem(infoLabel, 8, 0, 0, 0)
    elem(Spacer(), 0, 2, 1, 0)
    elem(nextLabel, 8, 0, 0, 0)
  }.build()

  private val root = wrappedWithVScroll(panel)

  override fun getComponent(): JComponent = root
  override fun getPreferredFocusComponent(): JComponent? = panel
}
