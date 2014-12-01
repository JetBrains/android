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
package com.android.tools.idea.welcome;

import com.android.sdklib.internal.repository.sources.SdkSources;
import com.android.sdklib.internal.repository.updater.SettingsController;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.sdklib.repository.remote.RemoteSdk;
import com.android.tools.idea.avdmanager.LogWrapper;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.utils.NullLogger;
import com.google.common.collect.Multimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.WelcomeScreenProvider;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Shows a wizard first time Android Studio is launched
 */
public final class AndroidStudioWelcomeScreenProvider implements WelcomeScreenProvider {
  public static final String SYSTEM_PROPERTY_DISABLE_WIZARD = "disable.android.first.run";

  private static boolean ourWasShown = false; // Do not show wizard multiple times in one session even if it was canceled

  /**
   * Analyzes system state and decides if and how the wizard should be invoked.
   *
   * @return one of the {@link FirstRunWizardMode} constants or {@code null} if wizard is not needed.
   */
  @Nullable
  public static FirstRunWizardMode getWizardMode() {
    if (AndroidFirstRunPersistentData.getInstance().isSdkUpToDate()) {
      List<Sdk> sdks = DefaultSdks.getEligibleAndroidSdks();
      if (sdks.isEmpty()) {
        return FirstRunWizardMode.MISSING_SDK;
      }
      else {
        return null;
      }
    }
    else {
      return InstallerData.get() == null ? FirstRunWizardMode.NEW_INSTALL : FirstRunWizardMode.INSTALL_HANDOFF;
    }
  }

  private static boolean isWizardDisabled() {
    return AndroidPlugin.isGuiTestingMode() || Boolean.getBoolean(SYSTEM_PROPERTY_DISABLE_WIZARD);
  }

  @Nullable
  @Override
  public WelcomeScreen createWelcomeScreen(JRootPane rootPane) {
    Multimap<PkgType, RemotePkgInfo> remotePackages = ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(new ThrowableComputable<Multimap<PkgType, RemotePkgInfo>, RuntimeException>() {
        @Override
        public Multimap<PkgType, RemotePkgInfo> compute() throws RuntimeException {
          return fetchPackages();
        }
      }, "Fetching Android SDK component information", false, null);
    FirstRunWizardMode wizardMode = getWizardMode();
    assert wizardMode != null; // This means isAvailable was false! Why are we even called?
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourWasShown = true;
    return new FirstRunWizardHost(wizardMode, remotePackages);
  }

  private Multimap<PkgType,RemotePkgInfo> fetchPackages() {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setIndeterminate(true);
    }
    RemoteSdk remoteSdk = new RemoteSdk(new SettingsController(new LogWrapper(Logger.getInstance(getClass()))));
    SdkSources sdkSources = remoteSdk.fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, new NullLogger());
    return remoteSdk.fetch(sdkSources, new NullLogger());
  }

  @Override
  public boolean isAvailable() {
    return !ourWasShown && !isWizardDisabled() && getWizardMode() != null;
  }

}
