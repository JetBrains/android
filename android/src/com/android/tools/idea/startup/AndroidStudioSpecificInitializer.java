/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.actions.AndroidNewModuleAction;
import com.android.tools.idea.actions.AndroidNewModuleInGroupAction;
import com.android.tools.idea.actions.AndroidNewProjectAction;
import com.android.tools.idea.sdk.VersionCheck;
import com.google.common.io.Closeables;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** Initialization performed only in the context of the Android IDE. */
public class AndroidStudioSpecificInitializer implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.startup.AndroidStudioSpecificInitializer");

  @NonNls public static final String NEW_NEW_PROJECT_WIZARD = "android.newProjectWizard";

  @NonNls private static final String ANDROID_SDK_FOLDER_NAME = "sdk";

  /** Paths relative to the IDE installation folder where the Android SDK maybe present. */
  private static final String[] ANDROID_SDK_RELATIVE_PATHS =
    { ANDROID_SDK_FOLDER_NAME, File.separator + ".." + File.separator + ANDROID_SDK_FOLDER_NAME,};

  @Override
  public void run() {
    // Fix New Project actions
    //noinspection UseOfArchaicSystemPropertyAccessors
    if (System.getProperty(NEW_NEW_PROJECT_WIZARD) == null || Boolean.getBoolean(NEW_NEW_PROJECT_WIZARD)) {
      fixNewProjectActions();
    }

    try {
      // Setup JDK and Android SDK if necessary
      setupSdks();
    } catch (Exception e) {
      LOG.error("Unexpected error while setting up SDKs: ", e);
    }

    // Always reset the Default scheme to match Android standards
    // User modifications won't be lost since they are made in a separate scheme (copied off of this default scheme)
    CodeStyleScheme scheme = CodeStyleSchemes.getInstance().getDefaultScheme();
    if (scheme != null) {
      CodeStyleSettings settings = scheme.getCodeStyleSettings();
      if (settings != null) {
        AndroidCodeStyleSettingsModifier.modify(settings);
      }
    }
  }

  private static void fixNewProjectActions() {
    // TODO: This is temporary code. We should build out our own menu set and welcome screen exactly how we want. In the meantime,
    // unregister IntelliJ's version of the project actions and manually register our own.

    replaceAction("NewProject", new AndroidNewProjectAction());
    replaceAction("WelcomeScreen.CreateNewProject", new AndroidNewProjectAction());
    replaceAction("NewModule", new AndroidNewModuleAction());
    replaceAction("NewModuleInGroup", new AndroidNewModuleInGroupAction());
  }

  private static void replaceAction(String actionId, AnAction newAction) {
    ActionManager am = ActionManager.getInstance();
    AnAction oldAction = am.getAction(actionId);
    newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
    am.unregisterAction(actionId);
    am.registerAction(actionId, newAction);
  }

  private static void setupSdks() {
    Sdk sdk = findFirstCompatibleAndroidSdk();
    if (sdk != null) {
      return;
    }
    // Called in a 'invokeLater' block, otherwise file chooser will hang forever.
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        String androidSdkPath = getAndroidSdkPath();
        AndroidSdkUtils.createNewAndroidPlatform(androidSdkPath);
      }
    });
  }

  @Nullable
  private static Sdk findFirstCompatibleAndroidSdk() {
    for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
      String sdkPath = sdk.getHomePath();
      if (VersionCheck.isCompatibleVersion(sdkPath)) {
        return sdk;
      }
    }
    return null;
  }

  @Nullable
  private static String getAndroidSdkPath() {
    String studioHome = PathManager.getHomePath();
    if (studioHome == null) {
      LOG.info("Unable to find Studio home directory");
    }
    else {
      LOG.info(String.format("Found Studio home directory at: '1$%s'", studioHome));
      for (String path : ANDROID_SDK_RELATIVE_PATHS) {
        File dir = new File(studioHome, path);
        String absolutePath = dir.getAbsolutePath();
        LOG.info(String.format("Looking for Android SDK at '1$%s'", absolutePath));
        if (AndroidSdkType.getInstance().isValidSdkHome(absolutePath) && VersionCheck.isCompatibleVersion(dir)) {
          LOG.info(String.format("Found Android SDK at '1$%s'", absolutePath));
          return absolutePath;
        }
      }
    }
    LOG.info("Unable to locate SDK within the Android studio installation.");

    String androidHomeValue = System.getenv(AndroidSdkUtils.ANDROID_HOME_ENV);
    String msg = String.format("Checking if ANDROID_HOME is set: '%1$s' is '%2$s'", AndroidSdkUtils.ANDROID_HOME_ENV, androidHomeValue);
    LOG.info(msg);

    if (!StringUtil.isEmpty(androidHomeValue) &&
        AndroidSdkType.getInstance().isValidSdkHome(androidHomeValue) &&
        VersionCheck.isCompatibleVersion(androidHomeValue)) {
      LOG.info("Using Android SDK specified by the environment variable.");
      return androidHomeValue;
    }

    String sdkPath = getLastSdkPathUsedByAndroidTools();
    if (!StringUtil.isEmpty(sdkPath) &&
        AndroidSdkType.getInstance().isValidSdkHome(androidHomeValue) &&
        VersionCheck.isCompatibleVersion(sdkPath)) {
      msg = String.format("Last SDK used by Android tools: '%1$s'", sdkPath);
    } else {
      msg = "Unable to locate last SDK used by Android tools";
    }
    LOG.info(msg);
    return sdkPath;
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
    File f = new File(new File(userHome, ".android"), "ddms.cfg");
    if (!f.exists()) {
      return null;
    }
    Properties properties = new Properties();
    FileInputStream fis = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      fis = new FileInputStream(f);
      properties.load(fis);
    } catch (IOException e) {
      return null;
    } finally {
      Closeables.closeQuietly(fis);
    }
    return properties.getProperty("lastSdkPath");
  }
}
