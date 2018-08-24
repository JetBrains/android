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
import com.android.tools.idea.gradle.project.GradlePerProjectExperimentalSettings;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.setup.post.EnableDisableSingleVariantSyncStep.EligibilityState.*;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

// If single-variant sync is not enabled and the project is eligible, show dialog to ask user to opt-in,
// if single-variant sync is enabled but the project is not eligible, show dialog to disable single-variant sync and re-sync project.
public class EnableDisableSingleVariantSyncStep {
  public static final String PATH_IN_SETTINGS = "File → Settings → Experimental → Gradle → Only sync the active variant";
  private static final String DO_NOT_DISPLAY_DIALOG_ENABLE_SINGLE_VARIANT_SYNC = "do.not.display.dialog.enable.single.variant.sync";

  /**
   * @return true if single-variant is enabled but not supported for current project.
   */
  public boolean checkAndDisableOption(@NotNull Project project, @NotNull GradleSyncState syncState) {
    GradlePerProjectExperimentalSettings settings = GradlePerProjectExperimentalSettings.getInstance(project);
    // Already disabled.
    if (!settings.USE_SINGLE_VARIANT_SYNC) {
      return false;
    }

    // Single-Variant sync is supported on current project.
    EligibilityState state = isEligibleForSingleVariantSync(project);
    if (state.equals(ELIGIBLE) || state.equals(PURE_JAVA)) {
      return false;
    }

    String message = "Projects that " + state.getReason() +
                     " are currently not compatible with the experimental \"Only sync the active variant\" feature.\n" +
                     "Click OK to disable the feature and sync again.\n" +
                     "\n" +
                     "You can update your choice later from\n" +
                     PATH_IN_SETTINGS;

    invokeAndWaitIfNeeded(
      (Runnable)() -> Messages.showMessageDialog(message, "Experimental Gradle Sync Feature", Messages.getWarningIcon())
    );
    settings.USE_SINGLE_VARIANT_SYNC = false;
    // Sync Project again.
    if (!syncState.lastSyncFailedOrHasIssues()) {
      syncState.syncEnded();
    }
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.userRequest();
    request.cleanProject = true;
    GradleSyncInvoker.getInstance().requestProjectSync(project, request);
    return true;
  }

  public void checkAndEnableOption(@NotNull Project project) {
    GradlePerProjectExperimentalSettings settings = GradlePerProjectExperimentalSettings.getInstance(project);
    // Already enabled.
    if (settings.USE_SINGLE_VARIANT_SYNC) {
      return;
    }

    // Asked for current project, and user chose No.
    if (PropertiesComponent.getInstance(project).getBoolean(DO_NOT_DISPLAY_DIALOG_ENABLE_SINGLE_VARIANT_SYNC)) {
      return;
    }

    // Single-Variant sync not supported on current project.
    if (!isEligibleForSingleVariantSync(project).equals(ELIGIBLE)) {
      return;
    }

    String message = "For faster sync times Android Studio can be configured to only sync the active variant.\n" +
                     "Enable this experimental feature now?\n" +
                     "\n" +
                     "You can update your choice later from\n" +
                     PATH_IN_SETTINGS;
    int userAcceptsEnable = invokeAndWaitIfNeeded(
      () -> MessageDialogBuilder.yesNo("Experimental Gradle Sync Feature", message)
                                .yesText(Messages.YES_BUTTON)
                                .noText(Messages.NO_BUTTON)
                                .icon(null) // no icon.
                                .show());
    if (userAcceptsEnable == Messages.YES) {
      settings.USE_SINGLE_VARIANT_SYNC = true;
    }
    // Ensure this dialog is displayed only once per project.
    PropertiesComponent.getInstance(project).setValue(DO_NOT_DISPLAY_DIALOG_ENABLE_SINGLE_VARIANT_SYNC, true);
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
