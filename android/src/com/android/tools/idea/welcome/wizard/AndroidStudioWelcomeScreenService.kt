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
package com.android.tools.idea.welcome.wizard

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.config.InstallerData
import com.android.tools.idea.welcome.config.installerData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.ui.Messages
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.proxy.CommonProxy
import java.io.IOException

/**
 * A service responsible for determining whether to show the Android Studio Welcome Screen and, if
 * so, which wizard mode to use.
 */
@Service(Service.Level.APP)
class AndroidStudioWelcomeScreenService {

  /**
   * Indicates whether the welcome wizard has already been shown in the current IDE session. Used to
   * prevent the wizard from appearing multiple times.
   */
  var wizardWasShown: Boolean = false

  /**
   * Checks if the welcome screen and wizard should be displayed.
   *
   * @return `true` if the welcome screen should be shown, `false` otherwise.
   */
  fun isAvailable(): Boolean {
    val isWizardDisabled =
      GuiTestingService.getInstance().isGuiTestingMode ||
        java.lang.Boolean.getBoolean(SYSTEM_PROPERTY_DISABLE_WIZARD)
    return !wizardWasShown &&
      !isWizardDisabled &&
      getWizardMode(
        AndroidFirstRunPersistentData.getInstance(),
        installerData,
        IdeSdks.getInstance(),
      ) != null
  }

  /**
   * Determines the appropriate mode for the first-run wizard based on current system state and
   * installation status.
   *
   * @param persistentData Persistent data related to Android Studio's first-run configuration.
   * @param installerData Data from the installer, if available. Used for install handoff scenarios.
   * @param ideSdks The IDE's SDK manager. Used to check for installed SDKs.
   * @return The [FirstRunWizardMode] to use, or `null` if the wizard should not be shown.
   */
  fun getWizardMode(
    persistentData: AndroidFirstRunPersistentData,
    installerData: InstallerData?,
    ideSdks: IdeSdks,
  ): FirstRunWizardMode? {
    if (StudioFlags.NPW_FIRST_RUN_SHOW.get()) {
      return FirstRunWizardMode.NEW_INSTALL
    }

    return when {
      isHandoff(persistentData, installerData) -> FirstRunWizardMode.INSTALL_HANDOFF
      !persistentData.isSdkUpToDate -> FirstRunWizardMode.NEW_INSTALL
      ideSdks.eligibleAndroidSdks.isEmpty() -> FirstRunWizardMode.MISSING_SDK
      else -> null
    }
  }

  /** Returns true if the handoff data was updated since the last time wizard ran. */
  private fun isHandoff(
    persistentData: AndroidFirstRunPersistentData,
    installerData: InstallerData?,
  ): Boolean {
    val data = installerData ?: return false
    return (!persistentData.isSdkUpToDate || !persistentData.isSameTimestamp(data.timestamp)) &&
      data.isCurrentVersion
  }

  /**
   * Performs a check to see if the IDE can connect to the internet. This is important for
   * downloading SDK components.
   *
   * @param httpConfigurable The IDE's HTTP settings configurable.
   */
  @WorkerThread
  fun checkInternetConnection(httpConfigurable: HttpConfigurable) {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    CommonProxy.isInstalledAssertion()

    do {
      var retryConnection: Boolean
      try {
        val connection = httpConfigurable.openHttpConnection("http://developer.android.com")
        connection.connect()
        connection.disconnect()
        retryConnection = false
      } catch (e: IOException) {
        retryConnection = promptToRetryFailedConnection()
      } catch (e: RuntimeException) {
        retryConnection = promptToRetryFailedConnection()
      } catch (e: Throwable) {
        // Some other unexpected error related to JRE setup, e.g.
        // java.lang.NoClassDefFoundError: Could not initialize class javax.crypto.SunJCE_b
        //     at javax.crypto.KeyGenerator.a(DashoA13*..)
        //     ....
        // See b/37021138 for more.
        // This shouldn't cause a crash at startup which prevents starting the IDE!
        retryConnection = false
        var message = "Couldn't check internet connection"
        if (e.toString().contains("crypto")) {
          message += "; check your JDK/JRE installation / consider running on a newer version."
        }
        log.warn(message, e)
      }
    } while (retryConnection)
  }

  private fun promptToRetryFailedConnection(): Boolean {
    return invokeAndWaitIfNeeded { promptUserForProxy() }
  }

  private fun promptUserForProxy(): Boolean {
    val selection =
      Messages.showIdeaMessageDialog(
        null,
        "Unable to access Android SDK add-on list",
        "Android Studio First Run",
        arrayOf("Setup Proxy", "Cancel"),
        1,
        Messages.getErrorIcon(),
        null,
      )
    val showSetupProxy = selection == 0
    if (showSetupProxy) {
      HttpConfigurable.editConfigurable(null)
    }
    return showSetupProxy
  }

  companion object {
    /** System property used to disable the first-run wizard. */
    const val SYSTEM_PROPERTY_DISABLE_WIZARD = "disable.android.first.run"

    @JvmStatic
    val instance: AndroidStudioWelcomeScreenService
      get() =
        ApplicationManager.getApplication()
          .getService(AndroidStudioWelcomeScreenService::class.java)
  }
}
