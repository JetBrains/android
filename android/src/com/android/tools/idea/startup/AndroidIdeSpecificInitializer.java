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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.actions.AndroidNewModuleAction;
import com.android.tools.idea.actions.AndroidNewProjectAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.*;
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
  @NonNls private static final String ANDROID_SDK_FOLDER_NAME = "sdk";

  @Override
  public void run() {
    // Fix New Project actions
    //noinspection UseOfArchaicSystemPropertyAccessors
    if (Boolean.getBoolean(NEW_NEW_PROJECT_WIZARD)) {
      fixNewProjectActions();
    }

    // Setup JDK and Android SDK if necessary
    setupSdks();
  }

  private static void setupSdks() {
    Sdk sdk = getExistingSdk(AndroidSdkType.getInstance());
    if (sdk != null) {
      // already have a Android SDK (and its dependent JDK)
      ExternalAnnotationsSupport.addAnnotationsIfNecessary(sdk);
      return;
    }

    final Sdk jdk = getExistingSdk(JavaSdk.getInstance());
    final String jdkHome = jdk == null ? getJdkHome() : jdk.getHomePath();
    final String sdkHome = getAndroidSdkHome();

    if (jdkHome != null && sdkHome != null) {
      createSdks(jdk, jdkHome, sdkHome);
    } else {
      // Show a simpler dialog to add these SDK's if they can't be added automatically
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          final SelectSdkDialog dlg = new SelectSdkDialog(jdkHome, sdkHome);
          dlg.show();
          if (dlg.isOK()) {
            createSdks(jdk, dlg.getJdkHome(), dlg.getAndroidHome());
          }
        }
      }, ModalityState.any());
    }
  }

  @Nullable
  private static Sdk getExistingSdk(SdkTypeId type) {
    Sdk[] sdks = ProjectJdkTable.getInstance().getAllJdks();
    if (sdks != null) {
      for (Sdk sdk : sdks) {
        if (sdk.getSdkType() == type) {
          return sdk;
        }
      }
    }

    return null;
  }

  private static void createSdks(@Nullable Sdk jdk, @NotNull String jdkHome, @NotNull String sdkHome) {
    Sdk javaSdk = jdk != null ? jdk : createJavaSdk(jdkHome);
    if (javaSdk != null) {
      createAndroidSdk(sdkHome, javaSdk);
    }
  }

  @Nullable
  private static Sdk createAndroidSdk(@NotNull String androidHome, @NotNull Sdk javaSdk) {
    Sdk androidSdk = SdkConfigurationUtil.createAndAddSDK(androidHome, AndroidSdkType.getInstance());
    if (androidSdk == null) {
      return null;
    }

    // Attempt to insert SDK annotations
    ExternalAnnotationsSupport.addAnnotations(androidSdk);

    MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    AndroidSdkData sdkData = AndroidSdkData.parse(androidHome, log);
    if (sdkData == null) {
      return null;
    }
    IAndroidTarget[] targets = sdkData.getTargets();
    IAndroidTarget target = findBestTarget(targets);
    if (target == null) {
      return null;
    }

    AndroidSdkUtils.setUpSdk(androidSdk,
                             javaSdk,
                             new Sdk[] { javaSdk },
                             target,
                             true,
                             target.getName());
    return androidSdk;
  }

  @Nullable
  private static IAndroidTarget findBestTarget(@NotNull IAndroidTarget[] targets) {
    if (targets.length == 0) {
      return null;
    }

    IAndroidTarget bestTarget = null;
    int maxApiLevel = -1;

    for (IAndroidTarget target : targets) {
      AndroidVersion version = target.getVersion();

      if (target.isPlatform() && !version.isPreview() && version.getApiLevel() > maxApiLevel) {
        bestTarget = target;
        maxApiLevel = version.getApiLevel();
      }
    }

    return bestTarget;
  }

  /** Paths relative to the IDE installation folder where the Android SDK maybe present. */
  private static final String[] ANDROID_SDK_RELATIVE_PATHS = {
    ANDROID_SDK_FOLDER_NAME,
    File.separator + ".." + File.separator + ANDROID_SDK_FOLDER_NAME,
  };

  @Nullable
  private static String getAndroidSdkHome() {
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
  private static Sdk createJavaSdk(@NotNull String jdkHome) {
    return SdkConfigurationUtil.createAndAddSDK(jdkHome, JavaSdk.getInstance());
  }

  @Nullable
  private static String getJdkHome() {
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
