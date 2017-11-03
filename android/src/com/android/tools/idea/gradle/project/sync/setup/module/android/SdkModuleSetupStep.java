/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.SDK_SETUP_ISSUES;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SdkModuleSetupStep extends AndroidModuleSetupStep {
  @NotNull private final AndroidSdks myAndroidSdks;

  public SdkModuleSetupStep() {
    this(AndroidSdks.getInstance());
  }

  @VisibleForTesting
  SdkModuleSetupStep(@NotNull AndroidSdks androidSdks) {
    myAndroidSdks = androidSdks;
  }

  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull AndroidModuleModel androidModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    File androidSdkHomePath = IdeSdks.getInstance().getAndroidSdkPath();
    // Android SDK may be not configured in IntelliJ
    if (androidSdkHomePath == null) {
      assert !IdeInfo.getInstance().isAndroidStudio();
      logAndroidSdkHomeNotFound();
      return;
    }

    ModifiableRootModel moduleModel = ideModelsProvider.getModifiableRootModel(module);
    LanguageLevel languageLevel = androidModel.getJavaLanguageLevel();
    if (languageLevel != null) {
      moduleModel.getModuleExtension(LanguageLevelModuleExtensionImpl.class).setLanguageLevel(languageLevel);
    }

    AndroidProject androidProject = androidModel.getAndroidProject();
    String compileTarget = androidProject.getCompileTarget();
    Sdk sdk = myAndroidSdks.findSuitableAndroidSdk(compileTarget);
    if (sdk == null) {
      sdk = myAndroidSdks.tryToCreate(androidSdkHomePath, compileTarget);

      if (sdk == null) {
        // If SDK was not created, this might be an add-on.
        sdk = findMatchingSdkForAddon(androidProject);
      }
    }

    if (sdk == null) {
      showPlatformNotFoundError(module, compileTarget);
      return;
    }

    moduleModel.setSdk(sdk);
    String sdkPath = sdk.getHomePath();
    if (sdkPath == null) {
      sdkPath = "<path not set>";
    }
    getLog().info(String.format("Set Android SDK '%1$s' (%2$s) to module '%3$s'", sdk.getName(), sdkPath, module.getName()));
  }

  private static void logAndroidSdkHomeNotFound() {
    Logger log = getLog();
    log.warn("Path to Android SDK not set");

    List<Sdk> sdks = IdeSdks.getInstance().getEligibleAndroidSdks();
    log.warn("# of eligible SDKs: " + sdks.size());
    for (Sdk sdk : sdks) {
      log.info("sdk: " + sdk.toString());
    }
  }

  private static void showPlatformNotFoundError(@NotNull Module module, @NotNull String compileTarget) {
    String text = String.format("Module '%1$s': platform '%2$s' not found.", module.getName(), compileTarget);
    getLog().warn(text);

    SyncMessage msg = new SyncMessage(SDK_SETUP_ISSUES, ERROR, text);
    GradleSyncMessages.getInstance(module.getProject()).report(msg);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(SdkModuleSetupStep.class);
  }

  @Nullable
  private Sdk findMatchingSdkForAddon(@NotNull AndroidProject androidProject) {
    Collection<String> bootClasspath = androidProject.getBootClasspath();
    if (bootClasspath.size() > 1) {
      File androidJarPath = findAndroidJarFilePath(bootClasspath);
      if (androidJarPath != null) {
        // We only know the path of android.jar. Now we are going to find an existing Android SDK that uses the same android.jar.
        // If the boot classpath has more than 1 element (something besides android.jar), we are dealing with an add-on.
        // This is an add-on
        return findSdk(androidJarPath);
      }
    }
    return null;
  }

  @Nullable
  private static File findAndroidJarFilePath(@NotNull Collection<String> bootClasspath) {
    for (String entry : bootClasspath) {
      File file = new File(entry);
      if (FN_FRAMEWORK_LIBRARY.equals(file.getName())) {
        return file;
      }
    }
    return null;
  }

  @Nullable
  private Sdk findSdk(@NotNull File androidJarPath) {
    for (Sdk sdk : myAndroidSdks.getAllAndroidSdks()) {
      if (containsPath(sdk, androidJarPath)) {
        return sdk;
      }
    }
    return null;
  }

  private static boolean containsPath(@NotNull Sdk sdk, @NotNull File path) {
    for (VirtualFile sdkFile : sdk.getRootProvider().getFiles(CLASSES)) {
      // We need to convert the VirtualFile to java.io.File, because the path of the VirtualPath is using 'jar' protocol and it won't
      // match the path returned by AndroidProject#getBootClasspath().
      File sdkFilePath = virtualToIoFile(sdkFile);
      if (filesEqual(sdkFilePath, path)) {
        return true;
      }
    }
    return false;
  }
}
