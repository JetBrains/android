/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;

import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallPlatformHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanupStep;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SdksCleanupStep extends ProjectCleanupStep {
  @NotNull private final AndroidSdks myAndroidSdks;

  public SdksCleanupStep() {
    myAndroidSdks = AndroidSdks.getInstance();
  }

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
    if (androidFacet == null) {
      return;
    }
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || invalidSdks.contains(sdk) || fixedSdks.contains(sdk)) {
      return;
    }
    SdksCleanupUtil.updateSdkIfNeeded(sdk, myAndroidSdks);
    fixedSdks.add(sdk);
    // If attempting to fix up the roots in the SDK fails, install the target over again
    // (this is a truly corrupt install, as opposed to an incorrectly synced SDK which the
    // above workaround deals with)
    if (isMissingAndroidLibrary(sdk)) {
      invalidSdks.add(sdk);
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

  private void reinstallMissingPlatforms(@NotNull Set<Sdk> invalidSdks, @NotNull Project project) {
    List<AndroidVersion> versionsToInstall = new ArrayList<>();
    List<String> missingPlatforms = new ArrayList<>();

    for (Sdk sdk : invalidSdks) {
      AndroidSdkAdditionalData additionalData = AndroidSdkAdditionalData.from(sdk);
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
}
