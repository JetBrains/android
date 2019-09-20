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
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.config.installerData
import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizardHost
import com.google.common.util.concurrent.Atomics
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.WelcomeScreenProvider
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.proxy.CommonProxy
import java.io.IOException
import javax.swing.JRootPane

val log = logger<AndroidStudioWelcomeScreenProvider>()

/**
 * Shows a wizard first time Android Studio is launched.
 */
class AndroidStudioWelcomeScreenProvider : WelcomeScreenProvider {
  override fun createWelcomeScreen(rootPane: JRootPane): WelcomeScreen {
    checkInternetConnection()
    val wizardMode = wizardMode!!
    // This means isAvailable was false! Why are we even called?

    ourWasShown = true
    return if (StudioFlags.NPW_FIRST_RUN_WIZARD.get()) StudioFirstRunWelcomeScreen(wizardMode) else FirstRunWizardHost(wizardMode)
  }

  override fun isAvailable(): Boolean {
    val isWizardDisabled = GuiTestingService.getInstance().isGuiTestingMode || java.lang.Boolean.getBoolean(SYSTEM_PROPERTY_DISABLE_WIZARD)
    return !ourWasShown && !isWizardDisabled && wizardMode != null
  }

  private enum class ConnectionState {
    OK, NO_CONNECTION
  }

  companion object {
    private const val SYSTEM_PROPERTY_DISABLE_WIZARD = "disable.android.first.run"
    private var ourWasShown: Boolean = false // Do not show wizard multiple times in one session even if it was canceled

    /**
     * Analyzes system state and decides if and how the wizard should be invoked.
     *
     * @return one of the [FirstRunWizardMode] constants or `null` if wizard is not needed.
     */
    // TODO: Remove this temporary code, once the Welcome Wizard is more completely ported.
    // This code forces the first run wizard to run every time, but eventually it should only run the first time.
    val wizardMode: FirstRunWizardMode?
      @JvmStatic
      get() {
        if (StudioFlags.NPW_FIRST_RUN_WIZARD.get() || StudioFlags.NPW_FIRST_RUN_SHOW.get()) {
          return FirstRunWizardMode.NEW_INSTALL
        }

        val persistentData = AndroidFirstRunPersistentData.getInstance()
        return when {
          isHandoff(persistentData) -> FirstRunWizardMode.INSTALL_HANDOFF
          !persistentData.isSdkUpToDate -> FirstRunWizardMode.NEW_INSTALL
          IdeSdks.getInstance().eligibleAndroidSdks.isEmpty() -> FirstRunWizardMode.MISSING_SDK
          else -> null
        }
      }

    /**
     * Returns true if the handoff data was updated since the last time wizard ran.
     */
    private fun isHandoff(persistentData: AndroidFirstRunPersistentData): Boolean {
      val data = installerData ?: return false
      return (!persistentData.isSdkUpToDate || !persistentData.isSameTimestamp(data.timestamp)) && data.isCurrentVersion
    }

    private fun checkInternetConnection(): ConnectionState {
      CommonProxy.isInstalledAssertion()
      var result: ConnectionState? = null
      while (result == null) {
        try {
          val connection = HttpConfigurable.getInstance().openHttpConnection("http://developer.android.com")
          connection.connect()
          connection.disconnect()
          result = ConnectionState.OK
        }
        catch (e: IOException) {
          result = promptToRetryFailedConnection()
        }
        catch (e: RuntimeException) {
          result = promptToRetryFailedConnection()
        }
        catch (e: Throwable) {
          // Some other unexpected error related to JRE setup, e.g.
          // java.lang.NoClassDefFoundError: Could not initialize class javax.crypto.SunJCE_b
          //     at javax.crypto.KeyGenerator.a(DashoA13*..)
          //     ....
          // See http://b.android.com/149270 for more.
          // This shouldn't cause a crash at startup which prevents starting the IDE!
          result = ConnectionState.NO_CONNECTION
          var message = "Couldn't check internet connection"
          if (e.toString().contains("crypto")) {
            message += "; check your JDK/JRE installation / consider running on a newer version."
          }
          log.warn(message, e)
        }

      }
      return result
    }

    private fun promptToRetryFailedConnection(): ConnectionState? {
      val atomicBoolean = Atomics.newReference<ConnectionState>()
      invokeAndWaitIfNeeded {  atomicBoolean.set(promptUserForProxy()) }
      return atomicBoolean.get()
    }

    private fun promptUserForProxy(): ConnectionState? {
      val selection = Messages.showIdeaMessageDialog(
        null, "Unable to access Android SDK add-on list", "Android Studio First Run",
        arrayOf("Setup Proxy", "Cancel"), 1, Messages.getErrorIcon(), null
      )
      return if (selection == 0) {
        HttpConfigurable.editConfigurable(null)
        null
      }
      else {
        ConnectionState.NO_CONNECTION
      }
    }
  }
}
