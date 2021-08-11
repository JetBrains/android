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

import static com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor.ALWAYS_SKIP_SYNC;
import static com.android.tools.idea.gradle.project.sync.idea.KotlinPropertiesKt.restoreKotlinUserDataFromDataNodes;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER;
import static com.intellij.util.ThreeState.YES;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.VariantAbi;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver;
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolverKeys;
import com.android.tools.idea.gradle.project.sync.idea.VariantAndAbi;
import com.android.tools.idea.gradle.project.sync.idea.VariantSwitcher;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
public class BuildVariantUpdater {
  @NotNull private final List<BuildVariantView.BuildVariantSelectionChangeListener> mySelectionChangeListeners =
    ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull
  public static BuildVariantUpdater getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BuildVariantUpdater.class);
  }

  // called by IDEA.
  @SuppressWarnings("unused")
  BuildVariantUpdater() { }

  /**
   * Add an {@link BuildVariantView.BuildVariantSelectionChangeListener} to the updater. Listeners are
   * invoked when the project's selected build variant changes.
   */
  void addSelectionChangeListener(@NotNull BuildVariantView.BuildVariantSelectionChangeListener listener) {
    mySelectionChangeListeners.add(listener);
  }

  /**
   * Updates a module's structure when the user selects a build variant from the tool window.
   *
   * @param project              the module's project.
   * @param moduleName           the module's name.
   * @param selectedBuildVariant the name of the selected build variant (without ABI).
   * @return true if there are affected facets.
   */
  public boolean updateSelectedBuildVariant(@NotNull Project project,
                                            @NotNull String moduleName,
                                            @NotNull String selectedBuildVariant) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find module '%1$s'.", moduleName));
      return false;
    }

    NdkModuleModel ndkModuleModel = getNdkModelIfItHasNativeVariantAbis(moduleToUpdate);
    NdkFacet ndkFacet = NdkFacet.getInstance(moduleToUpdate);
    if (ndkModuleModel == null || ndkFacet == null) {
      // Non-native module. ABI is irrelevant. Proceed with the build variant without ABI.
      return updateSelectedVariant(project, moduleName, new VariantAndAbi(selectedBuildVariant, null));
    }

    // Native module: try to preserve the existing ABI for that module (if exists).
    VariantAbi newVariantAbi = resolveNewVariantAbi(ndkFacet, ndkModuleModel, selectedBuildVariant, null);
    if (newVariantAbi == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find suitable ABI for native module '%1$s'.", moduleName));
      return false;
    }

    return updateSelectedVariant(project, moduleName, VariantAndAbi.fromVariantAbi(newVariantAbi));
  }

  /**
   * Updates a module's structure when the user selects an ABI from the tool window.
   *
   * @param project         the module's project.
   * @param moduleName      the module's name.
   * @param selectedAbiName the name of the selected ABI.
   * @return true if there are affected facets.
   */
  public boolean updateSelectedAbi(@NotNull Project project,
                                   @NotNull String moduleName,
                                   @NotNull String selectedAbiName) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      logAndShowAbiNameFailure(String.format("Cannot find module '%1$s'.", moduleName));
      return false;
    }

    NdkModuleModel ndkModuleModel = getNdkModelIfItHasNativeVariantAbis(moduleToUpdate);
    NdkFacet ndkFacet = NdkFacet.getInstance(moduleToUpdate);
    if (ndkModuleModel == null || ndkFacet == null) {
      // This is unexpected. If we presented ABI dropdown for this module, then it must have an NDK model.
      logAndShowAbiNameFailure(String.format("Cannot find native module model '%1$s'.", moduleName));
      return false;
    }

    // Keep using the same existing build variant.
    VariantAbi currentSelectedVariantAbi = ndkFacet.getSelectedVariantAbi();
    VariantAbi newVariantAbi;
    if (currentSelectedVariantAbi == null) {
      newVariantAbi = null;
    }
    else {
      newVariantAbi = resolveNewVariantAbi(ndkFacet, ndkModuleModel, currentSelectedVariantAbi.getVariant(), selectedAbiName);
    }
    if (newVariantAbi == null) {
      logAndShowAbiNameFailure(String.format("Cannot find suitable ABI for native module '%1$s'.", moduleName));
      return false;
    }

    return updateSelectedVariant(project, moduleName, VariantAndAbi.fromVariantAbi(newVariantAbi));
  }

  /**
   * Updates a module's structure when the user selects a build variant or ABI.
   *
   * @param project       the module's project.
   * @param moduleName    the module's name.
   * @param variantAndAbi the name of the selected build variant (without abi for non-native modules, with ABI for native modules).
   * @return true if there are affected facets.
   */
  private boolean updateSelectedVariant(@NotNull Project project,
                                        @NotNull String moduleName,
                                        @NotNull VariantAndAbi variantAndAbi) {
    Module module = findModule(project, moduleName);
    if (module == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find module '%1$s'.", moduleName));
      return false;
    }

    if (!findAndUpdateAffectedFacets(module, variantAndAbi)) {
      return false;
    }

    @Nullable ExternalProjectInfo data =
      ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, project.getBasePath());

    Map<String, VariantAndAbi> variantsExpectedAfterSwitch =
      StudioFlags.GRADLE_SYNC_ENABLE_CACHED_VARIANTS.get()
      ? VariantSwitcher.computeExpectedVariantsAfterSwitch(module, variantAndAbi, data)
      : null;

    Runnable invokeVariantSelectionChangeListeners = () -> {
      for (BuildVariantView.BuildVariantSelectionChangeListener listener : mySelectionChangeListeners) {
        listener.selectionChanged();
      }
    };

    // There are three different cases,
    // 1. Build files have been changed, request a full Gradle Sync - let Gradle Sync infrastructure handle single variant or not.
    // 2. Build files were not changed, variant to select doesn't exist, which can only happen with single-variant sync, request Variant-only Sync.
    // 3. Build files were not changed, variant to select exists, do module setup for affected modules.
    if (GradleSyncState.getInstance(project).isSyncNeeded().equals(YES)) {
      requestGradleSync(project, module, invokeVariantSelectionChangeListeners);
      return true;
    }

    if (variantsExpectedAfterSwitch != null) {
      DataNode<ProjectData> variantProjectDataNode = VariantSwitcher.findAndSetupSelectedCachedVariantData(data, variantsExpectedAfterSwitch);
      if (variantProjectDataNode != null) {
        restoreKotlinUserDataFromDataNodes(variantProjectDataNode);
        setupCachedVariant(project, variantProjectDataNode, invokeVariantSelectionChangeListeners);
        return true;
      }
    }

    // Build file is not changed, the cached variants should be cached and reused.
    AndroidGradleProjectResolver.saveCurrentlySyncedVariantsForReuse(project);
    requestGradleSync(project, module, invokeVariantSelectionChangeListeners);

    return true;
  }

  /**
   * Finds all need-to-update facets and change the selected variant in facets recursively.
   * If the target variant exists, change selected variant in ModuleModel as well.
   *
   * @return true if the target variant exists.
   */
  private static boolean findAndUpdateAffectedFacets(@NotNull Module moduleToUpdate,
                                                     @NotNull VariantAndAbi variantToSelect) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(moduleToUpdate);
    if (androidFacet == null) {
      throw new IllegalStateException(
        String.format("Cannot update the selected build variant. Module: %s Variant: %s", moduleToUpdate, variantToSelect));
    }
    NdkFacet ndkFacet = NdkFacet.getInstance(moduleToUpdate);
    VariantAbi selectedVariantAbi = ndkFacet != null ? ndkFacet.getSelectedVariantAbi() : null;
    String selectedAbi = selectedVariantAbi != null ? selectedVariantAbi.getAbi() : null;
    if (
      Objects.equals(variantToSelect.getVariant(), androidFacet.getProperties().SELECTED_BUILD_VARIANT)
      && Objects.equals(variantToSelect.getAbi(), selectedAbi)
    ) {
      // Nothing to update. The user has selected the same build variant.
      return false;
    }

    String variantName = variantToSelect.getVariant();
    if (ndkFacet != null) {
      NdkModuleModel ndkModuleModel = getNdkModelIfItHasNativeVariantAbis(ndkFacet);
      if (ndkModuleModel != null) {
        VariantAbi variantAbiToSelect = variantToSelect.toVariantAbi();
        if (variantAbiToSelect != null) {
          // If there is ABI but there is a facet something went wrong and Gradle sync should fix it.
          ndkFacet.setSelectedVariantAbi(variantAbiToSelect);
        }
      }
    }
    androidFacet.getProperties().SELECTED_BUILD_VARIANT = variantName;
    return true;
  }

  /**
   * If the user modified the ABI of a module, then any dependent native module should use that same ABI (if possible).
   * If the user did not modify any ABIs (e.g., they changed a non-native module from "debug" to "release"), then any dependent native
   * module should preserve its ABI (if possible).
   * In either case, if the target ABI (either selected by the user, or preserved automatically) does not exist (e.g., "debug-x86" exists,
   * but "release-x86" does not), then the dependent modules should use any available ABI for the target variant.
   *
   * @param ndkModel        The NDK model of the current module, which contains the old variant with ABI. E.g., "debug-x86".
   * @param newVariant      The name of the selected variant (without ABI). E.g., "release".
   * @param userSelectedAbi The name of the selected ABI. E.g., "x86".
   * @return The variant that to be used for the current module, including the ABI. E.g., "release-x86".
   */
  @Nullable
  private static VariantAbi resolveNewVariantAbi(
    @NotNull NdkFacet ndkFacet,
    @NotNull NdkModuleModel ndkModel,
    @NotNull String newVariant,
    @Nullable String userSelectedAbi) {
    if (userSelectedAbi != null) {
      VariantAbi newVariantAbi = new VariantAbi(newVariant, userSelectedAbi);
      if (ndkModel.getAllVariantAbis().contains(newVariantAbi)) {
        return newVariantAbi;
      }
    }
    VariantAbi selectedVariantAbi = ndkFacet.getSelectedVariantAbi();
    if (selectedVariantAbi == null) return null;
    String existingAbi = selectedVariantAbi.getAbi();
    return new VariantAbi(newVariant, existingAbi);  // e.g., debug-x86
  }


  @NotNull
  private static GradleSyncListener getSyncListener(@NotNull Runnable variantSelectionChangeListeners) {
    return new GradleSyncListener() {
      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        AndroidGradleProjectResolver.clearVariantsSavedForReuse(project);
        variantSelectionChangeListeners.run();
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        AndroidGradleProjectResolver.clearVariantsSavedForReuse(project);
        variantSelectionChangeListeners.run();
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        if (project.getUserData(ALWAYS_SKIP_SYNC) == null) {
          throw new IllegalStateException("Sync cannot complete with syncSkipped result when switching variants.");
        }
        AndroidGradleProjectResolver.clearVariantsSavedForReuse(project);
        variantSelectionChangeListeners.run();
      }
    };
  }

  private static void requestGradleSync(@NotNull Project project,
                                        @NotNull Module module,
                                        @NotNull Runnable variantSelectionChangeListeners) {
    String moduleId = AndroidGradleProjectResolver.getModuleIdForModule(module);
    if (moduleId != null) {
      project.putUserData(AndroidGradleProjectResolverKeys.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI, moduleId);
    }
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER);
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, getSyncListener(variantSelectionChangeListeners));
  }


  private static void setupCachedVariant(@NotNull Project project,
                                         @NotNull DataNode<ProjectData> variantData,
                                         @NotNull Runnable variantSelectionChangeListeners) {
    Application application = ApplicationManager.getApplication();

    Task.Backgroundable task = new Task.Backgroundable(project, "Setting up Project", false/* cannot be canceled*/) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        getLog().info("Starting setup of cached variant");

        // While we work to move the rest of the setup we need to perform two commits, once using IDEAs data import and the other
        // using the remainder of out setup steps.
        VariantSwitcher.switchVariant(project, variantData);

        GradleSyncState.getInstance(project).syncSkipped(null);

        // Commit changes and dispose models providers
        if (application.isUnitTestMode()) {
          variantSelectionChangeListeners.run();
        }
        else {
          application.invokeLater(variantSelectionChangeListeners);
        }

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

  @Nullable
  private static Module findModule(@NotNull Project project, @NotNull String moduleName) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    return moduleManager.findModuleByName(moduleName);
  }

  @Nullable
  private static NdkModuleModel getNdkModelIfItHasNativeVariantAbis(@NotNull NdkFacet facet) {
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(facet);
    if (ndkModuleModel == null) {
      logAndShowBuildVariantFailure(
        String.format("Cannot find NativeAndroidProject for module '%1$s'.", facet.getModule().getName()));
      return null;
    }
    if (ndkModuleModel.getAllVariantAbis().isEmpty()) {
      // Native module that does not have any real ABIs. Proceed with the build variant without ABI.
      return null;
    }
    return ndkModuleModel;
  }

  @Nullable
  private static NdkModuleModel getNdkModelIfItHasNativeVariantAbis(@NotNull Module module) {
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
    if (ndkModuleModel == null) {
      return null;
    }
    if (ndkModuleModel.getAllVariantAbis().isEmpty()) {
      // Native module that does not have any real ABIs. Proceed with the build variant without ABI.
      return null;
    }
    return ndkModuleModel;
  }


  private static void logAndShowBuildVariantFailure(@NotNull String reason) {
    String prefix = "Unable to select build variant:\n";
    String msg = prefix + reason;
    getLog().error(msg);
    msg += ".\n\nConsult IDE log for more details (Help | Show Log)";
    Messages.showErrorDialog(msg, "Error");
  }

  private static void logAndShowAbiNameFailure(@NotNull String reason) {
    String prefix = "Unable to select ABI:\n";
    String msg = prefix + reason;
    getLog().error(msg);
    msg += ".\n\nConsult IDE log for more details (Help | Show Log)";
    Messages.showErrorDialog(msg, "Error");
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(BuildVariantUpdater.class);
  }
}
