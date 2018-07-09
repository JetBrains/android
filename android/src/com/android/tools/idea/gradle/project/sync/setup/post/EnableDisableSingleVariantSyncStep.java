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
import com.android.tools.idea.gradle.project.PropertyBasedDoNotAskOption;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

// If single-variant sync is not enabled and the project is eligible, show dialog to ask user to opt-in,
// if single-variant sync is enabled but the project is not eligible, show dialog to disable single-variant sync and re-sync project.
public class EnableDisableSingleVariantSyncStep {
  public static final String PATH_IN_SETTINGS = "File → Settings → Experimental → Gradle → Only resolve selected variants.";
  private static final String DO_NOT_ASK_TO_ENABLE_SINGLE_VARIANT_SYNC = "do.not.ask.enable.single.variant.sync";

  /**
   * @return true if single-variant is enabled but not supported for current project.
   */
  public boolean checkAndDisableOption(@NotNull Project project, @NotNull GradleSyncState syncState) {
    GradlePerProjectExperimentalSettings settings = GradlePerProjectExperimentalSettings.getInstance(project);
    // Already enabled.
    if (!settings.USE_SINGLE_VARIANT_SYNC) {
      return false;
    }

    // Single-Variant sync is supported on current project.
    if (isEligibleForSingleVariantSync(project)) {
      return false;
    }

    String message = "The new feature to only resolve active variants is not supported on current project.\n" +
                     "Disable this feature and sync again.";
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

    PropertyBasedDoNotAskOption myDoNotAskOption = new PropertyBasedDoNotAskOption(project, DO_NOT_ASK_TO_ENABLE_SINGLE_VARIANT_SYNC) {
      @Override
      @NotNull
      public String getDoNotShowMessage() {
        return "Don't remind me again for this project";
      }
    };

    //User checked do not ask again.
    if (!myDoNotAskOption.isToBeShown()) {
      return;
    }

    // Single-Variant sync not supported on current project.
    if (!isEligibleForSingleVariantSync(project)) {
      return;
    }

    String message = "Would you like to enable the new feature to only resolve active variants\n" +
                     "during Gradle sync?\n" +
                     "You can enable/disable this feature from\n" +
                     PATH_IN_SETTINGS;
    int userAcceptsEnable = invokeAndWaitIfNeeded(
      () -> MessageDialogBuilder.yesNo("Experimental Gradle Sync Feature", message)
                                .yesText(Messages.YES_BUTTON)
                                .noText(Messages.NO_BUTTON)
                                .doNotAsk(myDoNotAskOption).show());
    if (userAcceptsEnable == Messages.YES) {
      settings.USE_SINGLE_VARIANT_SYNC = true;
    }
  }

  // Check if the project is eligible for Single-Variant sync, returns false if any modules is native or kotlin module, or is using AGP version
  // that doesn't support single-variant sync, or there's no Android module.
  @VisibleForTesting
  static boolean isEligibleForSingleVariantSync(@NotNull Project project) {
    boolean hasAndroidModule = false;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      // Check if module has kotlin plugin, only populated in new sync.
      if (hasKotlinPlugin(module)) {
        return false;
      }
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        if (AndroidFacet.NAME.equals(facet.getName())) {
          hasAndroidModule = true;
          AndroidModuleModel androidModel = AndroidModuleModel.get(module);
          // old plugin.
          if (androidModel != null && !androidModel.getFeatures().isSingleVariantSyncSupported()) {
            return false;
          }
        }
        // kotlin module. Old sync relies on this check.
        // Hard-code kotlin facet name, because kotlin plugin didn't provide access to it, also good to avoid adding extra dependency on kotlin plugin.
        if ("Kotlin".equals(facet.getName())) {
          return false;
        }
      }
    }
    return hasAndroidModule;
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
}
