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
package com.android.tools.idea.gradle.customizer;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sets an Android SDK to a module imported from an {@link com.android.build.gradle.model.AndroidProject}.
 */
public class AndroidSdkModuleCustomizer implements ModuleCustomizer {
  @NotNull private final ProjectJdkTable myProjectJdkTable;
  @NotNull private final AndroidPlatformParser myParser;

  public AndroidSdkModuleCustomizer() {
    myProjectJdkTable = ProjectJdkTable.getInstance();
    myParser = new AndroidPlatformParser();
  }

  @VisibleForTesting
  AndroidSdkModuleCustomizer(@NotNull ProjectJdkTable projectJdkTable, @NotNull AndroidPlatformParser parser) {
    myProjectJdkTable = projectJdkTable;
    myParser = parser;
  }

  /**
   * Sets an Android SDK to the given module only if:
   * <ol>
   *   <li>the given module was created by importing an {@code AndroidProject}</li>
   *   <li>there is a matching Android SDK already defined in IDEA</li>
   * </ol>
   *
   * @param module             module to customize.
   * @param ideaAndroidProject the imported Android-Gradle project.
   */
  @Override
  public void customizeModule(@NotNull Module module, @Nullable IdeaAndroidProject ideaAndroidProject) {
    Sdk androidSdk = getAndroidSdk(ideaAndroidProject);
    if (androidSdk == null) {
      return;
    }
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel model = moduleRootManager.getModifiableModel();
    try {
      model.setSdk(androidSdk);
    } finally {
      model.commit();
    }
  }

  @Nullable
  private Sdk getAndroidSdk(@Nullable IdeaAndroidProject ideaAndroidProject) {
    if (ideaAndroidProject != null) {
      for (Sdk sdk : myProjectJdkTable.getAllJdks()) {
        AndroidPlatform androidPlatform = myParser.parse(sdk);
        if (androidPlatform != null) {
          // TODO(alruiz): select Android SDK based on API level. Gradle model (AndroidProject) does not provide this information yet.
          return sdk;
        }
      }
    }
    return null;
  }

  // The purpose of this class is to mock invocations to the static method AndroidPlatform#parse(Sdk).
  static class AndroidPlatformParser {
    @Nullable
    AndroidPlatform parse(@NotNull Sdk sdk) {
      return AndroidPlatform.parse(sdk);
    }
  }
}
