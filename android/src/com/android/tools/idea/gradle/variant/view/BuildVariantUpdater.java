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

import com.android.builder.model.Variant;
import com.android.builder.model.level2.Library;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel.NdkVariant;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.CompilerOutputModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.ContentRootsModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.DependenciesAndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.ContentRootModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.gradle.util.GradleProjects.executeProjectChanges;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
class BuildVariantUpdater {
  @NotNull private final ModuleSetupContext.Factory myModuleSetupContextFactory;
  @NotNull private final IdeModifiableModelsProviderFactory myModifiableModelsProviderFactory;
  @NotNull private final List<AndroidModuleSetupStep> myAndroidModuleSetupSteps;
  @NotNull private final NdkModuleSetupStep[] myNdkModuleSetupSteps = {new ContentRootModuleSetupStep()};

  BuildVariantUpdater() {
    this(new ModuleSetupContext.Factory(), new IdeModifiableModelsProviderFactory(),
         Arrays.asList(new ContentRootsModuleSetupStep(), new DependenciesAndroidModuleSetupStep(), new CompilerOutputModuleSetupStep()));
  }

  @VisibleForTesting
  BuildVariantUpdater(@NotNull ModuleSetupContext.Factory moduleSetupContextFactory,
                      @NotNull IdeModifiableModelsProviderFactory modifiableModelsProviderFactory,
                      @NotNull List<AndroidModuleSetupStep> androidModuleSetupSteps) {
    myModuleSetupContextFactory = moduleSetupContextFactory;
    myModifiableModelsProviderFactory = modifiableModelsProviderFactory;
    myAndroidModuleSetupSteps = androidModuleSetupSteps;
  }

  /**
   * Updates a module's structure when the user selects a build variant from the tool window.
   *
   * @param project          the module's project.
   * @param moduleName       the module's name.
   * @param buildVariantName the name of the selected build variant.
   * @return {@code true} if the module update was successful, {@code false} otherwise.
   */
  boolean updateSelectedVariant(@NotNull Project project,
                                @NotNull String moduleName,
                                @NotNull String buildVariantName) {
    List<AndroidFacet> affectedAndroidFacets = new ArrayList<>();
    List<NdkFacet> affectedNdkFacets = new ArrayList<>();
    executeProjectChanges(project, () -> {
      doUpdate(project, moduleName, buildVariantName, affectedAndroidFacets, affectedNdkFacets);
      PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
      setupRequest.generateSourcesAfterSync = false;
      setupRequest.cleanProjectAfterSync = false;

      PostSyncProjectSetup.getInstance(project).setUpProject(setupRequest, new EmptyProgressIndicator());
      generateSourcesIfNeeded(project, affectedAndroidFacets);
    });
    return !affectedAndroidFacets.isEmpty() || !affectedNdkFacets.isEmpty();
  }

  private void doUpdate(@NotNull Project project,
                        @NotNull String moduleName,
                        @NotNull String variant,
                        @NotNull List<AndroidFacet> affectedAndroidFacets,
                        @NotNull List<NdkFacet> affectedNdkFacets) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      logAndShowUpdateFailure(variant, String.format("Cannot find module '%1$s'.", moduleName));
      return;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(moduleToUpdate);
    NdkFacet ndkFacet = NdkFacet.getInstance(moduleToUpdate);

    if (androidFacet == null && ndkFacet == null) {
      String msg = String.format("Cannot find 'Android' or 'Native-Android-Gradle' facets in module '%1$s'.", moduleToUpdate.getName());
      logAndShowUpdateFailure(variant, msg);
    }
    if (ndkFacet != null) {
      NdkModuleModel ndkModuleModel = getNativeAndroidModel(ndkFacet, variant);
      if (ndkModuleModel == null || !updateSelectedVariant(ndkFacet, ndkModuleModel, variant)) {
        return;
      }
      affectedNdkFacets.add(ndkFacet);
    }
    if (androidFacet != null) {
      AndroidModuleModel androidModel = getAndroidModel(androidFacet, variant);
      if (androidModel == null || !updateSelectedVariant(androidFacet, androidModel, variant, affectedAndroidFacets)) {
        return;
      }
      affectedAndroidFacets.add(androidFacet);
    }
  }

  @Nullable
  private static Module findModule(@NotNull Project project, @NotNull String moduleName) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    return moduleManager.findModuleByName(moduleName);
  }

  private boolean updateSelectedVariant(@NotNull AndroidFacet androidFacet,
                                        @NotNull AndroidModuleModel androidModel,
                                        @NotNull String variantToSelect,
                                        @NotNull List<AndroidFacet> affectedFacets) {
    Variant selectedVariant = androidModel.getSelectedVariant();
    if (variantToSelect.equals(selectedVariant.getName())) {
      return false;
    }
    androidModel.setSelectedVariantName(variantToSelect);
    androidModel.syncSelectedVariantAndTestArtifact(androidFacet);
    Module module = setUpModule(androidFacet.getModule(), androidModel);

    for (Library library : androidModel.getSelectedMainCompileLevel2Dependencies().getModuleDependencies()) {
      String gradlePath = library.getProjectPath();
      if (isEmpty(gradlePath)) {
        continue;
      }
      String projectVariant = library.getVariant();
      if (isNotEmpty(projectVariant)) {
        ensureVariantIsSelected(module.getProject(), gradlePath, projectVariant, affectedFacets);
      }
    }
    return true;
  }

  private boolean updateSelectedVariant(@NotNull NdkFacet ndkFacet,
                                        @NotNull NdkModuleModel ndkModuleModel,
                                        @NotNull String variantToSelect) {
    NdkVariant selectedVariant = ndkModuleModel.getSelectedVariant();
    if (variantToSelect.equals(selectedVariant.getName())) {
      return false;
    }
    ndkModuleModel.setSelectedVariantName(variantToSelect);
    ndkFacet.getConfiguration().SELECTED_BUILD_VARIANT = ndkModuleModel.getSelectedVariant().getName();
    setUpModule(ndkFacet.getModule(), ndkModuleModel);

    // TODO: Also update the dependent modules variants.
    return true;
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

  @NotNull
  private Module setUpModule(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
    IdeModifiableModelsProvider modelsProvider = myModifiableModelsProviderFactory.create(module.getProject());
    ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
    try {
      for (AndroidModuleSetupStep setupStep : myAndroidModuleSetupSteps) {
        if (setupStep.invokeOnBuildVariantChange()) {
          // TODO get modules by gradle path
          setupStep.setUpModule(context, androidModel);
        }
      }
      modelsProvider.commit();
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      rethrowAllAsUnchecked(t);
    }
    return module;
  }

  private void setUpModule(@NotNull Module module, @NotNull NdkModuleModel ndkModuleModel) {
    IdeModifiableModelsProviderImpl modelsProvider = new IdeModifiableModelsProviderImpl(module.getProject());
    ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
    try {
      for (NdkModuleSetupStep setupStep : myNdkModuleSetupSteps) {
        if (setupStep.invokeOnBuildVariantChange()) {
          // TODO get modules by gradle path
          setupStep.setUpModule(context, ndkModuleModel);
        }
      }
      modelsProvider.commit();
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      rethrowAllAsUnchecked(t);
    }
  }

  private void ensureVariantIsSelected(@NotNull Project project,
                                       @NotNull String moduleGradlePath,
                                       @NotNull String variant,
                                       @NotNull List<AndroidFacet> affectedFacets) {
    Module module = findModuleByGradlePath(project, moduleGradlePath);
    if (module == null) {
      logAndShowUpdateFailure(variant, String.format("Cannot find module with Gradle path '%1$s'.", moduleGradlePath));
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      logAndShowUpdateFailure(variant, String.format("Cannot find 'Android' facet in module '%1$s'.", module.getName()));
      return;
    }

    AndroidModuleModel androidModel = getAndroidModel(facet, variant);
    if (androidModel == null) {
      return;
    }

    if (!updateSelectedVariant(facet, androidModel, variant, affectedFacets)) {
      return;
    }
    affectedFacets.add(facet);
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
