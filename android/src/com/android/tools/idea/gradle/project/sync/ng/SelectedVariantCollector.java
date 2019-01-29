/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;

class SelectedVariantCollector {
  @NotNull private final Project myProject;

  SelectedVariantCollector(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  SelectedVariants collectSelectedVariants() {
    SelectedVariants selectedVariants = new SelectedVariants();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      SelectedVariant variant = findSelectedVariant(module);
      if (variant != null) {
        selectedVariants.addSelectedVariant(variant.moduleId, variant.variantName, variant.abiName);
      }
    }
    return selectedVariants;
  }

  @VisibleForTesting
  @Nullable
  SelectedVariant findSelectedVariant(@NotNull Module module) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet != null) {
      GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
      if (gradleModel != null) {
        File rootFolder = gradleModel.getRootFolderPath();
        String projectPath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;

        AndroidFacet androidFacet = AndroidFacet.getInstance(module);
        NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
        NdkFacet ndkFacet = NdkFacet.getInstance(module);
        if (ndkFacet != null && ndkModuleModel != null) {
          String ndkVariantName = ndkFacet.getConfiguration().SELECTED_BUILD_VARIANT;
          return new SelectedVariant(rootFolder, projectPath, ndkModuleModel.getVariantName(ndkVariantName),
                                     ndkModuleModel.getAbiName(ndkVariantName));
        }
        if (androidFacet != null) {
          return new SelectedVariant(rootFolder, projectPath, androidFacet.getProperties().SELECTED_BUILD_VARIANT, null);
        }
      }
    }
    return null;
  }

  static class SelectedVariant {
    @NotNull final String moduleId;
    @NotNull final String variantName;
    @Nullable final String abiName;

    SelectedVariant(@NotNull File rootFolderPath, @NotNull String gradlePath, @NotNull String variantName, @Nullable String abiName) {
      this.moduleId = createUniqueModuleId(rootFolderPath, gradlePath);
      this.variantName = variantName;
      this.abiName = abiName;
    }
  }
}
