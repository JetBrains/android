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

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.actions.AndroidNewModuleAction;
import com.android.tools.idea.actions.AndroidNewProjectAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Initialization performed only in the context of the Android IDE. */
public class AndroidIdeSpecificInitializer implements Runnable {
  @NonNls public static final String NEW_NEW_PROJECT_WIZARD = "android.newProjectWizard";
  @NonNls private static final String CONFIG_V1 = "AndroidIdeConfig.V1";
  @NonNls private static final String ANDROID_SDK_FOLDER_NAME = "sdk";

  @Override
  public void run() {
    // Fix New Project actions
    if (Boolean.getBoolean(NEW_NEW_PROJECT_WIZARD)) {
      fixNewProjectActions();
    }

    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    if (!propertiesComponent.getBoolean(CONFIG_V1, false)) {
      propertiesComponent.setValue(CONFIG_V1, "true");
      setupSDKs();
    }
  }

  // Setup JDK and Android SDK
  private void setupSDKs() {
    Sdk javaSdk = createJavaSdk();
    if (javaSdk == null) {
      return;
    }

    Sdk androidSdk = createAndroidSdk(javaSdk);
    if (androidSdk == null) {
      return;
    }

    //ApplicationManager.getApplication().invokeAndWait(new Runnable() {
    //  @Override
    //  public void run() {
    //    SelectSdkDialog dlg = new SelectSdkDialog();
    //    dlg.show();
    //  }
    //}, ModalityState.any());
  }

  @Nullable
  private Sdk createAndroidSdk(@NotNull Sdk javaSdk) {
    String androidHome = getAndroidSdk();
    if (androidHome == null) {
      return null;
    }

    Sdk androidSdk = SdkConfigurationUtil.createAndAddSDK(androidHome, AndroidSdkType.getInstance());
    if (androidSdk == null) {
      return null;
    }

    MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    AndroidSdkData sdkData = AndroidSdkData.parse(androidHome, log);
    if (sdkData == null) {
      return null;
    }
    IAndroidTarget[] targets = sdkData.getTargets();
    if (targets.length == 0) {
      return null;
    }

    IAndroidTarget target = targets[0];
    AndroidSdkUtils.setUpSdk(androidSdk,
                             javaSdk,
                             new Sdk[] { javaSdk },
                             target,
                             true,
                             target.getName());
    return androidSdk;
  }

  /** Paths relative to the IDE installation folder where the Android SDK maybe present. */
  private static final String[] ANDROID_SDK_RELATIVE_PATHS = {
    ANDROID_SDK_FOLDER_NAME,
    File.separator + ".." + File.separator + ANDROID_SDK_FOLDER_NAME,
  };

  @Nullable
  private String getAndroidSdk() {
    String ideaHome = PathManager.getHomePath();
    if (ideaHome == null) {
      return null;
    }

    for (String path : ANDROID_SDK_RELATIVE_PATHS) {
      File f = new File(ideaHome, path);
      if (f.isDirectory()) {
        return f.getAbsolutePath();
      }
    }

    return null;
  }

  @Nullable
  private static Sdk createJavaSdk() {
    String jdkHome = getBestJdk();
    return (jdkHome != null) ?
           SdkConfigurationUtil.createAndAddSDK(jdkHome, JavaSdk.getInstance()) : null;
  }

  @Nullable
  private static String getBestJdk() {
    Collection<String> jdks = JavaSdk.getInstance().suggestHomePaths();

    if (jdks.isEmpty()) {
      return null;
    }

    // search for JDKs in both the suggest folder and all its sub folders
    List<String> roots = new ArrayList<String>();
    for (String j : jdks) {
      roots.add(j);
      roots.addAll(getSubFolders(j));
    }

    return getBestJdk(roots);
  }

  @Nullable
  private static String getBestJdk(List<String> jdkRoots) {
    String bestJdk = null;

    for (String jdk : jdkRoots) {
      if (JavaSdk.getInstance().isValidSdkHome(jdk)) {
        if (bestJdk == null) {
          bestJdk = jdk;
        } else {
          bestJdk = selectJdk(bestJdk, jdk);
        }
      }
    }

    return bestJdk;
  }

  /** Prioritize JDK 1.6, otherwise pick the one with the highest version. */
  private static String selectJdk(@NotNull String jdk1, @NotNull String jdk2) {
    JavaSdkVersion v1 = getVersion(jdk1);
    if (JavaSdkVersion.JDK_1_6.equals(v1)) {
      return jdk1;
    }

    JavaSdkVersion v2 = getVersion(jdk2);
    if (JavaSdkVersion.JDK_1_6.equals(v2)) {
      return jdk2;
    }

    return v1.ordinal() > v2.ordinal() ? jdk1 : jdk2;
  }

  private static JavaSdkVersion getVersion(String jdk) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    String version = javaSdk.getVersionString(jdk);
    if (version == null) {
      return JavaSdkVersion.JDK_1_0;
    }

    JavaSdkVersion sdkVersion = javaSdk.getVersion(version);
    return sdkVersion == null ? JavaSdkVersion.JDK_1_0 : sdkVersion;
  }

  private static List<String> getSubFolders(String path) {
    File f = new File(path);
    if (!f.isDirectory()) {
      return Collections.emptyList();
    }

    File[] folders = f.listFiles(new FileFilter() {
      @Override
      public boolean accept(File p) {
        return p.isDirectory();
      }
    });

    if (folders == null) {
      return Collections.emptyList();
    }

    List<String> folderPaths = new ArrayList<String>(folders.length);
    for (File folder : folders) {
      folderPaths.add(folder.getAbsolutePath());
    }

    return folderPaths;
  }

  private static void fixNewProjectActions() {
    // TODO: This is temporary code. We should build out our own menu set and welcome screen exactly how we want. In the meantime,
    // unregister IntelliJ's version of the project actions and manually register our own.

    replaceAction("NewProject", new AndroidNewProjectAction());
    replaceAction("WelcomeScreen.CreateNewProject", new AndroidNewProjectAction());
    replaceAction("NewModule", new AndroidNewModuleAction());
  }

  private static void replaceAction(String actionId, AnAction newAction) {
    ActionManager am = ActionManager.getInstance();
    AnAction oldAction = am.getAction(actionId);
    newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
    am.unregisterAction(actionId);
    am.registerAction(actionId, newAction);
  }
}
