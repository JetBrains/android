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
import com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll
import com.intellij.ui.dsl.builder.panel

/**
 * This is to be shown as the first AEHD Wizard step just to inform the user that AEHD uninstallation is about to start.
 * It is here just to make sure we don't run uninstallation operations straight away as the first wizard step,
 * as this would not be in line with common wizard conventions.
 */
class AehdUninstallInfoStep(
) : ModelWizardStep.WithoutModel("Uninstalling Android Emulator hypervisor driver") {

  private val infoText = """
This wizard will execute Android Emulator hypervisor driver stand-alone uninstaller. This is an additional step required to remove this package<br><br>
Click 'Next' to proceed
"""

  private val myPanel = panel {
    row { text(infoText) }
  }

  private val root = wrapWithVScroll(myPanel)

  override fun getComponent() = root
  override fun getPreferredFocusComponent() = myPanel
}
