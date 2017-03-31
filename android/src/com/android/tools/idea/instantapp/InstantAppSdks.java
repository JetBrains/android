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

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.repository.api.RepoPackage.PATH_SEPARATOR;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;

/**
 * Responsible for providing InstantApp SDK.
 * It is registered as a service so it can be easily mocked.
 */
public class InstantAppSdks {
  @NotNull private static final String INSTANT_APP_SDK_ENV_VAR = "WH_SDK";
  @NotNull private static final String INSTANT_APP_SDK_PATH = FD_EXTRAS + ";google;instantapps";
  @NotNull public static final String INSTANT_APP_SDK_PROPERTY = "android.instant_app_sdk_location";

  @Nullable private File mySdk = null;
  @Nullable private Boolean myInstantAppSdkAvailable = null;

  @NotNull
  public static InstantAppSdks getInstance() {
    return ServiceManager.getService(InstantAppSdks.class);
  }

  @Nullable
  public File getInstantAppSdk(boolean tryToInstall) {
    if (mySdk != null && mySdk.exists() && mySdk.isDirectory()) {
      return mySdk;
    }

    // Try to get SDK through WH_SDK environment variable
    mySdk = validateSdk(System.getenv(INSTANT_APP_SDK_ENV_VAR));

    if (mySdk == null) {
      // Try to get SDK through defined property
      mySdk = validateSdk(System.getProperty(INSTANT_APP_SDK_PROPERTY));
    }

    if (mySdk == null) {
      File androidSdk = IdeSdks.getInstance().getAndroidSdkPath();
      if (androidSdk != null) {
        mySdk = validateSdk(androidSdk.getAbsolutePath() + ("/" + INSTANT_APP_SDK_PATH).replace(PATH_SEPARATOR, File.separatorChar));
        if (tryToInstall && mySdk == null) {
          // Try to get SDK through SDK manager
          installSdkIfNeeded();
          mySdk = validateSdk(androidSdk.getAbsolutePath() + ("/" + INSTANT_APP_SDK_PATH).replace(PATH_SEPARATOR, File.separatorChar));
        }
      }
    }

    return mySdk;
  }

  private static void installSdkIfNeeded() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      int result = Messages.showYesNoDialog("Required Instant App SDK components not installed. Do you want to install it now?", "Instant Apps", null);
      if (result == Messages.OK) {
        ModelWizardDialog dialog = createDialogForPaths(null, ImmutableList.of(INSTANT_APP_SDK_PATH));
        if (dialog != null) {
          dialog.show();
        }
      }
    });
  }

  @Nullable
  private static File validateSdk(String sdk) {
    if (StringUtil.isEmpty(sdk)) {
      return null;
    }
    File sdkFile = new File(sdk);
    if (!sdkFile.exists() || !sdkFile.isDirectory()) {
      return null;
    }
    return sdkFile;
  }

  // Used to verify if the user has access to an Instant App SDK. This is temporary and should be deleted once the SDK becomes public.
  // If we use #getInstantAppSdk to verify if it's available, the user will be showed a dialog each time, which we don't want during startup, for example.
  public boolean isInstantAppSdkEnabled() {
    if (myInstantAppSdkAvailable == null) {
      myInstantAppSdkAvailable = false;
      String sdkUrl = System.getenv("SDK_TEST_BASE_URL");
      if (getInstantAppSdk(false) != null) {
        myInstantAppSdkAvailable = true;
      }
      else if (StringUtil.isNotEmpty(sdkUrl)) {
        try {
          File sdkRepo = new File(new URI(sdkUrl));
          if (sdkRepo.exists() && sdkRepo.isDirectory()) {
            String[] children = sdkRepo.list();
            if (children != null) {
              for (String child : children) {
                if (child.startsWith("whsdk")) {
                  myInstantAppSdkAvailable = true;
                }
              }
            }
          }
        }
        catch (URISyntaxException e) {
          // Ignore
        }
      }
    }
    return myInstantAppSdkAvailable;
  }
}
