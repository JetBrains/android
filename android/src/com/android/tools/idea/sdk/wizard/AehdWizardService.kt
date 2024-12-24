/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.sdk.wizard

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker
import com.google.wireless.android.sdk.stats.SetupWizardEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

/**
 * A service responsible for showing the wizard which configures the Android Emulator hypervisor
 * driver (AEHD).
 */
@Service(Service.Level.APP)
class AehdWizardService {

  /**
   * Displays the wizard.
   *
   * @return `true` if the wizard finished successfully, `false` otherwise
   */
  @UiThread
  fun showAndGet(installationIntention: AehdSdkComponentTreeNode.InstallationIntention): Boolean {
    val tracker = FirstRunWizardTracker(SetupWizardEvent.SetupWizardMode.AEHD_WIZARD)
    if (!StudioFlags.NPW_FIRST_RUN_WIZARD.get()) {
      val wizard = AehdWizard(installationIntention, AehdWizardController(), tracker)
      wizard.init()
      return wizard.showAndGet()
    } else {
      val wizard = AehdModelWizard(installationIntention, AehdWizardController(), tracker)
      return wizard.showAndGet()
    }
  }

  companion object {
    @JvmStatic
    val instance: AehdWizardService
      get() = ApplicationManager.getApplication().getService(AehdWizardService::class.java)
  }
}
