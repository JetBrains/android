/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp;

import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;

import com.android.repository.api.LocalPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.android.instantapps.sdk.api.ExtendedSdk;
import com.google.android.instantapps.sdk.api.SdkLoader;
import com.google.android.instantapps.sdk.api.TelemetryManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import java.io.File;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Responsible for providing InstantApp SDK.
 * It is registered as a service so it can be easily mocked.
 */
public class InstantAppSdks {
  @NotNull private static final String INSTANT_APP_SDK_PATH = FD_EXTRAS + ";google;instantapps";
  private static final String SDK_LIB_JAR_PATH = "tools/lib.jar";

  @VisibleForTesting static final String UPGRADE_PROMPT_TEXT =
    "Required Google Play Instant SDK must be updated to run this task. Do you want to update it now?";
  @VisibleForTesting static final LoadInstantAppSdkException COULD_NOT_LOAD_NEW_SDK_EXCEPTION =
    new LoadInstantAppSdkException("Could not load required version of the Google Play Instant SDK");

  private ExtendedSdk cachedSdkLib = null;

  @NotNull
  public static InstantAppSdks getInstance() {
    return ApplicationManager.getApplication().getService(InstantAppSdks.class);
  }

  /**
   * Attempts to load the Google Play Instant SDK.
   *
   * If the SDK is missing, it will trigger an install. Failing that, it will throw an exception.
   */
  @NotNull
  public Path getOrInstallInstantAppSdk() {
    LocalPackage localPackage = getInstantAppLocalPackage();
    if (localPackage == null) {
      return ensureSdkInstalled().getLocation();
    }
    return localPackage.getLocation();
  }

  @Nullable
  private static LocalPackage getInstantAppLocalPackage() {
    AndroidSdkHandler androidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    return androidSdkHandler.getLocalPackage(INSTANT_APP_SDK_PATH, new StudioLoggerProgressIndicator(InstantAppSdks.class));
  }

  private static @NotNull LocalPackage ensureSdkInstalled() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (MessageDialogBuilder.yesNo("Google Play Instant", "Required Google Play Instant SDK not installed. Do you want to install it now?").show() == Messages.YES) {
        ModelWizardDialog dialog = createDialogForPaths(null, ImmutableList.of(INSTANT_APP_SDK_PATH));
        if (dialog != null) {
          dialog.show();
        }
      }
    });

    LocalPackage localPackage = getInstantAppLocalPackage();
    if (localPackage == null) {
      throw new LoadInstantAppSdkException("Could not load the Google Play Instant SDK");
    } else {
      return localPackage;
    }
  }

  private static void updateSdk() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      int result = MessageDialogBuilder.yesNo("Google Play Instant", UPGRADE_PROMPT_TEXT).show();
      if (result == Messages.OK) {
        ModelWizardDialog dialog = createDialogForPaths(null, ImmutableList.of(INSTANT_APP_SDK_PATH));
        if (dialog != null) {
          dialog.show();
        }
      }
    });
  }

  /**
   * Attempts to dynamically load the Instant Apps SDK library used to provision devices and run
   * apps. Returns null if it could not be loaded.
   */
  @NotNull
  public ExtendedSdk loadLibrary() {
    return loadLibrary(true);
  }

  @NotNull
  @VisibleForTesting
  ExtendedSdk loadLibrary(boolean attemptUpgrades) {
    if (cachedSdkLib == null) {
      Path sdkRoot = getOrInstallInstantAppSdk();

      File jar = sdkRoot.resolve(SDK_LIB_JAR_PATH).toFile();

      if (!jar.exists()) {
        // This SDK is too old and is lacking the library JAR
        if (attemptUpgrades) {
          updateSdk();
          return loadLibrary(false);
        }
        else {
          throw COULD_NOT_LOAD_NEW_SDK_EXCEPTION;
        }
      }

      cachedSdkLib = new SdkLoader().loadSdk(
        jar,
        TelemetryManager.HostApplication.ANDROID_STUDIO, ApplicationInfo.getInstance().getFullVersion());
      if (cachedSdkLib == null) {
        if (attemptUpgrades) {
          // This SDK contains a library JAR that's too old
          updateSdk();
          return loadLibrary(false);
        }
        else {
          throw COULD_NOT_LOAD_NEW_SDK_EXCEPTION;
        }
      }
    }

    // We want to set this every time the SDK is used to keep it up to date
    cachedSdkLib.getTelemetryManager().setOptInStatus(AnalyticsSettings.getOptedIn()
                                                      ? TelemetryManager.OptInStatus.OPTED_IN
                                                      : TelemetryManager.OptInStatus.OPTED_OUT);

    return cachedSdkLib;
  }

  public static class LoadInstantAppSdkException extends RuntimeException {
    public LoadInstantAppSdkException(@NotNull String message) {
      super(message);
    }
  }
}
