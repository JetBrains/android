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

import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.android.sdk.AndroidSdkUtils.DEFAULT_JDK_NAME;
import static org.jetbrains.android.sdk.AndroidSdkUtils.createNewAndroidPlatform;
import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdkManagerEnabled;

import com.android.SdkConstants;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.analytics.SystemInfoStatsMonitor;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.install.patch.PatchInstallingRestarter;
import com.android.tools.idea.ui.GuiTestingService;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.wizard.AndroidStudioWelcomeScreenProvider;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSdkInitializer implements Runnable {
  private static final Logger LOG = Logger.getInstance(AndroidSdkInitializer.class);

  // Paths relative to the IDE installation folder where the Android SDK may be present.
  @NonNls private static final String ANDROID_SDK_FOLDER_NAME = "sdk";
  private static final String[] ANDROID_SDK_RELATIVE_PATHS = {
    ANDROID_SDK_FOLDER_NAME,
    File.separator + ".." + File.separator + ANDROID_SDK_FOLDER_NAME
  };
  // Default install location from users home dir.
  @NonNls private static String getAndroidSdkDefaultInstallDir() {
    return SystemInfo.isWindows ? FileUtil.join(System.getenv("LOCALAPPDATA"), "Android", "Sdk")
                                : SystemInfo.isMac ? FileUtil.join(SystemProperties.getUserHome(), "Library", "Android", "sdk")
                                                   : FileUtil.join(SystemProperties.getUserHome(), "Android", "Sdk");
  }

  @Override
  public void run() {
    if (!isAndroidSdkManagerEnabled()) {
      return;
    }

    // If running in a GUI test we don't want the "Select SDK" dialog to show up when running GUI tests.
    // In unit tests, we only want to set up SDKs which are set up explicitly by the test itself, whereas initialisers
    // might lead to unexpected SDK leaks because having not set up the SDKs, the test will consequently not release them either.
    if (GuiTestingService.getInstance().isGuiTestingMode() || ApplicationManager.getApplication().isUnitTestMode()
        || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      // This is good enough. Later on in the GUI test we'll validate the given SDK path.
      return;
    }

    IdeSdks ideSdks = IdeSdks.getInstance();
    File androidSdkPath = ideSdks.getAndroidSdkPath();
    if (androidSdkPath == null) {
      try {
        // Setup JDK and Android SDK if necessary
        setUpSdks();
        androidSdkPath = ideSdks.getAndroidSdkPath();
      }
      catch (Exception e) {
        LOG.error("Unexpected error while setting up SDKs: ", e);
      }
    }

    if (androidSdkPath != null) {
      int androidPlatformToAutocreate = StudioFlags.ANDROID_PLATFORM_TO_AUTOCREATE.get();
      if (androidPlatformToAutocreate != 0) {
        LOG.info(
          String.format(Locale.US, "Automatically creating an Android platform using SDK path==%s and SDK version==%d", androidSdkPath,
                        androidPlatformToAutocreate));
        AndroidSdkUtils.createNewAndroidPlatform(androidSdkPath.toString(), false);
      }

      AndroidSdkHandler handler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, androidSdkPath.toPath());
      new PatchInstallingRestarter(handler).restartAndInstallIfNecessary();
      // We need to start the system info monitoring even in case when user never
      // runs a single emulator instance: e.g., incompatible hypervisor might be
      // the reason why emulator is never run, and that's exactly the data
      // SystemInfoStatsMonitor collects
      new SystemInfoStatsMonitor().start();
    }
  }

  private static void setUpSdks() {
    Sdk sdk = findFirstAndroidSdk();
    if (sdk != null) {
      String sdkHomePath = sdk.getHomePath();
      assert sdkHomePath != null;
      IdeSdks.getInstance().createAndroidSdkPerAndroidTarget(FilePaths.stringToFile(sdkHomePath));
      return;
    }

    // Called in a 'invokeLater' block, otherwise file chooser will hang forever.
    ApplicationManager.getApplication().invokeLater(() -> {
      File androidSdkPath = findValidAndroidSdkPath();
      if (androidSdkPath == null) {
        return;
      }

      FirstRunWizardMode wizardMode = AndroidStudioWelcomeScreenProvider.getWizardMode();
      // Only show "Select SDK" dialog if the "First Run" wizard is not displayed.
      boolean promptSdkSelection = wizardMode == null;

      Sdk newSdk = createNewAndroidPlatform(androidSdkPath.getPath(), promptSdkSelection);
      if (newSdk != null) {
        // Rename the SDK to fit our default naming convention.
        String sdkNamePrefix = AndroidSdks.SDK_NAME_PREFIX;
        if (newSdk.getName().startsWith(sdkNamePrefix)) {
          SdkModificator sdkModificator = newSdk.getSdkModificator();
          sdkModificator.setName(sdkNamePrefix + newSdk.getName().substring(sdkNamePrefix.length()));
          sdkModificator.commitChanges();

          // Rename the JDK that goes along with this SDK.
          AndroidSdkAdditionalData additionalData = AndroidSdks.getInstance().getAndroidSdkAdditionalData(newSdk);
          if (additionalData != null) {
            Sdk jdk = additionalData.getJavaSdk();
            if (jdk != null) {
              sdkModificator = jdk.getSdkModificator();
              sdkModificator.setName(DEFAULT_JDK_NAME);
              sdkModificator.commitChanges();
            }
          }

          // Fill out any missing build APIs for this new SDK.
          IdeSdks.getInstance().createAndroidSdkPerAndroidTarget(androidSdkPath);
        }
      }
    });
  }

  @Nullable
  private static Sdk findFirstAndroidSdk() {
    List<Sdk> sdks = AndroidSdks.getInstance().getAllAndroidSdks();
    return !sdks.isEmpty() ? sdks.get(0) : null;
  }

  @Nullable
  public static File findValidAndroidSdkPath() {
    File candidate = getAndroidSdkPathOrDefault();
    return AndroidSdkType.getInstance().isValidSdkHome(candidate.getPath()) ? candidate : null;
  }

  /**
   * Tries to find a path to an Android SDK. Looks in:
   * <p><ul>
   * <li>ANDROID_HOME_ENV</li>
   * <li>ANDROID_SDK_ROOT_ENV</li>
   * <li>the platform-specific default path</li>
   * </ul></p>
   *
   * @return The path to the SDK, or the default SDK path if none is found.
   */
  @NotNull
  public static File getAndroidSdkPathOrDefault() {
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
    return getAndroidSdkOrDefault(System.getenv(), AndroidSdkType.getInstance());
  }

  @NotNull
  private static File getAndroidSdkOrDefault(Map<String, String> env, AndroidSdkType instance) {
    return getAndroidSdkOrDefault(env, instance, IdeInfo.getInstance());
  }

  @VisibleForTesting
  @NotNull
  static File getAndroidSdkOrDefault(Map<String, String> env, AndroidSdkType instance, IdeInfo ideInfo) {
    // The order of insertion matters as it defines SDK locations precedence.
    Map<String, Callable<String>> sdkLocationCandidates = new LinkedHashMap<>();
    sdkLocationCandidates.put(SdkConstants.ANDROID_HOME_ENV + " environment variable",
                              () -> env.get(SdkConstants.ANDROID_HOME_ENV));
    sdkLocationCandidates.put(SdkConstants.ANDROID_SDK_ROOT_ENV + " environment variable",
                              () -> env.get(SdkConstants.ANDROID_SDK_ROOT_ENV));

    String sdkPath;
    for (Map.Entry<String, Callable<String>> locationCandidate : sdkLocationCandidates.entrySet()) {
      try {
        String pathDescription = locationCandidate.getKey();
        sdkPath = locationCandidate.getValue().call();
        String msg;
        if (!isEmpty(sdkPath) && (instance.isValidSdkHome(sdkPath) || ideInfo.isGameTools())) {
          // Game Tools doesn't need the path to contain a valid SDK; it also accepts
          // non-existing/empty directories so that the user can set up SDK from scratch at
          // a directory of their choice.
          msg = String.format("%1$s: '%2$s'", pathDescription, sdkPath);
        }
        else {
          msg = String.format("Examined and not found a valid Android SDK path: %1$s", pathDescription);
          sdkPath = null;
        }
        LOG.info(msg);
        if (sdkPath != null) {
          return FilePaths.stringToFile(sdkPath);
        }
      }
      catch (Exception e) {
        LOG.info("Exception during SDK lookup", e);
      }
    }

    String defaultDir = getAndroidSdkDefaultInstallDir();
    LOG.info("Using default SDK path: " + defaultDir);
    return FilePaths.stringToFile(defaultDir);
  }
}
