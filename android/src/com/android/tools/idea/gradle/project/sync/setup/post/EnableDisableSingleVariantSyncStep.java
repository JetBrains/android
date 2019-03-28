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
package com.android.tools.idea.gradle.project.sync.setup.post;

import static com.android.tools.idea.gradle.project.sync.ng.NewGradleSync.NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.ELIGIBLE;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.KOTLIN_MPP;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.OLD_PLUGIN;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.PURE_JAVA;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

// Update the eligibility of single-variant sync for current project.
public class EnableDisableSingleVariantSyncStep {
  public static void setSingleVariantSyncState(@NotNull Project project) {
    EligibilityState state = isEligibleForSingleVariantSync(project);
    PropertiesComponent.getInstance(project).setValue(NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC, !state.equals(ELIGIBLE));
  }

  // Check if the project is eligible for Single-Variant sync.
  // Returns EligibilityState which indicates the state or the specific reason if not supported.
  @NotNull
  static EligibilityState isEligibleForSingleVariantSync(@NotNull Project project) {
    boolean hasAndroidModule = false;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      // Check if module has kotlin plugin, only populated in new sync.
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        if (AndroidFacet.NAME.equals(facet.getName())) {
          hasAndroidModule = true;
          AndroidModuleModel androidModel = AndroidModuleModel.get(module);
          // old plugin.
          if (androidModel != null && !androidModel.getFeatures().isSingleVariantSyncSupported()) {
            return OLD_PLUGIN;
          }
        }

        // native module.
        if (NdkFacet.getFacetName().equals(facet.getName())) {
          NdkModuleModel ndkModel = NdkModuleModel.get(module);
          // old plugin.
          if (ndkModel != null && !ndkModel.getFeatures().isSingleVariantSyncSupported()) {
            return OLD_PLUGIN;
          }
        }

        // kotlin module. Old sync relies on this check.
        // Hard-code kotlin facet name, because kotlin plugin didn't provide access to it, also good to avoid adding extra dependency on kotlin plugin.
        if ("Kotlin".equals(facet.getName())) {
          if (!hasKotlinPlugin(module)) {
            return OLD_PLUGIN;
          }
          // TODO: Add a MPP test for this once we have a correctly syncing project in test data.
          else if (hasMPPPlugin(module)) {
            return KOTLIN_MPP;
          }
        }
      }
    }
    return hasAndroidModule ? ELIGIBLE : PURE_JAVA;
  }

  // Returns true if the module has kotlin plugin applied.
  static boolean hasKotlinPlugin(@NotNull Module module) {
    return doesModuleContainPlugin(module, "org.jetbrains.kotlin");
  }

  static boolean hasMPPPlugin(@NotNull Module module) {
    return doesModuleContainPlugin(module, "org.jetbrains.kotlin.multiplatform");
  }

  static boolean doesModuleContainPlugin(@NotNull Module module, @NotNull String pluginId) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet != null) {
      GradleModuleModel moduleModel = gradleFacet.getGradleModuleModel();
      if (moduleModel != null) {
        List<String> plugins = moduleModel.getGradlePlugins();
        return plugins.stream().anyMatch(p -> p.startsWith(pluginId));
      }
    }
    return false;
  }

  enum EligibilityState {
    // Project eligible for single-variant sync.
    ELIGIBLE {
      @Override
      @NotNull
      String getReason() {
        return "";
      }
    },
    // The plugin doesn't support single-variant sync.
    OLD_PLUGIN {
      @Override
      @NotNull
      String getReason() {
        return "use Android Gradle Plugin older than 3.3";
      }
    },
    // The project doesn't contain any Android module.
    PURE_JAVA {
      @Override
      @NotNull
      String getReason() {
        return "do not contain Android module";
      }
    },
    // The project contains a Kotlin MPP modules
    KOTLIN_MPP {
      @Override
      @NotNull
      String getReason() {
        return "uses Kotlin MPP";
      }
    };

    @NotNull
    abstract String getReason();
  }
}
