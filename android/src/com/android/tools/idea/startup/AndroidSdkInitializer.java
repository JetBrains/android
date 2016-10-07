/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.startup;

import com.android.SdkConstants;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.SystemInfoStatsMonitor;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.install.patch.PatchInstallingRestarter;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.wizard.AndroidStudioWelcomeScreenProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.List;
import java.util.Properties;

import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.android.tools.idea.sdk.VersionCheck.isCompatibleVersion;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidSdkManagerEnabled;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.android.AndroidPlugin.isGuiTestingMode;
import static org.jetbrains.android.sdk.AndroidSdkUtils.*;

public class AndroidSdkInitializer implements Runnable {
  private static final Logger LOG = Logger.getInstance(AndroidSdkInitializer.class);

  // Paths relative to the IDE installation folder where the Android SDK may be present.
  @NonNls private static final String ANDROID_SDK_FOLDER_NAME = "sdk";
  private static final String[] ANDROID_SDK_RELATIVE_PATHS = {
    ANDROID_SDK_FOLDER_NAME,
    File.separator + ".." + File.separator + ANDROID_SDK_FOLDER_NAME
  };

  @Override
  public void run() {
    if (!isAndroidSdkManagerEnabled()) {
      return;
    }

    // If running in a GUI test we don't want the "Select SDK" dialog to show up when running GUI tests.
    if (isGuiTestingMode()) {
      // This is good enough. Later on in the GUI test we'll validate the given SDK path.
      return;
    }

    File androidSdkPath = IdeSdks.getAndroidSdkPath();
    if (androidSdkPath == null) {
      try {
        // Setup JDK and Android SDK if necessary
        setUpSdks();
        androidSdkPath = IdeSdks.getAndroidSdkPath();
      }
      catch (Exception e) {
        LOG.error("Unexpected error while setting up SDKs: ", e);
      }
    }

    if (androidSdkPath != null) {
      AndroidSdkHandler handler = AndroidSdkHandler.getInstance(androidSdkPath);
      new PatchInstallingRestarter(handler, FileOpUtils.create()).restartAndInstallIfNecessary();
      // We need to start the system info monitoring even in case when user never
      // runs a single emulator instance: e.g., incompatible hypervisor might be
      // the reason why emulator is never run, and that's exactly the data
      // SystemInfoStatsMonitor collects
      new SystemInfoStatsMonitor().start();
    }
  }

  private static void setUpSdks() {
    Sdk sdk = findFirstCompatibleAndroidSdk();
    if (sdk != null) {
      String sdkHomePath = sdk.getHomePath();
      assert sdkHomePath != null;
      IdeSdks.createAndroidSdkPerAndroidTarget(new File(toSystemDependentName(sdkHomePath)));
      return;
    }

    // Called in a 'invokeLater' block, otherwise file chooser will hang forever.
    ApplicationManager.getApplication().invokeLater(() -> {
      File androidSdkPath = findOrGetAndroidSdkPath();
      if (androidSdkPath == null) {
        return;
      }

      FirstRunWizardMode wizardMode = AndroidStudioWelcomeScreenProvider.getWizardMode();
      // Only show "Select SDK" dialog if the "First Run" wizard is not displayed.
      boolean promptSdkSelection = wizardMode == null;

      Sdk newSdk = createNewAndroidPlatform(androidSdkPath.getPath(), promptSdkSelection);
      if (newSdk != null) {
        // Rename the SDK to fit our default naming convention.
        if (newSdk.getName().startsWith(SDK_NAME_PREFIX)) {
          SdkModificator sdkModificator = newSdk.getSdkModificator();
          sdkModificator.setName(SDK_NAME_PREFIX + newSdk.getName().substring(SDK_NAME_PREFIX.length()));
          sdkModificator.commitChanges();

          // Rename the JDK that goes along with this SDK.
          AndroidSdkAdditionalData additionalData = getAndroidSdkAdditionalData(newSdk);
          if (additionalData != null) {
            Sdk jdk = additionalData.getJavaSdk();
            if (jdk != null) {
              sdkModificator = jdk.getSdkModificator();
              sdkModificator.setName(DEFAULT_JDK_NAME);
              sdkModificator.commitChanges();
            }
          }

          // Fill out any missing build APIs for this new SDK.
          IdeSdks.createAndroidSdkPerAndroidTarget(androidSdkPath);
        }
      }
    });
  }

  @Nullable
  private static Sdk findFirstCompatibleAndroidSdk() {
    List<Sdk> sdks = getAllAndroidSdks();
    for (Sdk sdk : sdks) {
      String sdkPath = sdk.getHomePath();
      if (isCompatibleVersion(sdkPath)) {
        return sdk;
      }
    }
    return !sdks.isEmpty() ? sdks.get(0) : null;
  }

  @Nullable
  private static File findOrGetAndroidSdkPath() {
    String studioHome = PathManager.getHomePath();
    if (isEmpty(studioHome)) {
      LOG.info("Unable to find Studio home directory");
    }
    else {
      LOG.info(String.format("Found Studio home directory at: '%1$s'", studioHome));
      for (String path : ANDROID_SDK_RELATIVE_PATHS) {
        File dir = new File(studioHome, path);
        String absolutePath = toCanonicalPath(dir.getAbsolutePath());
        LOG.info(String.format("Looking for Android SDK at '%1$s'", absolutePath));
        if (AndroidSdkType.getInstance().isValidSdkHome(absolutePath)) {
          LOG.info(String.format("Found Android SDK at '%1$s'", absolutePath));
          return new File(absolutePath);
        }
      }
    }
    LOG.info("Unable to locate SDK within the Android studio installation.");

    String androidHomeValue = System.getenv(SdkConstants.ANDROID_HOME_ENV);
    String msg = String.format("Checking if ANDROID_HOME is set: '%1$s' is '%2$s'", SdkConstants.ANDROID_HOME_ENV, androidHomeValue);
    LOG.info(msg);

    if (!isEmpty(androidHomeValue) && AndroidSdkType.getInstance().isValidSdkHome(androidHomeValue)) {
      LOG.info("Using Android SDK specified by the environment variable.");
      return new File(toSystemDependentName(androidHomeValue));
    }

    String sdkPath = getLastSdkPathUsedByAndroidTools();
    if (!isEmpty(sdkPath) && AndroidSdkType.getInstance().isValidSdkHome(sdkPath)) {
      msg = String.format("Last SDK used by Android tools: '%1$s'", sdkPath);
    } else {
      msg = "Unable to locate last SDK used by Android tools";
    }
    LOG.info(msg);
    return sdkPath != null ? new File(toSystemDependentName(sdkPath)) : null;
  }

  /**
   * Returns the value for property 'lastSdkPath' as stored in the properties file at $HOME/.android/ddms.cfg, or {@code null} if the file
   * or property doesn't exist.
   *
   * This is only useful in a scenario where existing users of ADT/Eclipse get Studio, but without the bundle. This method duplicates some
   * functionality of {@link com.android.prefs.AndroidLocation} since we don't want any file system writes to happen during this process.
   */
  @Nullable
  private static String getLastSdkPathUsedByAndroidTools() {
    String userHome = SystemProperties.getUserHome();
    if (userHome == null) {
      return null;
    }
    File file = new File(new File(userHome, ".android"), "ddms.cfg");
    if (!file.exists()) {
      return null;
    }
    try {
      Properties properties = getProperties(file);
      return properties.getProperty("lastSdkPath");
    } catch (IOException e) {
      return null;
    }
  }
}
