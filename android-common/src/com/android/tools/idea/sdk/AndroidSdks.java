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
package com.android.tools.idea.sdk;

import com.android.annotations.NonNull;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.sdk.AndroidSdkData;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AndroidSdks {
  static @NotNull AndroidSdks getInstance() {
    return ApplicationManager.getApplication().getService(AndroidSdks.class);
  }
  @NonNls String SDK_NAME_PREFIX = "Android ";
  Sdk findSuitableAndroidSdk(@NotNull String targetHash);
  void setSdkData(@Nullable AndroidSdkData data);
  @NotNull AndroidSdkHandler tryToChooseSdkHandler();
  /**
   * Returns the {@link AndroidSdkData} for the current SDK.
   *
   * @return the {@link AndroidSdkData} for the current SDK, or {@code null} during the first run or if an error occurred when setting up
   * the SDK.
   */
  @Nullable AndroidSdkData tryToChooseAndroidSdk();
  @NotNull List<Sdk> getAllAndroidSdks();
  @Nullable Sdk tryToCreate(@NotNull File sdkPath, @NotNull String targetHashString);
  @Nullable Sdk create(@NotNull IAndroidTarget target, @NotNull File sdkPath, boolean addRoots);
  @Nullable Sdk create(@NotNull IAndroidTarget target, @NotNull File sdkPath, @NotNull String sdkName, boolean addRoots);
  void setUpSdk(@NotNull Sdk androidSdk, @NotNull IAndroidTarget target, @NotNull String sdkName, @NotNull Collection<Sdk> allSdks);
  void findAndSetPlatformSources(@NotNull IAndroidTarget target, @NotNull SdkModificator sdkModificator);
  /**
   * Finds the root source code folder for the given android target, if any.
   */
  @Nullable File findPlatformSources(@NotNull IAndroidTarget target);
  @NotNull List<OrderRoot> getLibraryRootsForTarget(@NotNull IAndroidTarget target, @Nullable File sdkPath, boolean addPlatformAndAddOnJars);
  @NotNull String chooseNameForNewLibrary(@NotNull IAndroidTarget target);
  boolean isAndroidSdk(@NotNull Sdk sdk);
  @VisibleForTesting void replaceLibraries(@NotNull Sdk sdk, @NotNull VirtualFile[] libraries);
  boolean isInAndroidSdk(@NonNull PsiElement element);
  boolean isInAndroidSdk(@NonNull Project project, @NonNull VirtualFile file);
}