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

import com.android.tools.idea.sdk.install.VmType
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll
import com.intellij.ui.layout.panel
import com.intellij.uiDesigner.core.Spacer

/**
 * This is to be shown as the first HAXM Wizard step just to inform the user that HAXM uninstallation is about to start.
 * It is here just to make sure we don't run uninstallation operations straight away as the first wizard step,
 * as this would not be in line with common wizard conventions.
 */
class VmUninstallInfoStep(
  type: VmType = VmType.HAXM
) : ModelWizardStep.WithoutModel("Uninstalling $type") {
  private val panel = panel {
    row {
      label("This wizard will execute $type stand-alone uninstaller. This is an additional step required to remove this package.")
    }
    row {
      Spacer()()
    }
    row {
      label("Click 'Next' to proceed")
    }
  }
  private val root = wrapWithVScroll(panel)

  override fun getComponent() = root
  override fun getPreferredFocusComponent() = panel
}
