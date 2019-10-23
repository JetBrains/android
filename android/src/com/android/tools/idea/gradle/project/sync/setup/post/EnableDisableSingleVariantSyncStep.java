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
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.BUILDSRC_MODULE;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.ELIGIBLE;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.KOTLIN;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.OLD_PLUGIN;
import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.PURE_JAVA;
import static com.google.common.base.Strings.nullToEmpty;

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.SyncProjectModels;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

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
    if (hasBuildSrcModule(project)) {
      return BUILDSRC_MODULE;
    }
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
          return KOTLIN;
        }
      }
    }
    return hasAndroidModule ? ELIGIBLE : PURE_JAVA;
  }

  private static boolean hasBuildSrcModule(@NotNull Project project) {
    String projectPath = project.getBasePath();
    List<String> projectRoots = new ArrayList<>();
    if (projectPath != null) {
      // collect root project directory.
      projectRoots.add(projectPath);
      // collect composite build project directory.
      GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath);
      if (projectSettings != null) {
        GradleProjectSettings.CompositeBuild compositeBuild = projectSettings.getCompositeBuild();
        if (compositeBuild != null) {
          for (BuildParticipant participant : compositeBuild.getCompositeParticipants()) {
            projectRoots.add(nullToEmpty(participant.getRootPath()));
          }
        }
      }
    }
    return projectRoots.stream().anyMatch(path -> SyncProjectModels.hasBuildSrcModule(path));
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
    KOTLIN {
      @Override
      @NotNull
      String getReason() {
        return "use Kotlin";
      }
    },
    // The project contains buildSrc modules
    BUILDSRC_MODULE {
      @Override
      @NotNull
      String getReason() {
        return "contain buildSrc module";
      }
    };

    @NotNull
    abstract String getReason();
  }
}
