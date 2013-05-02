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

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sets an Android SDK to a module imported from an {@link com.android.build.gradle.model.AndroidProject}.
 */
public class AndroidSdkModuleCustomizer implements ModuleCustomizer {
  /**
   * Sets an Android SDK to the given module only if:
   * <ol>
   *   <li>the given module was created by importing an {@code AndroidProject}</li>
   *   <li>there is a matching Android SDK already defined in IDEA</li>
   * </ol>
   *
   * @param module             module to customize.
   * @param project            project that owns the module to customize.
   * @param ideaAndroidProject the imported Android-Gradle project.
   */
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
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
  private static Sdk getAndroidSdk(@Nullable IdeaAndroidProject ideaAndroidProject) {
    if (ideaAndroidProject != null) {
      String compileTarget = ideaAndroidProject.getDelegate().getCompileTarget();
      for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
        AndroidPlatform androidPlatform = AndroidPlatform.parse(sdk);
        if (androidPlatform != null) {
          AndroidSdkData sdkData = androidPlatform.getSdkData();
          IAndroidTarget target = sdkData.findTargetByHashString(compileTarget);
          if (target != null) {
            return sdk;
          }
        }
      }
    }
    // TODO: Prompt user for path of an Android SDK.
    return null;
  }
}
