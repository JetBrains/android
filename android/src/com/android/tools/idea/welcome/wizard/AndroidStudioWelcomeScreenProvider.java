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
package com.android.tools.idea.welcome.wizard;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.config.InstallerData;
import com.google.common.util.concurrent.Atomics;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.WelcomeScreenProvider;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.proxy.CommonProxy;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shows a wizard first time Android Studio is launched
 */
public final class AndroidStudioWelcomeScreenProvider implements WelcomeScreenProvider {
  private static final String SYSTEM_PROPERTY_DISABLE_WIZARD = "disable.android.first.run";
  private static boolean ourWasShown; // Do not show wizard multiple times in one session even if it was canceled

  /**
   * Analyzes system state and decides if and how the wizard should be invoked.
   *
   * @return one of the {@link FirstRunWizardMode} constants or {@code null} if wizard is not needed.
   */
  @Nullable
  public static FirstRunWizardMode getWizardMode() {
    AndroidFirstRunPersistentData persistentData = AndroidFirstRunPersistentData.getInstance();
    if (isHandoff(persistentData)) {
      return FirstRunWizardMode.INSTALL_HANDOFF;
    }
    else if (!persistentData.isSdkUpToDate()) {
      return FirstRunWizardMode.NEW_INSTALL;
    }
    else if (IdeSdks.getInstance().getEligibleAndroidSdks().isEmpty()) {
      return FirstRunWizardMode.MISSING_SDK;
    }
    else {
      return null;
    }
  }

  /**
   * return true if the handoff data was updated since the last time wizard ran
   */
  private static boolean isHandoff(AndroidFirstRunPersistentData persistentData) {
    if (InstallerData.exists()) {
      if (!persistentData.isSdkUpToDate() || !persistentData.isSameTimestamp(InstallerData.get().getTimestamp())) {
        return InstallerData.get().isCurrentVersion();
      }
    }
    return false;
  }

  @NotNull
  private static ConnectionState checkInternetConnection() {
    CommonProxy.isInstalledAssertion();
    ConnectionState result = null;
    while (result == null) {
      try {
        HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection("http://developer.android.com");
        connection.connect();
        connection.disconnect();
        result = ConnectionState.OK;
      }
      catch (IOException | RuntimeException e) {
        result = promptToRetryFailedConnection();
      }
      catch (Throwable e) {
        // Some other unexpected error related to JRE setup, e.g.
        // java.lang.NoClassDefFoundError: Could not initialize class javax.crypto.SunJCE_b
        //     at javax.crypto.KeyGenerator.a(DashoA13*..)
        //     ....
        // See http://b.android.com/149270 for more.
        // This shouldn't cause a crash at startup which prevents starting the IDE!
        result = ConnectionState.NO_CONNECTION;
        String message = "Couldn't check internet connection";
        if (e.toString().contains("crypto")) {
          message += "; check your JDK/JRE installation / consider running on a newer version.";
        }
        Logger.getInstance(AndroidStudioWelcomeScreenProvider.class).warn(message, e);
      }
    }
    return result;
  }

  @Nullable
  private static ConnectionState promptToRetryFailedConnection() {
    final AtomicReference<ConnectionState> atomicBoolean = Atomics.newReference();
    Application application = ApplicationManager.getApplication();
    application.invokeAndWait(() -> atomicBoolean.set(promptUserForProxy()), application.getAnyModalityState());
    return atomicBoolean.get();
  }

  @Nullable
  private static ConnectionState promptUserForProxy() {
    int selection = Messages.showIdeaMessageDialog(null, "Unable to access Android SDK add-on list", "Android Studio First Run",
                                                   new String[]{"Setup Proxy", "Cancel"}, 1, Messages.getErrorIcon(), null);
    if (selection == 0) {
      //noinspection ConstantConditions
      HttpConfigurable.editConfigurable(null);
      return null;
    }
    else {
      return ConnectionState.NO_CONNECTION;
    }
  }

  @NotNull
  @Override
  public WelcomeScreen createWelcomeScreen(JRootPane rootPane) {
    checkInternetConnection();
    FirstRunWizardMode wizardMode = getWizardMode();
    assert wizardMode != null; // This means isAvailable was false! Why are we even called?
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourWasShown = true;
    return new FirstRunWizardHost(wizardMode);
  }

  @Override
  public boolean isAvailable() {
    boolean isWizardDisabled = AndroidPlugin.isGuiTestingMode() || Boolean.getBoolean(SYSTEM_PROPERTY_DISABLE_WIZARD);
    return !ourWasShown && !isWizardDisabled && getWizardMode() != null;
  }

  private enum ConnectionState {
    OK, NO_CONNECTION
  }
}
