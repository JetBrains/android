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

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_FULL_SYNC;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ThreeState.YES;

import com.android.tools.idea.gradle.model.IdeModuleLibrary;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.VariantAbi;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver;
import com.android.tools.idea.gradle.project.sync.idea.VariantSwitcher;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.projectsystem.gradle.sync.AndroidModuleDataServiceKt;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      return updateSelectedVariant(project, moduleName, selectedBuildVariant);
    }

    // Native module: try to preserve the existing ABI for that module (if exists).
    VariantAbi newVariantAbi = resolveNewVariantAbi(ndkFacet, ndkModuleModel, selectedBuildVariant, null);
    if (newVariantAbi == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find suitable ABI for native module '%1$s'.", moduleName));
      return false;
    }

    return updateSelectedVariant(project, moduleName, newVariantAbi.getDisplayName());
  }

  /**
   * Updates a module's structure when the user selects an ABI from the tool window.
   *
   * @param project         the module's project.
   * @param moduleName      the module's name.
   * @param selectedAbiName the name of the selected ABI.
   * @return true if there are affected facets.
   */
  boolean updateSelectedAbi(@NotNull Project project,
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
    } else {
      newVariantAbi = resolveNewVariantAbi(ndkFacet, ndkModuleModel, currentSelectedVariantAbi.getVariant(), selectedAbiName);
    }
    if (newVariantAbi == null) {
      logAndShowAbiNameFailure(String.format("Cannot find suitable ABI for native module '%1$s'.", moduleName));
      return false;
    }

    return updateSelectedVariant(project, moduleName, newVariantAbi.getDisplayName());
  }

  /**
   * Updates a module's structure when the user selects a build variant or ABI.
   *
   * @param project          the module's project.
   * @param moduleName       the module's name.
   * @param buildVariantName the name of the selected build variant (without abi for non-native modules, with ABI for native modules).
   * @return true if there are affected facets.
   */
  private boolean updateSelectedVariant(@NotNull Project project,
                                        @NotNull String moduleName,
                                        @NotNull String buildVariantName) {
    List<AndroidFacet> affectedAndroidFacets = new ArrayList<>();
    List<NdkFacet> affectedNdkFacets = new ArrayList<>();
    // find all of affected facets, and update the value of selected build variant.
    boolean variantToUpdateExists =
      findAndUpdateAffectedFacets(project, moduleName, buildVariantName, affectedAndroidFacets, affectedNdkFacets);
    // nothing to update.
    if (affectedAndroidFacets.isEmpty() && affectedNdkFacets.isEmpty()) {
      return false;
    }
    Runnable invokeVariantSelectionChangeListeners = () -> {
      for (BuildVariantView.BuildVariantSelectionChangeListener listener : mySelectionChangeListeners) {
        listener.selectionChanged();
      }
    };

    // There are three different cases,
    // 1. Build files have been changed, request a full Gradle Sync - let Gradle Sync infrastructure handle single variant or not.
    // 2. Build files were not changed, variant to select doesn't exist, which can only happen with single-variant sync, request Variant-only Sync.
    // 3. Build files were not changed, variant to select exists, do module setup for affected modules.
    if (hasBuildFilesChanged(project)) {
      setVariantSwitchedProperty(project, moduleName);
      requestFullGradleSync(project, invokeVariantSelectionChangeListeners);
    }
    else if (!variantToUpdateExists) {
      // Build file is not changed, the cached variants should be cached and reused.
      project.putUserData(AndroidGradleProjectResolver.USE_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS, true);
      setVariantSwitchedProperty(project, moduleName);
      requestVariantOnlyGradleSync(project, moduleName, invokeVariantSelectionChangeListeners);
    }
    else {
      // For now we need to update every facet to ensure content entries are accurate.
      // TODO: Remove this once content entries use DataNodes.
      List<AndroidFacet> allAndroidFacets = new ArrayList<>();
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        AndroidFacet androidFacet = AndroidFacet.getInstance(module);
        if (androidFacet != null) {
          allAndroidFacets.add(androidFacet);
        }
      }
      setupCachedVariant(project, allAndroidFacets, invokeVariantSelectionChangeListeners);
    }
    return true;
  }

  private static void setVariantSwitchedProperty(@NotNull Project project,
                                                 @NotNull String moduleName) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      return;
    }
    String moduleId = AndroidGradleProjectResolver.getModuleIdForModule(moduleToUpdate);
    if (moduleId != null) {
      project.putUserData(AndroidGradleProjectResolver.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI, moduleId);
    }
  }

  /**
   * Finds all need-to-update facets and change the selected variant in facets recursively.
   * If the target variant exists, change selected variant in ModuleModel as well.
   *
   * @return true if the target variant exists.
   */
  private static boolean findAndUpdateAffectedFacets(@NotNull Project project,
                                                     @NotNull String moduleName,
                                                     @NotNull String variantToSelect,
                                                     @NotNull List<AndroidFacet> affectedAndroidFacets,
                                                     @NotNull List<NdkFacet> affectedNdkFacets) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find module '%1$s'.", moduleName));
      return false;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(moduleToUpdate);
    NdkFacet ndkFacet = NdkFacet.getInstance(moduleToUpdate);

    if (androidFacet == null && ndkFacet == null) {
      String msg = String.format("Cannot find 'Android' or 'Native-Android-Gradle' facets in module '%1$s'.", moduleToUpdate.getName());
      logAndShowBuildVariantFailure(msg);
    }

    boolean ndkVariantExists = true;
    boolean androidVariantExists = true;
    String variantName = variantToSelect;
    String abiName = null;
    if (ndkFacet != null) {
      NdkModuleModel ndkModuleModel = getNdkModelIfItHasNativeVariantAbis(ndkFacet);
      if (ndkModuleModel != null) {
        VariantAbi variantAbiToSelect = VariantAbi.fromString(variantToSelect);
        if (variantAbiToSelect == null) {
          logAndShowBuildVariantFailure(String.format("Cannot parse variant and ABI '%s'.", variantToSelect));
          return false;
        }
        if (ndkModuleModel.getAllVariantAbis().contains(variantAbiToSelect)) {
          ndkVariantExists =
            updateAffectedFacetsForNdkModule(ndkFacet, ndkModuleModel, variantAbiToSelect, affectedNdkFacets);

          // The variant name (without ABI) and ABI name to use for dependent modules.
          variantName = variantAbiToSelect.getVariant();
          abiName = variantAbiToSelect.getAbi();
        }
      }
    }

    if (androidFacet != null) {
      AndroidModuleModel androidModel = getAndroidModel(androidFacet);
      if (androidModel != null) {
        androidVariantExists =
          updateAffectedFacetsForAndroidModule(project, androidFacet, androidModel, variantName, abiName, affectedAndroidFacets,
                                               affectedNdkFacets);
      }
    }
    return ndkVariantExists && androidVariantExists;
  }

  private static boolean updateAffectedFacetsForNdkModule(@NotNull NdkFacet ndkFacet,
                                                          @NotNull NdkModuleModel ndkModuleModel,
                                                          @NotNull VariantAbi variantAbiToSelect,
                                                          @NotNull List<NdkFacet> affectedNdkFacets) {
    if (variantAbiToSelect.equals(ndkFacet.getSelectedVariantAbi()) && ndkModuleModel.getSyncedVariantAbis().contains(variantAbiToSelect)) {
      return true;
    }
    affectedNdkFacets.add(ndkFacet);
    ndkFacet.setSelectedVariantAbi(variantAbiToSelect);
    return ndkModuleModel.getSyncedVariantAbis().contains(variantAbiToSelect);
  }

  private static boolean updateAffectedFacetsForAndroidModule(@NotNull Project project,
                                                              @NotNull AndroidFacet androidFacet,
                                                              @NotNull AndroidModuleModel androidModel,
                                                              @NotNull String variantToSelect,
                                                              @Nullable String abiToSelect,
                                                              @NotNull List<AndroidFacet> affectedAndroidFacets,
                                                              @NotNull List<NdkFacet> affectedNdkFacets) {
    boolean isVariantChanged = !variantToSelect.equals(androidModel.getSelectedVariant().getName());
    boolean isAbiChanged = abiToSelect != null;

    if (!isVariantChanged && !isAbiChanged) {
      return true;
    }

    // Either the variant or ABI changed.
    // An ABI change does not affect Android facets. However, it may affect the NDK facets of dependent modules, which is
    // why we still need to explore the impact of this change on dependent modules.

    if (isVariantChanged) {
      // The current Android facet is affected only if the variant changed.
      affectedAndroidFacets.add(androidFacet);
      androidFacet.getProperties().SELECTED_BUILD_VARIANT = variantToSelect;
    }

    if (!androidModel.variantExists(variantToSelect)) {
      return false;
    }

    androidModel.setSelectedVariantName(variantToSelect);
    AndroidModuleDataServiceKt.syncSelectedVariant(androidFacet, androidModel.getSelectedVariant());
    // The variant of dependency modules can be updated only if the target variant exists, otherwise, there's no way to get the dependency modules of target variant.
    updateSelectedVariantsForDependencyModules(project, androidModel, abiToSelect, affectedAndroidFacets, affectedNdkFacets);
    return true;
  }

  private static void updateSelectedVariantsForDependencyModules(@NotNull Project project,
                                                                 @NotNull AndroidModuleModel androidModel,
                                                                 @Nullable String abiToSelect,
                                                                 @NotNull List<AndroidFacet> affectedAndroidFacets,
                                                                 @NotNull List<NdkFacet> affectedNdkFacets) {
    for (IdeModuleLibrary library : androidModel.getSelectedMainCompileLevel2Dependencies().getModuleDependencies()) {
      if (isNotEmpty(library.getVariant()) && isNotEmpty(library.getProjectPath())) {
        Module dependencyModule = ProjectStructure.getInstance(project).getModuleFinder().findModuleFromLibrary(library);
        updateDependencyModule(
          project, library.getProjectPath(), dependencyModule, library.getVariant(), abiToSelect, affectedAndroidFacets, affectedNdkFacets);
      }
    }

    // Keep feature modules consistent with base module for Dynamic Apps.
    // TODO: if feature variant is exposed in the model, change this hard coded imposed 1:1 variant between base and feature
    for (String gradlePath : androidModel.getAndroidProject().getDynamicFeatures()) {
      if (isNotEmpty(gradlePath)) {
        Module dependencyModule = GradleUtil.findModuleByGradlePath(project, gradlePath);
        if (dependencyModule != null) {
          AndroidModuleModel depModuleModel = AndroidModuleModel.get(dependencyModule);
          String variantToSelect = androidModel.getSelectedVariant().getName();
          // Update only if the target variant is valid for dependency module.
          if (depModuleModel != null && depModuleModel.getVariantNames().contains(variantToSelect)) {
            updateDependencyModule(project, gradlePath, dependencyModule, variantToSelect, abiToSelect, affectedAndroidFacets,
                                   affectedNdkFacets);
          }
        }
      }
    }
  }

  private static void updateDependencyModule(@NotNull Project project,
                                             @NotNull String gradlePath,
                                             @Nullable Module dependencyModule,
                                             @NotNull String projectVariant,
                                             @Nullable String abiToSelect,
                                             @NotNull List<AndroidFacet> affectedAndroidFacets,
                                             @NotNull List<NdkFacet> affectedNdkFacets) {
    if (dependencyModule == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find module with Gradle path '%1$s'.", gradlePath));
      return;
    }

    AndroidFacet dependencyFacet = AndroidFacet.getInstance(dependencyModule);
    if (dependencyFacet == null) {
      logAndShowBuildVariantFailure(
        String.format("Cannot find 'Android' facet in module '%1$s'.", dependencyModule.getName()));
      return;
    }

    AndroidModuleModel dependencyModel = getAndroidModel(dependencyFacet);
    if (dependencyModel != null) {
      // Update the affected NDK facets, if exists.
      NdkFacet dependencyNdkFacet = NdkFacet.getInstance(dependencyModule);
      NdkModuleModel dependencyNdkModel = dependencyNdkFacet == null ? null : getNdkModelIfItHasNativeVariantAbis(dependencyNdkFacet);
      if (dependencyNdkModel != null) {
        VariantAbi projectVariantWithAbi = resolveNewVariantAbi(dependencyNdkFacet, dependencyNdkModel, projectVariant, abiToSelect);
        if (projectVariantWithAbi != null) {
          updateAffectedFacetsForNdkModule(dependencyNdkFacet, dependencyNdkModel, projectVariantWithAbi, affectedNdkFacets);
        }
      }

      // Update the Android facets.
      updateAffectedFacetsForAndroidModule(
        project, dependencyFacet, dependencyModel, projectVariant, abiToSelect, affectedAndroidFacets, affectedNdkFacets);
    }
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

      // Failed to find the user-provided ABI in this module for the new variant.
      // Fall through intended.
    }

    // If the user did not provide an ABI in their selection, or the variant+ABI combination they selected for some parent/ancestor module
    // does not exist in the current module, then we try to preserve the ABI for this module.
    VariantAbi selectedVariantAbi = ndkFacet.getSelectedVariantAbi();
    if (selectedVariantAbi == null) return null;
    String existingAbi = selectedVariantAbi.getAbi();
    VariantAbi proposedVariantAbi = new VariantAbi(newVariant, existingAbi);  // e.g., debug-x86

    if (ndkModel.getAllVariantAbis().contains(proposedVariantAbi)) {
      return proposedVariantAbi;
    }

    // Cannot preserve ABI. It is probably filtered out by the user. Fall back to any other available ABI for that build variant.
    return ndkModel.getAllVariantAbis().stream().filter(variantAbi -> variantAbi.getVariant().equals(newVariant)).findFirst().orElse(null);
  }

  private static boolean hasBuildFilesChanged(@NotNull Project project) {
    return GradleSyncState.getInstance(project).isSyncNeeded().equals(YES);
  }

  private static void requestFullGradleSync(@NotNull Project project,
                                            @NotNull Runnable variantSelectionChangeListeners) {
    GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_VARIANT_SELECTION_FULL_SYNC,
                                                       getSyncListener(variantSelectionChangeListeners));
  }

  @NotNull
  private static GradleSyncListener getSyncListener(@NotNull Runnable variantSelectionChangeListeners) {
    return new GradleSyncListener() {
      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        project.putUserData(AndroidGradleProjectResolver.USE_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS, null);
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        project.putUserData(AndroidGradleProjectResolver.USE_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS, null);
        variantSelectionChangeListeners.run();
      }
    };
  }

  private static void requestVariantOnlyGradleSync(@NotNull Project project,
                                                   @NotNull String moduleName,
                                                   @NotNull Runnable variantSelectionChangeListeners) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      return;
    }

    GradleFacet gradleFacet = GradleFacet.getInstance(moduleToUpdate);
    if (gradleFacet == null) {
      return;
    }
    AndroidModuleModel androidModel = AndroidModuleModel.get(moduleToUpdate);
    GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
    if (androidModel != null && gradleModel != null) {
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER);
      GradleSyncInvoker.getInstance().requestProjectSync(project, request, getSyncListener(variantSelectionChangeListeners));
    }
  }

  private static void setupCachedVariant(@NotNull Project project,
                                         @NotNull List<AndroidFacet> affectedAndroidFacets,
                                         @NotNull Runnable variantSelectionChangeListeners) {
    Application application = ApplicationManager.getApplication();

    Task.Backgroundable task = new Task.Backgroundable(project, "Setting up Project", false/* cannot be canceled*/) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        getLog().info("Starting setup of cached variant");

        // While we work to move the rest of the setup we need to perform two commits, once using IDEAs data import and the other
        // using the remainder of out setup steps.
        VariantSwitcher.switchVariant(project, affectedAndroidFacets);

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
  private static AndroidModuleModel getAndroidModel(@NotNull AndroidFacet facet) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    if (androidModel == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find AndroidProject for module '%1$s'.", facet.getModule().getName()));
    }
    return androidModel;
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