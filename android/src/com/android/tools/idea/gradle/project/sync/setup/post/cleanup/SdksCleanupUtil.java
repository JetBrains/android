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

import static com.android.tools.idea.startup.ExternalAnnotationsSupport.attachJdkAnnotations;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static java.util.Arrays.asList;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.StudioAndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SdksCleanupUtil {
  // If the sdk is outdated, then all of its roots will be recreated.
  // An Sdk is considered outdated if any of the roots are different from expected roots.
  public static void updateSdkIfNeeded(@NotNull Sdk sdk, @NotNull AndroidSdks androidSdks) {
    IAndroidTarget target = getTarget(sdk);
    if (target != null) {
      updateSdkIfNeeded(sdk, androidSdks, target);
    }
  }

  @VisibleForTesting
  static void updateSdkIfNeeded(@NotNull Sdk sdk, @NotNull AndroidSdks androidSdks, @NotNull IAndroidTarget target) {
    List<OrderRoot> expectedRoots = androidSdks.getLibraryRootsForTarget(target, FilePaths.stringToFile(sdk.getHomePath()), true);
    Map<OrderRootType, Set<String>> urlsByRootType = new HashMap<>();
    for (OrderRoot root : expectedRoots) {
      urlsByRootType.computeIfAbsent(root.getType(), k -> new HashSet<>()).add(root.getFile().getUrl());
    }

    for (OrderRootType type : asList(CLASSES, SOURCES, JavadocOrderRootType.getInstance())) {
      List<String> urlInSdk = asList(sdk.getRootProvider().getUrls(type));
      Set<String> expectedUrls = urlsByRootType.getOrDefault(type, Collections.emptySet());
      if (urlInSdk.size() != expectedUrls.size() || urlInSdk.stream().anyMatch(url -> !expectedUrls.contains(url))) {
        updateSdk(sdk, expectedRoots);
        return;
      }
    }
  }

  @Nullable
  private static IAndroidTarget getTarget(@NotNull Sdk sdk) {
    AndroidSdkAdditionalData additionalData = AndroidSdkAdditionalData.from(sdk);
    AndroidSdkData sdkData = StudioAndroidSdkData.getSdkData(sdk);
    if (additionalData == null || sdkData == null) {
      return null;
    }
    IAndroidTarget target = additionalData.getBuildTarget(sdkData);
    if (target == null) {
      AndroidSdkHandler sdkHandler = sdkData.getSdkHandler();
      StudioLoggerProgressIndicator logger = new StudioLoggerProgressIndicator(SdksCleanupUtil.class);
      sdkHandler.getSdkManager(logger).loadSynchronously(0, logger, null, null);
      target = sdkHandler.getAndroidTargetManager(logger).getTargetFromHashString(additionalData.getBuildTargetHashString(), logger);
    }
    return target;
  }

  private static void updateSdk(@NotNull Sdk sdk, @NotNull List<OrderRoot> expectedRoots) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.removeAllRoots();
    for (OrderRoot orderRoot : expectedRoots) {
      sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
    }
    attachJdkAnnotations(sdkModificator);
    ApplicationManager.getApplication().invokeAndWait(sdkModificator::commitChanges);
  }
}
