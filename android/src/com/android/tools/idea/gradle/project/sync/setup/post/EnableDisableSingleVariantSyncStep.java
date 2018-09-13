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

import com.android.annotations.VisibleForTesting;
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
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.ng.NewGradleSync.NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.*;

// Update the eligibility of single-variant sync for current project.
public class EnableDisableSingleVariantSyncStep {
  public static final String PATH_IN_SETTINGS = "File → Settings → Experimental → Gradle → Only sync the active variant";

  public static void setSingleVariantSyncState(@NotNull Project project) {
    EligibilityState state = isEligibleForSingleVariantSync(project);
    //PropertiesComponent.getInstance(project).setValue(NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC, !state.equals(ELIGIBLE));
  }

  // Check if the project is eligible for Single-Variant sync.
  // Returns EligibilityState which indicates the state or the specific reason if not supported.
  @VisibleForTesting
  @NotNull
  static EligibilityState isEligibleForSingleVariantSync(@NotNull Project project) {
    boolean hasAndroidModule = false;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      // Check if module has kotlin plugin, only populated in new sync.
      if (hasKotlinPlugin(module)) {
        return HAS_KOTLIN;
      }
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
          return HAS_KOTLIN;
        }
      }
    }
    return hasAndroidModule ? ELIGIBLE : PURE_JAVA;
  }

  // Returns true if the module has kotlin plugin applied.
  static boolean hasKotlinPlugin(@NotNull Module module) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet != null) {
      GradleModuleModel moduleModel = gradleFacet.getGradleModuleModel();
      if (moduleModel != null) {
        List<String> plugins = moduleModel.getGradlePlugins();
        return plugins.stream().anyMatch(p -> p.startsWith("org.jetbrains.kotlin"));
      }
    }
    return false;
  }

  @VisibleForTesting
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
    // The project contains Kotlin modules.
    HAS_KOTLIN {
      @Override
      @NotNull
      String getReason() {
        return "use Kotlin";
      }
    },
    // The project contains Native modules.
    HAS_NATIVE {
      @Override
      @NotNull
      String getReason() {
        return "contain native module";
      }
    },
    // The project doesn't contain any Android module.
    PURE_JAVA {
      @Override
      @NotNull
      String getReason() {
        return "do not contain Android module";
      }
    };

    @NotNull
    abstract String getReason();
  }
}
