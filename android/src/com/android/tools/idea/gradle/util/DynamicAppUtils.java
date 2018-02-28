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
package com.android.tools.idea.gradle.util;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Various utility methods to navigate through various parts (dynamic features, base split, etc.)
 * of dynamic apps.
 */
public class DynamicAppUtils {
  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module.
   */
  @NotNull
  public static List<Module> getDependentFeatureModules(@NotNull Module module) {
    AndroidModuleModel androidModule = AndroidModuleModel.get(module);
    if (androidModule == null) {
      return ImmutableList.of();
    }
    return getDependentFeatureModules(module.getProject(), androidModule.getAndroidProject());
  }

  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module.
   */
  @NotNull
  public static List<Module> getDependentFeatureModules(@NotNull Project project, @NotNull AndroidProject androidProject) {
    Map<String, Module> featureMap = getDynamicFeaturesMap(project);
    return androidProject.getDynamicFeatures().stream()
      .map(featurePath -> featureMap.get(featurePath))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  /**
   * Returns the list of {@link Module modules} to build for a given base module.
   */
  @NotNull
  public static List<Module> getModulesToBuild(@NotNull Module module) {
    return Stream
      .concat(Stream.of(module), getDependentFeatureModules(module).stream())
      .collect(Collectors.toList());
  }

  /**
   * Returns {@code true} if Instant Run is supported for this module.
   *
   * Note: We currently disable Instant Run as soon as the base split has any dynamic feature.
   */
  public static boolean isInstantRunSupported(@NotNull Module module) {
    AndroidModuleModel androidModule = AndroidModuleModel.get(module);
    if (androidModule == null) {
      return true;
    }
    return androidModule.getAndroidProject().getDynamicFeatures().isEmpty();
  }

  @NotNull
  private static Map<String, Module> getDynamicFeaturesMap(@NotNull Project project) {
    return Arrays.stream(ModuleManager.getInstance(project).getModules())
      .map(module -> {
        // Check the module is a "dynamic feature"
        AndroidModuleModel model = AndroidModuleModel.get(module);
        if (model == null) {
          return null;
        }
        if (model.getAndroidProject().getProjectType() != AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE) {
          return null;
        }

        // Find the gradle path of the module
        GradleFacet facet = GradleFacet.getInstance(module);
        if (facet == null) {
          return null;
        }
        GradleModuleModel gradleModel = facet.getGradleModuleModel();
        if (gradleModel == null) {
          return null;
        }
        return Pair.create(gradleModel.getGradlePath(), module);
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toMap(p -> p.first, p -> p.second, DynamicAppUtils::handleModuleAmbiguity));
  }

  @NotNull
  private static Module handleModuleAmbiguity(@NotNull Module m1, @NotNull Module m2) {
    getLogger().warn(String.format("Unexpected ambiguity processing modules: %s - %s", m1.getName(), m2.getName()));
    return m1;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(DynamicAppUtils.class);
  }
}