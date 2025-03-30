/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.view;

import static com.android.tools.idea.gradle.project.sync.SelectedVariantCollectorKt.getModuleIdForSyncRequest;
import static com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor.ALWAYS_SKIP_SYNC;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER;
import static com.intellij.util.ThreeState.YES;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder;
import com.android.tools.idea.gradle.project.sync.SwitchVariantRequest;
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver;
import com.android.tools.idea.gradle.project.sync.idea.VariantSwitcher;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
public class BuildVariantUpdater {
  @NotNull
  private final Project myProject;

  @NotNull
  public static BuildVariantUpdater getInstance(@NotNull Project project) {
    return project.getService(BuildVariantUpdater.class);
  }

  // called by IDEA.
  @SuppressWarnings("unused")
  BuildVariantUpdater(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Updates a module's structure when the user selects a build variant from the tool window.
   */
  public void updateSelectedBuildVariant(@NotNull Module moduleToUpdate,
                                         @NotNull String selectedBuildVariant) {
    String moduleId = getModuleIdForSyncRequest(moduleToUpdate);
    if (moduleId != null) {
      updateSelectedVariant(moduleToUpdate, new SwitchVariantRequest(moduleId, selectedBuildVariant, null));
    }
  }

  /**
   * Updates a module's structure when the user selects an ABI from the tool window.
   */
  public void updateSelectedAbi(@NotNull Module moduleToUpdate,
                                @NotNull String selectedAbiName) {
    String moduleId = getModuleIdForSyncRequest(moduleToUpdate);
    if (moduleId != null) {
      updateSelectedVariant(moduleToUpdate,
                            new SwitchVariantRequest(moduleId,
                                                     null,
                                                     selectedAbiName));
    }
  }

  /**
   * Updates a module's structure when the user attempts to resolve a conflict.
   */
  public void updateSelectedVariantAndAbi(@NotNull Module module,
                                          @NotNull String selectedVariant,
                                          @NotNull String selectedAbiName) {
    String moduleId = getModuleIdForSyncRequest(module);
    if (moduleId != null) {
      updateSelectedVariant(module,
                            new SwitchVariantRequest(
                              moduleId,
                              selectedVariant,
                              selectedAbiName));
    }
  }

  /**
   * Updates a module's structure when the user selects a build variant or ABI.
   */
  private void updateSelectedVariant(@NotNull Module moduleToUpdate,
                                     @NotNull SwitchVariantRequest variantAndAbi) {
    @Nullable ExternalProjectInfo data =
      ProjectDataManager.getInstance().getExternalProjectData(myProject, GradleConstants.SYSTEM_ID, myProject.getBasePath());

    DataNode<ProjectData> variantProjectDataNode =
      StudioFlags.GRADLE_SYNC_ENABLE_CACHED_VARIANTS.get() && data != null
      ? VariantSwitcher.findVariantProjectData(moduleToUpdate, variantAndAbi, data)
      : null;

    // There are three different cases,
    // 1. Build files have been changed, request a full Gradle Sync - let Gradle Sync infrastructure handle single variant or not.
    // 2. Build files were not changed, variant to select doesn't exist, which can only happen with single-variant sync, request Variant-only Sync.
    // 3. Build files were not changed, variant to select exists, do module setup for affected modules.
    if (GradleSyncState.getInstance(myProject).isSyncNeeded().equals(YES)) {
      requestGradleSync(myProject, variantAndAbi, false);
      return;
    }

    if (data != null && variantProjectDataNode != null) {
      VariantSwitcher.findAndSetupSelectedCachedVariantData(data, variantProjectDataNode);
      setupCachedVariant(myProject, variantProjectDataNode);
      return;
    }

    // Build file is not changed, the cached variants should be cached and reused.
    AndroidGradleProjectResolver.saveCurrentlySyncedVariantsForReuse(myProject);
    requestGradleSync(myProject, variantAndAbi, false);
  }

  @NotNull
  private static GradleSyncListener getSyncListener() {
    return new GradleSyncListener() {
      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        AndroidGradleProjectResolver.clearVariantsSavedForReuse(project);
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        AndroidGradleProjectResolver.clearVariantsSavedForReuse(project);
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        if (project.getUserData(ALWAYS_SKIP_SYNC) == null) {
          AndroidNotification.getInstance(project)
            .showProgressBalloon("Cannot change the current build variant at this moment", MessageType.ERROR);
        }
        AndroidGradleProjectResolver.clearVariantsSavedForReuse(project);
      }
    };
  }

  public static void requestGradleSync(
    @NotNull Project project,
    SwitchVariantRequest requestedVariantChange,
    boolean importDefaultVariants
  ) {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(
      TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER, requestedVariantChange, importDefaultVariants);
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, getSyncListener());
  }

  private static void setupCachedVariant(@NotNull Project project,
                                         @NotNull DataNode<ProjectData> variantData) {
    Application application = ApplicationManager.getApplication();

    Task.Backgroundable task = new Task.Backgroundable(project, "Setting up Project", false/* cannot be canceled*/) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        getLog().info("Starting setup of cached variant");

        // While we work to move the rest of the setup we need to perform two commits, once using IDEAs data import and the other
        // using the remainder of out setup steps.
        VariantSwitcher.switchVariant(project, variantData);

        GradleSyncStateHolder.getInstance(project).syncSkipped(null);

        getLog().info("Finished setup of cached variant");
      }
    };

    if (application.isUnitTestMode()) {
      task.run(new EmptyProgressIndicator());
    }
    else {
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(BuildVariantUpdater.class);
  }
}
