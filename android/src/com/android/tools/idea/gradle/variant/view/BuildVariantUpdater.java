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
import com.android.tools.idea.gradle.project.sync.ng.NewGradleSync;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlySyncOptions;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleProjects.executeProjectChanges;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;
import static com.intellij.util.ThreeState.YES;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
public final class BuildVariantUpdater {
  @NotNull private final ModuleSetupContext.Factory myModuleSetupContextFactory;
  @NotNull private final IdeModifiableModelsProviderFactory myModifiableModelsProviderFactory;
  @NotNull private final AndroidVariantChangeModuleSetup myAndroidModuleSetupSteps;
  @NotNull private final NdkVariantChangeModuleSetup myNdkModuleSetupSteps;
  @NotNull private final List<BuildVariantView.BuildVariantSelectionChangeListener> mySelectionChangeListeners;

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
  @NonInjectable
  BuildVariantUpdater(@NotNull ModuleSetupContext.Factory moduleSetupContextFactory,
                      @NotNull IdeModifiableModelsProviderFactory modifiableModelsProviderFactory,
                      @NotNull AndroidVariantChangeModuleSetup androidModuleSetup,
                      @NotNull NdkVariantChangeModuleSetup ndkModuleSetup) {
    myModuleSetupContextFactory = moduleSetupContextFactory;
    myModifiableModelsProviderFactory = modifiableModelsProviderFactory;
    myAndroidModuleSetupSteps = androidModuleSetup;
    myNdkModuleSetupSteps = ndkModuleSetup;
    mySelectionChangeListeners = new ArrayList<>();
  }

  /**
   * Add an {@link BuildVariantView.BuildVariantSelectionChangeListener} to the updater. Listeners are
   * invoked when the project's selected build variant changes.
   */
  public void addSelectionChangeListener(@NotNull BuildVariantView.BuildVariantSelectionChangeListener listener) {
    synchronized (mySelectionChangeListeners) {
      mySelectionChangeListeners.add(listener);
    }
  }

  /**
   * Remove the {@link BuildVariantView.BuildVariantSelectionChangeListener} from the updater.
   */
  public void removeSelectionChangeListener(@NotNull BuildVariantView.BuildVariantSelectionChangeListener listener) {
    synchronized (mySelectionChangeListeners) {
      mySelectionChangeListeners.remove(listener);
    }
  }

  /**
   * Updates a module's structure when the user selects a build variant from the tool window.
   *
   * @param project          the module's project.
   * @param moduleName       the module's name.
   * @param buildVariantName the name of the selected build variant.
   */
  void updateSelectedVariant(@NotNull Project project,
                             @NotNull String moduleName,
                             @NotNull String buildVariantName) {
    List<AndroidFacet> affectedAndroidFacets = new ArrayList<>();
    List<NdkFacet> affectedNdkFacets = new ArrayList<>();
    // find all of affected facets, and update the value of selected build variant.
    boolean variantToUpdateExists =
      findAndUpdateAffectedFacets(project, moduleName, buildVariantName, affectedAndroidFacets, affectedNdkFacets);
    // nothing to update.
    if (affectedAndroidFacets.isEmpty() && affectedNdkFacets.isEmpty()) {
      return;
    }

    Runnable invokeVariantSelectionChangeListeners = () -> {
      synchronized (mySelectionChangeListeners) {
        for (BuildVariantView.BuildVariantSelectionChangeListener listener : mySelectionChangeListeners) {
          listener.selectionChanged();
        }
      }
    };

    // There are three different cases,
    // 1. Build files have been changed, request a full Gradle Sync - let Gradle Sync infrastructure handle single variant or not.
    // 2. Build files were not changed, variant to select doesn't exist, which can only happen with single-variant sync, request Variant-only Sync.
    // 3. Build files were not changed, variant to select exists, do module setup for affected modules.
    if (hasBuildFilesChanged(project)) {
      requestFullGradleSync(project, invokeVariantSelectionChangeListeners);
    }
    else if (!variantToUpdateExists) {
      requestVariantOnlyGradleSync(project, moduleName, buildVariantName, invokeVariantSelectionChangeListeners);
    }
    else {
      executeProjectChanges(project, () -> {
        setUpModules(buildVariantName, affectedAndroidFacets, affectedNdkFacets);
        PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
        setupRequest.generateSourcesAfterSync = false;
        setupRequest.cleanProjectAfterSync = false;
        PostSyncProjectSetup.getInstance(project).setUpProject(setupRequest, new EmptyProgressIndicator(), null);
        generateSourcesIfNeeded(project, affectedAndroidFacets);
      });

      Application application = ApplicationManager.getApplication();
      if (application.isUnitTestMode()) {
        invokeVariantSelectionChangeListeners.run();
      }
      else {
        application.invokeLater(invokeVariantSelectionChangeListeners);
      }
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
      logAndShowUpdateFailure(variantToSelect, String.format("Cannot find module '%1$s'.", moduleName));
      return false;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(moduleToUpdate);
    NdkFacet ndkFacet = NdkFacet.getInstance(moduleToUpdate);

    if (androidFacet == null && ndkFacet == null) {
      String msg = String.format("Cannot find 'Android' or 'Native-Android-Gradle' facets in module '%1$s'.", moduleToUpdate.getName());
      logAndShowUpdateFailure(variantToSelect, msg);
    }

    boolean ndkVariantExists = true;
    boolean androidVariantExists = true;
    String variantName = variantToSelect;
    if (ndkFacet != null) {
      NdkModuleModel ndkModuleModel = getNativeAndroidModel(ndkFacet, variantToSelect);
      if (ndkModuleModel != null) {
        ndkVariantExists = updateAffectedFacetsForNdkModule(ndkFacet, ndkModuleModel, variantToSelect, affectedNdkFacets);
        // The variant name to use for AndroidModuleModel.
        // For example, variantToSelect for ndk module is debug-x86, and for AndroidModuleModel it should be debug.
        variantName = ndkModuleModel.getVariantName(variantToSelect);
      }
    }

    if (androidFacet != null) {
      AndroidModuleModel androidModel = getAndroidModel(androidFacet, variantName);
      if (androidModel != null) {
        androidVariantExists =
          updateAffectedFacetsForAndroidModule(project, androidFacet, androidModel, variantName, affectedAndroidFacets);
      }
    }
    return ndkVariantExists && androidVariantExists;
  }

  private static boolean updateAffectedFacetsForNdkModule(@NotNull NdkFacet ndkFacet,
                                                          @NotNull NdkModuleModel ndkModuleModel,
                                                          @NotNull String variantToSelect,
                                                          @NotNull List<NdkFacet> affectedFacets) {
    if (variantToSelect.equals(ndkModuleModel.getSelectedVariant().getName())) {
      return true;
    }
    affectedFacets.add(ndkFacet);
    ndkFacet.getConfiguration().SELECTED_BUILD_VARIANT = variantToSelect;
    boolean variantToSelectExists = ndkModuleModel.variantExists(variantToSelect);
    if (variantToSelectExists) {
      ndkModuleModel.setSelectedVariantName(variantToSelect);
    }
    // TODO: Also update the dependent modules variants.
    return variantToSelectExists;
  }

  private static boolean updateAffectedFacetsForAndroidModule(@NotNull Project project,
                                                              @NotNull AndroidFacet androidFacet,
                                                              @NotNull AndroidModuleModel androidModel,
                                                              @NotNull String variantToSelect,
                                                              @NotNull List<AndroidFacet> affectedFacets) {
    if (variantToSelect.equals(androidModel.getSelectedVariant().getName())) {
      return true;
    }
    affectedFacets.add(androidFacet);
    androidFacet.getProperties().SELECTED_BUILD_VARIANT = variantToSelect;
    boolean variantToSelectExists = androidModel.variantExists(variantToSelect);
    if (variantToSelectExists) {
      androidModel.setSelectedVariantName(variantToSelect);
      androidModel.syncSelectedVariantAndTestArtifact(androidFacet);
      // The variant of dependency modules can be updated only if the target variant exists, otherwise, there's no way to get the dependency modules of target variant.
      updateSelectedVariantsForDependencyModules(project, androidModel, affectedFacets);
    }
    return variantToSelectExists;
  }

  private static void updateSelectedVariantsForDependencyModules(@NotNull Project project,
                                                                 @NotNull AndroidModuleModel androidModel,
                                                                 @NotNull List<AndroidFacet> affectedFacets) {
    for (Library library : androidModel.getSelectedMainCompileLevel2Dependencies().getModuleDependencies()) {
      if (isNotEmpty(library.getVariant()) && isNotEmpty(library.getProjectPath())) {
        Module dependencyModule = ProjectStructure.getInstance(project).getModuleFinder().findModuleFromLibrary(library);
        updateDependencyModule(project, library.getProjectPath(), dependencyModule, library.getVariant(), affectedFacets);
      }
    }

    // Keep feature modules consistent with base module for Dynamic Apps
    // TODO: if feature variant is exposed in the model, change this hard coded imposed 1:1 variant between base and feature
    for (String gradlePath : androidModel.getAndroidProject().getDynamicFeatures()) {
      if (isNotEmpty(gradlePath)) {
        Module dependencyModule = ProjectStructure.getInstance(project).getModuleFinder().findModuleByGradlePath(gradlePath);
        updateDependencyModule(project, gradlePath, dependencyModule, androidModel.getSelectedVariant().getName(), affectedFacets);
      }
    }
  }

  private static void updateDependencyModule(@NotNull Project project,
                                             @NotNull String gradlePath,
                                             @Nullable Module dependencyModule,
                                             @NotNull String projectVariant,
                                             @NotNull List<AndroidFacet> affectedFacets) {
    if (dependencyModule == null) {
      logAndShowUpdateFailure(projectVariant, String.format("Cannot find module with Gradle path '%1$s'.", gradlePath));
      return;
    }

    AndroidFacet dependencyFacet = AndroidFacet.getInstance(dependencyModule);
    if (dependencyFacet == null) {
      logAndShowUpdateFailure(projectVariant,
                              String.format("Cannot find 'Android' facet in module '%1$s'.", dependencyModule.getName()));
      return;
    }

    AndroidModuleModel dependencyModel = getAndroidModel(dependencyFacet, projectVariant);
    if (dependencyModel != null) {
      updateAffectedFacetsForAndroidModule(project, dependencyFacet, dependencyModel, projectVariant, affectedFacets);
    }
  }

  private static boolean hasBuildFilesChanged(@NotNull Project project) {
    return GradleSyncState.getInstance(project).isSyncNeeded().equals(YES);
  }

  private static void requestFullGradleSync(@NotNull Project project,
                                            @NotNull Runnable variantSelectionChangeListeners) {
    GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED,
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
    if (moduleToUpdate != null) {
      GradleFacet gradleFacet = GradleFacet.getInstance(moduleToUpdate);
      if (gradleFacet != null) {
        AndroidModuleModel androidModel = AndroidModuleModel.get(moduleToUpdate);
        NdkModuleModel ndkModuleModel = NdkModuleModel.get(moduleToUpdate);
        GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
        if (androidModel != null && gradleModel != null) {
          GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER);
          String variantName = buildVariantName;
          String abiName = null;
          if (ndkModuleModel != null) {
            variantName = ndkModuleModel.getVariantName(buildVariantName);
            abiName = ndkModuleModel.getAbiName(buildVariantName);
          }
          boolean isCompoundSyncEnabled = NewGradleSync.isCompoundSync(project);
          request.variantOnlySyncOptions =
            new VariantOnlySyncOptions(gradleModel.getRootFolderPath(), gradleModel.getGradlePath(), variantName, abiName, isCompoundSyncEnabled);
          // TODO: It is not necessary to generate source for all modules (when not in compound-sync), only the modules that have variant
          // changes need to be re-generated. Need to change generateSource functions to accept specified modules.
          request.generateSourcesOnSuccess = true;
          GradleSyncInvoker.getInstance().requestProjectSync(project, request, getSyncListener(variantSelectionChangeListeners));
        }
      }
    }
  }

  private void setUpModules(@NotNull String variant,
                            @NotNull List<AndroidFacet> affectedAndroidFacets,
                            @NotNull List<NdkFacet> affectedNdkFacets) {
    for (NdkFacet ndkFacet : affectedNdkFacets) {
      NdkModuleModel ndkModuleModel = getNativeAndroidModel(ndkFacet, variant);
      if (ndkModuleModel != null) {
        setUpModule(ndkFacet.getModule(), ndkModuleModel);
      }
    }

    for (AndroidFacet androidFacet : affectedAndroidFacets) {
      AndroidModuleModel androidModel = getAndroidModel(androidFacet, variant);
      if (androidModel != null) {
        setUpModule(androidFacet.getModule(), androidModel);
      }
    }
  }

  @Nullable
  private static Module findModule(@NotNull Project project, @NotNull String moduleName) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    return moduleManager.findModuleByName(moduleName);
  }

  private static void generateSourcesIfNeeded(@NotNull Project project, @NotNull List<AndroidFacet> affectedFacets) {
    if (!affectedFacets.isEmpty()) {
      // We build only the selected variant. If user changes variant, we need to re-generate sources since the generated sources may not
      // be there.
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        GradleProjectBuilder.getInstance(project).generateSources();
      }
    }
  }

  private void setUpModule(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
    IdeModifiableModelsProvider modelsProvider = myModifiableModelsProviderFactory.create(module.getProject());
    ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
    try {
      myAndroidModuleSetupSteps.setUpModule(context, androidModel);
      modelsProvider.commit();
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      //noinspection ConstantConditions
      rethrowAllAsUnchecked(t);
    }
  }

  private void setUpModule(@NotNull Module module, @NotNull NdkModuleModel ndkModuleModel) {
    IdeModifiableModelsProvider modelsProvider = myModifiableModelsProviderFactory.create(module.getProject());
    ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
    try {
      myNdkModuleSetupSteps.setUpModule(context, ndkModuleModel);
      modelsProvider.commit();
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      //noinspection ConstantConditions
      rethrowAllAsUnchecked(t);
    }
  }

  @Nullable
  private static AndroidModuleModel getAndroidModel(@NotNull AndroidFacet facet, @NotNull String variantToSelect) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    if (androidModel == null) {
      logAndShowUpdateFailure(variantToSelect, String.format("Cannot find AndroidProject for module '%1$s'.", facet.getModule().getName()));
    }
    return androidModel;
  }

  @Nullable
  private static NdkModuleModel getNativeAndroidModel(@NotNull NdkFacet facet, @NotNull String variantToSelect) {
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(facet);
    if (ndkModuleModel == null) {
      logAndShowUpdateFailure(variantToSelect,
                              String.format("Cannot find NativeAndroidProject for module '%1$s'.", facet.getModule().getName()));
    }
    return ndkModuleModel;
  }

  private static void logAndShowUpdateFailure(@NotNull String buildVariantName, @NotNull String reason) {
    String prefix = String.format("Unable to select build variant '%1$s':\n", buildVariantName);
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
}
