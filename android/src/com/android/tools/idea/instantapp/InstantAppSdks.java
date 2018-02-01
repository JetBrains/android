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

import com.android.instantapp.sdk.InstantAppSdkException;
import com.android.instantapp.sdk.Metadata;
import com.android.repository.api.LocalPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.android.instantapps.sdk.api.Sdk;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;

import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;

/**
 * Responsible for providing InstantApp SDK.
 * It is registered as a service so it can be easily mocked.
 */
public class InstantAppSdks {
  @NotNull private static final String INSTANT_APP_SDK_PATH = FD_EXTRAS + ";google;instantapps";
  private static final String SDK_LIB_JAR_PATH = "tools/lib.jar";

  private Sdk cachedSdkLib = null;

  @NotNull
  public static InstantAppSdks getInstance() {
    return ServiceManager.getService(InstantAppSdks.class);
  }

  @Nullable
  public File getInstantAppSdk(boolean tryToInstall) {
    LocalPackage localPackage = getInstantAppLocalPackage();
    if (localPackage == null && tryToInstall) {
      installSdkIfNeeded();
      localPackage = getInstantAppLocalPackage();
    }
    return localPackage == null ? null : localPackage.getLocation();
  }

  @Nullable
  private static LocalPackage getInstantAppLocalPackage() {
    AndroidSdkHandler androidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    return androidSdkHandler.getLocalPackage(INSTANT_APP_SDK_PATH, new StudioLoggerProgressIndicator(InstantAppSdks.class));
  }

  private static void installSdkIfNeeded() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      int result = Messages.showYesNoDialog(
        "Required Instant App SDK components not installed. Do you want to install it now?", "Instant Apps", null);
      if (result == Messages.OK) {
        ModelWizardDialog dialog = createDialogForPaths(null, ImmutableList.of(INSTANT_APP_SDK_PATH));
        if (dialog != null) {
          dialog.show();
        }
      }
    });
  }

  /**
   * Since instant app SDK is already public and available, it should be always enabled.
   * However this method can still be mocked in tests.
   */
  public boolean isInstantAppSdkEnabled() {
    return true;
  }

  public long getCompatApiMinVersion() {
    try {
      File iappSdk = getInstantAppSdk(false);
      if (iappSdk != null) {
        return Metadata.getInstance(iappSdk).getAiaCompatApiMinVersion();
      }
    }
    catch (InstantAppSdkException ex) {
      getLogger().error(ex);
    }
    return 1; // If there is any exception return the default value
  }


  public boolean shouldUseSdkLibraryToRun() {
    return StudioFlags.RUNDEBUG_USE_AIA_SDK_LIBRARY.get() && loadLibrary() != null;
  }

  /**
   * Attempts to dynamically load the Instant Apps SDK library used to provision devices and run
   * apps. Returns null if it could not be loaded.
   */
  @Nullable
  public Sdk loadLibrary() {
    if (cachedSdkLib == null) {
      try {
        File sdkRoot = getInstantAppSdk(/* tryToInstall= */ false); // TODO(tdeck): Consider setting to true and updating UI tests
        if (sdkRoot == null) {
          return null;
        }

        File jar = sdkRoot.toPath().resolve(SDK_LIB_JAR_PATH).toFile();
        if (!jar.exists()) {
          return null;
        }

        // Note that this needs to use a ClassLoader that will provide a source location for
        // classes, because the library uses its own JAR location as a reference point to find
        // binaries that it needs.
        // IMPORTANT: This class loader must NOT be closed or subsequent attempts to use library classes will fail!
        URLClassLoader childClassLoader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, getClass().getClassLoader());
        cachedSdkLib = ServiceLoader.load(Sdk.class, childClassLoader).iterator().next();
      }
      catch (IOException e) {
        getLogger().error(e);
      }
    }

    return cachedSdkLib;
  }

  private static Logger getLogger() {
    return Logger.getInstance(InstantApps.class);
  }
}
