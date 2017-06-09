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
package com.android.tools.idea.gradle.project.sync.setup.post.cleanup;

import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallPlatformHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanupStep;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.FN_ANNOTATIONS_JAR;
import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.startup.ExternalAnnotationsSupport.attachJdkAnnotations;
import static com.intellij.openapi.application.ModalityState.NON_MODAL;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static org.jetbrains.android.sdk.AndroidSdkType.DEFAULT_EXTERNAL_DOCUMENTATION_PATH;

public class SdksCleanupStep extends ProjectCleanupStep {
  @NotNull private final AndroidSdks myAndroidSdks;

  public SdksCleanupStep(@NotNull AndroidSdks androidSdks) {
    myAndroidSdks = androidSdks;
  }

  @Override
  public void cleanUpProject(@NotNull Project project,
                             @NotNull IdeModifiableModelsProvider ideModifiableModelsProvider,
                             @Nullable ProgressIndicator indicator) {
    Set<Sdk> fixedSdks = new HashSet<>();
    Set<Sdk> invalidSdks = new HashSet<>();
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      cleanUpSdk(module, fixedSdks, invalidSdks);
    }

    if (!invalidSdks.isEmpty()) {
      reinstallMissingPlatforms(invalidSdks, project);
    }
  }

  @VisibleForTesting
  void cleanUpSdk(@NotNull Module module, @NotNull Set<Sdk> fixedSdks, @NotNull Set<Sdk> invalidSdks) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null || androidFacet.getAndroidModel() == null) {
      return;
    }
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null && !invalidSdks.contains(sdk) && !fixedSdks.contains(sdk)) {
      AndroidSdkAdditionalData additionalData = myAndroidSdks.getAndroidSdkAdditionalData(sdk);
      AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);
      if (additionalData != null && sdkData != null) {
        IAndroidTarget target = additionalData.getBuildTarget(sdkData);
        if (target == null) {
          AndroidSdkHandler sdkHandler = sdkData.getSdkHandler();
          StudioLoggerProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
          sdkHandler.getSdkManager(logger).loadSynchronously(0, logger, null, null);
          target = sdkHandler.getAndroidTargetManager(logger).getTargetFromHashString(additionalData.getBuildTargetHashString(), logger);
        }
        if (target != null) {
          SdkModificator sdkModificator = null;
          if (isMissingAndroidLibrary(sdk) || shouldRemoveAnnotationsJar(sdk)) {
            // First try to recreate SDK; workaround for issue 78072
            sdkModificator = sdk.getSdkModificator();
            sdkModificator.removeAllRoots();
            for (OrderRoot orderRoot : myAndroidSdks.getLibraryRootsForTarget(target, toSystemDependentPath(sdk.getHomePath()), true)) {
              sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
            }
            attachJdkAnnotations(sdkModificator);
          }
          if (!myAndroidSdks.hasValidDocs(sdk, target)) {
            if (sdkModificator == null) {
              sdkModificator = sdk.getSdkModificator();
            }
            VirtualFile documentationPath = HttpFileSystem.getInstance().findFileByPath(DEFAULT_EXTERNAL_DOCUMENTATION_PATH);
            sdkModificator.addRoot(documentationPath, JavadocOrderRootType.getInstance());
          }
          if (!hasSources(sdk)) {
            // See https://code.google.com/p/android/issues/detail?id=233392
            if (sdkModificator == null) {
              sdkModificator = sdk.getSdkModificator();
            }
            myAndroidSdks.findAndSetPlatformSources(target, sdkModificator);
          }
          if (sdkModificator != null) {
            ApplicationManager.getApplication().invokeAndWait(sdkModificator::commitChanges);
            fixedSdks.add(sdk);
          }
        }
      }

      // If attempting to fix up the roots in the SDK fails, install the target over again
      // (this is a truly corrupt install, as opposed to an incorrectly synced SDK which the
      // above workaround deals with)
      if (isMissingAndroidLibrary(sdk)) {
        invalidSdks.add(sdk);
      }
    }
  }

  private boolean isMissingAndroidLibrary(@NotNull Sdk sdk) {
    if (myAndroidSdks.isAndroidSdk(sdk)) {
      for (VirtualFile library : sdk.getRootProvider().getFiles(CLASSES)) {
        // This code does not through the classes in the Android SDK. It iterates through a list of 3 files in the IDEA SDK: android.jar,
        // annotations.jar and res folder.
        if (library.getName().equals(FN_FRAMEWORK_LIBRARY) && library.exists()) {
          return false;
        }
      }
    }
    return true;
  }

  /*
   * Indicates whether annotations.jar should be removed from the given SDK (if it is an Android SDK.)
   * There are 2 issues:
   * 1. annotations.jar is not needed for API level 16 and above. The annotations are already included in android.jar. Until recently, the
   *    IDE added annotations.jar to the IDEA Android SDK definition unconditionally.
   * 2. Because annotations.jar is in the classpath, the IDE locks the file on Windows making automatic updates of SDK Tools fail. The
   *    update not only fails, it corrupts the 'tools' folder in the SDK.
   * From now on, creating IDEA Android SDKs will not include annotations.jar if API level is 16 or above, but we still need to remove
   * this jar from existing IDEA Android SDKs.
   */
  private boolean shouldRemoveAnnotationsJar(@NotNull Sdk sdk) {
    if (myAndroidSdks.isAndroidSdk(sdk)) {
      AndroidSdkAdditionalData additionalData = myAndroidSdks.getAndroidSdkAdditionalData(sdk);
      AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);
      boolean needsAnnotationsJar = false;
      if (additionalData != null && sdkData != null) {
        IAndroidTarget target = additionalData.getBuildTarget(sdkData);
        if (target != null) {
          needsAnnotationsJar = myAndroidSdks.needsAnnotationsJarInClasspath(target);
        }
      }
      for (VirtualFile library : sdk.getRootProvider().getFiles(CLASSES)) {
        // This code does not through the classes in the Android SDK. It iterates through a list of 3 files in the IDEA SDK: android.jar,
        // annotations.jar and res folder.
        if (library.getName().equals(FN_ANNOTATIONS_JAR) && library.exists() && !needsAnnotationsJar) {
          return true;
        }
      }
    }
    return false;
  }

  private void reinstallMissingPlatforms(@NotNull Set<Sdk> invalidSdks, @NotNull Project project) {
    List<AndroidVersion> versionsToInstall = new ArrayList<>();
    List<String> missingPlatforms = new ArrayList<>();

    for (Sdk sdk : invalidSdks) {
      AndroidSdkAdditionalData additionalData = myAndroidSdks.getAndroidSdkAdditionalData(sdk);
      if (additionalData != null) {
        String platform = additionalData.getBuildTargetHashString();
        if (platform != null) {
          missingPlatforms.add("'" + platform + "'");
          AndroidVersion version = AndroidTargetHash.getPlatformVersion(platform);
          if (version != null) {
            versionsToInstall.add(version);
          }
        }
      }
    }

    if (!versionsToInstall.isEmpty()) {
      String text = "Missing Android platform(s) detected: " + Joiner.on(", ").join(missingPlatforms);
      SyncMessage msg = new SyncMessage(SyncMessage.DEFAULT_GROUP, ERROR, text);
      msg.add(new InstallPlatformHyperlink(versionsToInstall));
      GradleSyncMessages.getInstance(project).report(msg);
    }
  }

  private static boolean hasSources(@NotNull Sdk sdk) {
    String[] urls = sdk.getRootProvider().getUrls(SOURCES);
    return urls.length > 0;
  }
}
