/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.config.installerData
import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizardHost
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.WelcomeScreenProvider
import com.intellij.util.net.HttpConfigurable
import javax.swing.JRootPane

val log = logger<AndroidStudioWelcomeScreenProvider>()

/** Shows a wizard first time Android Studio is launched. */
class AndroidStudioWelcomeScreenProvider : WelcomeScreenProvider {
  override fun createWelcomeScreen(rootPane: JRootPane): WelcomeScreen? {
    ApplicationManager.getApplication().executeOnPooledThread {
      if (!StudioFlags.NPW_OFFLINE.get()) {
        AndroidStudioWelcomeScreenService.instance.checkInternetConnection(
          HttpConfigurable.getInstance()
        )
      }
    }

    val useNewWizard = StudioFlags.NPW_FIRST_RUN_WIZARD.get()
    val wizardMode =
      AndroidStudioWelcomeScreenService.instance.getWizardMode(
        AndroidFirstRunPersistentData.getInstance(),
        installerData,
        IdeSdks.getInstance(),
      ) ?: return null
    val sdkComponentInstallerProvider = SdkComponentInstallerProvider()
    val tracker = FirstRunWizardTracker(wizardMode.toMetricKind())
    return createWelcomeScreen(useNewWizard, wizardMode, sdkComponentInstallerProvider, tracker)
  }

  @VisibleForTesting
  fun createWelcomeScreen(
    useNewWizard: Boolean,
    wizardMode: FirstRunWizardMode,
    sdkComponentInstallerProvider: SdkComponentInstallerProvider,
    tracker: FirstRunWizardTracker,
  ): WelcomeScreen {
    AndroidStudioWelcomeScreenService.instance.wizardWasShown = true
    return if (useNewWizard)
      StudioFirstRunWelcomeScreen(wizardMode, sdkComponentInstallerProvider, tracker)
    else FirstRunWizardHost(wizardMode, sdkComponentInstallerProvider, tracker)
  }

  override fun isAvailable(): Boolean {
    return AndroidStudioWelcomeScreenService.instance.isAvailable()
  }
}
