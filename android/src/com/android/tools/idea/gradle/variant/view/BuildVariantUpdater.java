/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.gradle.util.BatchUpdatesUtil.finishBatchUpdate;
import static com.android.tools.idea.gradle.util.BatchUpdatesUtil.startBatchUpdate;
import static com.android.tools.idea.gradle.util.GradleProjects.executeProjectChanges;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_FULL_SYNC;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;
import static com.intellij.util.ThreeState.YES;

import com.android.builder.model.level2.Library;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.VariantOnlySyncOptions;
import com.android.tools.idea.gradle.project.sync.setup.module.android.AndroidVariantChangeModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkVariantChangeModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
public class BuildVariantUpdater {
  @NotNull public static final Key<String> MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI =
    new Key<>("module.with.build.variant.switched.from.ui");
  @NotNull private final ModuleSetupContext.Factory myModuleSetupContextFactory;
  @NotNull private final IdeModifiableModelsProviderFactory myModifiableModelsProviderFactory;
  @NotNull private final AndroidVariantChangeModuleSetup myAndroidModuleSetupSteps;
  @NotNull private final NdkVariantChangeModuleSetup myNdkModuleSetupSteps;
  @NotNull private final List<BuildVariantView.BuildVariantSelectionChangeListener> mySelectionChangeListeners =
    ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull
  public static BuildVariantUpdater getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BuildVariantUpdater.class);
  }

  // called by IDEA.
  @SuppressWarnings("unused")
  BuildVariantUpdater() {
    this(new ModuleSetupContext.Factory(), new IdeModifiableModelsProviderFactory(), new AndroidVariantChangeModuleSetup(),
         new NdkVariantChangeModuleSetup());
  }

  @VisibleForTesting
  BuildVariantUpdater(@NotNull ModuleSetupContext.Factory moduleSetupContextFactory,
                      @NotNull IdeModifiableModelsProviderFactory modifiableModelsProviderFactory,
                      @NotNull AndroidVariantChangeModuleSetup androidModuleSetup,
                      @NotNull NdkVariantChangeModuleSetup ndkModuleSetup) {
    myModuleSetupContextFactory = moduleSetupContextFactory;
    myModifiableModelsProviderFactory = modifiableModelsProviderFactory;
    myAndroidModuleSetupSteps = androidModuleSetup;
    myNdkModuleSetupSteps = ndkModuleSetup;
  }

  /**
   * Add an {@link BuildVariantView.BuildVariantSelectionChangeListener} to the updater. Listeners are
   * invoked when the project's selected build variant changes.
   */
  public void addSelectionChangeListener(@NotNull BuildVariantView.BuildVariantSelectionChangeListener listener) {
    mySelectionChangeListeners.add(listener);
  }

  /**
   * Remove the {@link BuildVariantView.BuildVariantSelectionChangeListener} from the updater.
   */
  public void removeSelectionChangeListener(@NotNull BuildVariantView.BuildVariantSelectionChangeListener listener) {
    mySelectionChangeListeners.remove(listener);
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

    NdkModuleModel ndkModuleModel = getNdkModelIfNotJustDummy(moduleToUpdate);
    if (ndkModuleModel == null) {
      // Non-native module. ABI is irrelevant. Proceed with the build variant without ABI.
      return updateSelectedVariant(project, moduleName, selectedBuildVariant);
    }

    // Native module: try to preserve the existing ABI for that module (if exists).
    String newNdkBuildVariant = resolveNewNdkVariant(ndkModuleModel, selectedBuildVariant, null);
    if (newNdkBuildVariant == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find suitable ABI for native module '%1$s'.", moduleName));
      return false;
    }

    return updateSelectedVariant(project, moduleName, newNdkBuildVariant);
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

    NdkModuleModel ndkModuleModel = getNdkModelIfNotJustDummy(moduleToUpdate);
    if (ndkModuleModel == null) {
      // This is unexpected. If we presented ABI dropdown for this module, then it must have an NDK model.
      logAndShowAbiNameFailure(String.format("Cannot find native module model '%1$s'.", moduleName));
      return false;
    }

    // Keep using the same existing build variant.
    String existingNdkBuildVariant = ndkModuleModel.getSelectedVariant().getName();
    String existingBuildVariant = ndkModuleModel.getVariantName(existingNdkBuildVariant);
    String newNdkBuildVariant = resolveNewNdkVariant(ndkModuleModel, existingBuildVariant, selectedAbiName);
    if (newNdkBuildVariant == null) {
      logAndShowAbiNameFailure(String.format("Cannot find suitable ABI for native module '%1$s'.", moduleName));
      return false;
    }

    return updateSelectedVariant(project, moduleName, newNdkBuildVariant);
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
      setVariantSwitchedProperty(project, moduleName);
      requestVariantOnlyGradleSync(project, moduleName, buildVariantName, invokeVariantSelectionChangeListeners);
    }
    else {
      setupCachedVariant(project, affectedAndroidFacets, affectedNdkFacets, invokeVariantSelectionChangeListeners);
    }
    return true;
  }

  private static void setVariantSwitchedProperty(@NotNull Project project,
                                                 @NotNull String moduleName) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      return;
    }
    GradleFacet gradleFacet = GradleFacet.getInstance(moduleToUpdate);
    if (gradleFacet == null) {
      return;
    }
    GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
    if (gradleModel != null) {
      project.putUserData(MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI,
                          createUniqueModuleId(gradleModel.getRootFolderPath(), gradleModel.getGradlePath()));
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
      NdkModuleModel ndkModuleModel = getNdkModelIfNotJustDummy(ndkFacet);
      if (ndkModuleModel != null) {
        ndkVariantExists =
          updateAffectedFacetsForNdkModule(ndkFacet, ndkModuleModel, variantToSelect, affectedNdkFacets);

        // The variant name (without ABI) and ABI name to use for dependent modules.
        variantName = ndkModuleModel.getVariantName(variantToSelect);
        abiName = ndkModuleModel.getAbiName(variantToSelect);
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
                                                          @NotNull String variantToSelect,
                                                          @NotNull List<NdkFacet> affectedNdkFacets) {
    if (variantToSelect.equals(ndkModuleModel.getSelectedVariant().getName())) {
      return true;
    }

    affectedNdkFacets.add(ndkFacet);
    ndkFacet.getConfiguration().SELECTED_BUILD_VARIANT = variantToSelect;
    if (!ndkModuleModel.variantExists(variantToSelect)) {
      return false;
    }
    ndkModuleModel.setSelectedVariantName(variantToSelect);
    return true;
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
    androidModel.syncSelectedVariantAndTestArtifact(androidFacet);
    // The variant of dependency modules can be updated only if the target variant exists, otherwise, there's no way to get the dependency modules of target variant.
    updateSelectedVariantsForDependencyModules(project, androidModel, abiToSelect, affectedAndroidFacets, affectedNdkFacets);
    return true;
  }

  private static void updateSelectedVariantsForDependencyModules(@NotNull Project project,
                                                                 @NotNull AndroidModuleModel androidModel,
                                                                 @Nullable String abiToSelect,
                                                                 @NotNull List<AndroidFacet> affectedAndroidFacets,
                                                                 @NotNull List<NdkFacet> affectedNdkFacets) {
    for (Library library : androidModel.getSelectedMainCompileLevel2Dependencies().getModuleDependencies()) {
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
        Module dependencyModule = ProjectStructure.getInstance(project).getModuleFinder().findModuleByGradlePath(gradlePath);
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
      NdkModuleModel dependencyNdkModel = dependencyNdkFacet == null ? null : getNdkModelIfNotJustDummy(dependencyNdkFacet);
      if (dependencyNdkModel != null) {
        String projectVariantWithAbi = resolveNewNdkVariant(dependencyNdkModel, projectVariant, abiToSelect);
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
  private static String resolveNewNdkVariant(@NotNull NdkModuleModel ndkModel,
                                             @NotNull String newVariant,
                                             @Nullable String userSelectedAbi) {
    if (userSelectedAbi != null) {
      String ndkVariant = getNdkBuildVariantName(newVariant, userSelectedAbi);  // e.g., debug-x86
      if (ndkModel.getNdkVariantNames().contains(ndkVariant)) {
        return ndkVariant;
      }

      // Failed to find the user-provided ABI in this module for the new variant.
      // Fall through intended.
    }

    // If the user did not provide an ABI in their selection, or the variant+ABI combination they selected for some parent/ancestor module
    // does not exist in the current module, then we try to preserve the ABI for this module.
    String existingAbi = ndkModel.getAbiName(ndkModel.getSelectedVariant().getName());  // e.g., x86
    String ndkVariant = getNdkBuildVariantName(newVariant, existingAbi);  // e.g., debug-x86

    if (ndkModel.getNdkVariantNames().contains(ndkVariant)) {
      return ndkVariant;
    }

    // Cannot preserve ABI. It is probably filtered out by the user. Fall back to any other available ABI for that build variant.
    String expectedPrefix = newVariant + "-";
    Optional<String> variant = ndkModel.getNdkVariantNames().stream().filter(it -> it.startsWith(expectedPrefix)).findFirst();
    return variant.orElse(null);
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
      public void syncSucceeded(@NotNull Project project) {
        variantSelectionChangeListeners.run();
      }
    };
  }

  private static void requestVariantOnlyGradleSync(@NotNull Project project,
                                                   @NotNull String moduleName,
                                                   @NotNull String buildVariantName,
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
    NdkModuleModel ndkModuleModel = getNdkModelIfNotJustDummy(moduleToUpdate);
    GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
    if (androidModel != null && gradleModel != null) {
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER);
      String variantName = buildVariantName;
      String abiName = null;
      if (ndkModuleModel != null) {
        variantName = ndkModuleModel.getVariantName(buildVariantName);
        abiName = ndkModuleModel.getAbiName(buildVariantName);
      }
      boolean isCompoundSyncEnabled = GradleSyncState.isCompoundSync();
      request.variantOnlySyncOptions =
        new VariantOnlySyncOptions(gradleModel.getRootFolderPath(), gradleModel.getGradlePath(), variantName, abiName,
                                   isCompoundSyncEnabled);
      GradleSyncInvoker.getInstance().requestProjectSync(project, request, getSyncListener(variantSelectionChangeListeners));
    }
  }

  private void setupCachedVariant(@NotNull Project project,
                                  @NotNull List<AndroidFacet> affectedAndroidFacets,
                                  @NotNull List<NdkFacet> affectedNdkFacets,
                                  @NotNull Runnable variantSelectionChangeListeners) {
    Application application = ApplicationManager.getApplication();

    Task.Backgroundable task = new Task.Backgroundable(project, "Setting up Project", false/* cannot be canceled*/) {
      // Values to use in indicator
      private double PROGRESS_SETUP_MODULES_START = 0.0;
      private double PROGRESS_SETUP_MODULES_SIZE = 0.2;
      private double PROGRESS_SETUP_PROJECT_START = PROGRESS_SETUP_MODULES_START + PROGRESS_SETUP_MODULES_SIZE;
      private double PROGRESS_SETUP_PROJECT_SIZE = 0.2;
      private double PROGRESS_COMMIT_START = PROGRESS_SETUP_PROJECT_START + PROGRESS_SETUP_PROJECT_SIZE;
      private double PROGRESS_COMMIT_SIZE = 0.4;
      private double PROGRESS_GENERATE_SOURCES_START = PROGRESS_COMMIT_START + PROGRESS_COMMIT_SIZE;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        getLog().info("Starting setup of cached variant");
        // Setup modules
        List<IdeModifiableModelsProvider> modelsProviders = setUpModules(affectedAndroidFacets, affectedNdkFacets, indicator);

        // Setup Project
        setUpProject(project, indicator);

        // Commit changes and dispose models providers
        commitChanges(project, modelsProviders, indicator);

        // Run generate sources if needed
        generateSourcesIfNeeded(project, affectedAndroidFacets, indicator);

        if (application.isUnitTestMode()) {
          variantSelectionChangeListeners.run();
        }
        else {
          application.invokeLater(variantSelectionChangeListeners);
        }

        getLog().info("Finished setup of cached variant");
      }

      private List<IdeModifiableModelsProvider> setUpModules(@NotNull List<AndroidFacet> affectedAndroidFacets,
                                                             @NotNull List<NdkFacet> affectedNdkFacets,
                                                             @NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        indicator.setText("Setting up modules");
        indicator.setFraction(PROGRESS_SETUP_MODULES_START);
        List<IdeModifiableModelsProvider> modelsProviders = new ArrayList<>();
        for (NdkFacet ndkFacet : affectedNdkFacets) {
          NdkModuleModel ndkModuleModel = getNdkModelIfNotJustDummy(ndkFacet);
          if (ndkModuleModel != null) {
            modelsProviders.add(setUpModule(ndkFacet.getModule(), ndkModuleModel));
          }
        }

        for (AndroidFacet androidFacet : affectedAndroidFacets) {
          AndroidModuleModel androidModel = getAndroidModel(androidFacet);
          if (androidModel != null) {
            modelsProviders.add(setUpModule(androidFacet.getModule(), androidModel));
          }
        }
        return modelsProviders;
      }

      private void setUpProject(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        indicator.setText("Setting up project");
        indicator.setFraction(PROGRESS_SETUP_PROJECT_START);
        PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
        setupRequest.generateSourcesAfterSync = false;
        setupRequest.cleanProjectAfterSync = false;
        PostSyncProjectSetup.getInstance(project).setUpProject(setupRequest, null, null);
      }

      private void commitChanges(@NotNull Project project,
                                 @NotNull List<IdeModifiableModelsProvider> providers,
                                 @NotNull ProgressIndicator indicator) {
        startBatchUpdate(project);
        try {
          doCommitChanges(project, providers, indicator);
        }
        finally {
          finishBatchUpdate(project);
        }
      }

      private void doCommitChanges(@NotNull Project project,
                                   @NotNull List<IdeModifiableModelsProvider> providers,
                                   @NotNull ProgressIndicator indicator) {
        indicator.setText("Committing changes");
        indicator.setFraction(PROGRESS_COMMIT_START);
        double step = PROGRESS_COMMIT_SIZE / (providers.size() + 1);
        double progress = PROGRESS_COMMIT_START;
        for (IdeModifiableModelsProvider provider : providers) {
          executeProjectChanges(project, () -> {
            try {
              provider.commit();
            }
            catch (Throwable t) {
              provider.dispose();
              //noinspection ConstantConditions
              rethrowAllAsUnchecked(t);
            }
          });
          progress += step;
          indicator.setFraction(progress);
        }
      }

      private void generateSourcesIfNeeded(@NotNull Project project,
                                           @NotNull List<AndroidFacet> affectedAndroidFacets,
                                           @NotNull ProgressIndicator indicator) {
        if (!affectedAndroidFacets.isEmpty()) {
          // We build only the selected variant. If user changes variant, we need to re-generate sources since the generated sources may not
          // be there.
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            indicator.setFraction(PROGRESS_GENERATE_SOURCES_START);
            GradleProjectBuilder.getInstance(project).generateSources();
          }
        }
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

  private IdeModifiableModelsProvider setUpModule(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
    IdeModifiableModelsProvider modelsProvider = myModifiableModelsProviderFactory.create(module.getProject());
    ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
    try {
      myAndroidModuleSetupSteps.setUpModule(context, androidModel);
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      //noinspection ConstantConditions
      rethrowAllAsUnchecked(t);
    }
    return modelsProvider;
  }

  private IdeModifiableModelsProvider setUpModule(@NotNull Module module, @NotNull NdkModuleModel ndkModuleModel) {
    IdeModifiableModelsProvider modelsProvider = myModifiableModelsProviderFactory.create(module.getProject());
    ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
    try {
      myNdkModuleSetupSteps.setUpModule(context, ndkModuleModel);
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      //noinspection ConstantConditions
      rethrowAllAsUnchecked(t);
    }
    return modelsProvider;
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
  private static NdkModuleModel getNdkModelIfNotJustDummy(@NotNull NdkFacet facet) {
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(facet);
    if (ndkModuleModel == null) {
      logAndShowBuildVariantFailure(
        String.format("Cannot find NativeAndroidProject for module '%1$s'.", facet.getModule().getName()));
      return null;
    }
    if (ndkModuleModel.getSelectedVariant().getName().equals(NdkModuleModel.DummyNdkVariant.variantNameWithAbi)) {
      // Native module that does not have any real ABIs. Proceed with the build variant without ABI.
      return null;
    }
    return ndkModuleModel;
  }

  @Nullable
  private static NdkModuleModel getNdkModelIfNotJustDummy(@NotNull Module module) {
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
    if (ndkModuleModel == null) {
      return null;
    }
    if (ndkModuleModel.getSelectedVariant().getName().equals(NdkModuleModel.DummyNdkVariant.variantNameWithAbi)) {
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

  @VisibleForTesting
  static class IdeModifiableModelsProviderFactory {
    @NotNull
    IdeModifiableModelsProvider create(@NotNull Project project) {
      return new IdeModifiableModelsProviderImpl(project);
    }
  }

  /**
   * @return An NDK build variant name from the provided build variant name and ABI.
   */
  @NotNull
  private static String getNdkBuildVariantName(@NotNull String buildVariantNameWithoutAbi, @NotNull String abiName) {
    return buildVariantNameWithoutAbi + "-" + abiName;
  }
}
