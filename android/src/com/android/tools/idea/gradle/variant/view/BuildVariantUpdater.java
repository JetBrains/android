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

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel.NativeVariant;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.gradle.util.Projects.executeProjectChanges;
import static com.android.tools.idea.gradle.variant.conflict.ConflictSet.findConflicts;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
class BuildVariantUpdater {
  private static final Logger LOG = Logger.getInstance(BuildVariantUpdater.class);

  /**
   * Updates a module's structure when the user selects a build variant from the tool window.
   *
   * @param project          the module's project.
   * @param moduleName       the module's name.
   * @param buildVariantName the name of the selected build variant.
   * @return {@code true} if the module update was successful, {@code false} otherwise.
   */
  boolean updateSelectedVariant(@NotNull final Project project,
                                @NotNull final String moduleName,
                                @NotNull final String buildVariantName) {
    final List<AndroidFacet> affectedAndroidFacets = Lists.newArrayList();
    final List<NativeAndroidGradleFacet> affectedNativeAndroidFacets = Lists.newArrayList();
    executeProjectChanges(project, new Runnable() {
      @Override
      public void run() {
        Module updatedModule = doUpdate(project, moduleName, buildVariantName, affectedAndroidFacets, affectedNativeAndroidFacets);
        if (updatedModule != null) {
          ConflictSet conflicts = findConflicts(project);
          conflicts.showSelectionConflicts();
        }

        generateSourcesIfNeeded(affectedAndroidFacets);
      }
    });
    return !affectedAndroidFacets.isEmpty() || !affectedNativeAndroidFacets.isEmpty();
  }

  /**
   * Updates the given modules to use the new test artifact name.
   *
   * @param modules          modules to be updated. All have to have a corresponding facet and android project.
   * @param testArtifactName new test artifact name.
   * @return {@code true} if the module update was successful, {@code false} otherwise.
   */
  boolean updateTestArtifactsNames(@NotNull Project project,
                                   @NotNull final Iterable<Module> modules,
                                   @NotNull final String testArtifactName) {
    final List<AndroidFacet> affectedFacets = Lists.newArrayList();
    executeProjectChanges(project, new Runnable() {
      @Override
      public void run() {
        for (Module module : modules) {
          AndroidFacet androidFacet = AndroidFacet.getInstance(module);
          if (androidFacet == null) {
            continue;
          }

          AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
          assert androidModel != null;

          if (!androidModel.getSelectedTestArtifactName().equals(testArtifactName)) {
            androidModel.setSelectedTestArtifactName(testArtifactName);
            androidModel.syncSelectedVariantAndTestArtifact(androidFacet);
            invokeCustomizers(androidFacet.getModule(), androidModel);
            affectedFacets.add(androidFacet);
          }
        }

        generateSourcesIfNeeded(affectedFacets);
      }
    });
    return !affectedFacets.isEmpty();
  }

  @Nullable
  private Module doUpdate(@NotNull Project project,
                          @NotNull String moduleName,
                          @NotNull String variant,
                          @NotNull List<AndroidFacet> affectedAndroidFacets,
                          @NotNull List<NativeAndroidGradleFacet> affectedNativeAndroidFacets) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      logAndShowUpdateFailure(variant, String.format("Cannot find module '%1$s'.", moduleName));
      return null;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(moduleToUpdate);
    NativeAndroidGradleFacet nativeAndroidFacet = NativeAndroidGradleFacet.getInstance(moduleToUpdate);

    if (androidFacet == null && nativeAndroidFacet == null) {
      logAndShowUpdateFailure(variant,
                              String.format("Cannot find 'Android' or 'Native-Android-Gradle' facets in module '%1$s'.",
                                            moduleToUpdate.getName()));
    }
    if (nativeAndroidFacet != null) {
      NativeAndroidGradleModel nativeAndroidModel = getNativeAndroidModel(nativeAndroidFacet, variant);
      if (nativeAndroidModel == null) {
        return null;
      }
      if (!updateSelectedVariant(nativeAndroidFacet, nativeAndroidModel, variant)) {
        return null;
      }
      affectedNativeAndroidFacets.add(nativeAndroidFacet);
    }
    if (androidFacet != null) {
      AndroidGradleModel androidModel = getAndroidModel(androidFacet, variant);
      if (androidModel == null) {
        return null;
      }

      if (!updateSelectedVariant(androidFacet, androidModel, variant, affectedAndroidFacets)) {
        return null;
      }
      affectedAndroidFacets.add(androidFacet);
    }
    return moduleToUpdate;
  }

  @Nullable
  private static Module findModule(@NotNull Project project, @NotNull String moduleName) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    return moduleManager.findModuleByName(moduleName);
  }

  private boolean updateSelectedVariant(@NotNull AndroidFacet androidFacet,
                                        @NotNull AndroidGradleModel androidModel,
                                        @NotNull String variantToSelect,
                                        @NotNull List<AndroidFacet> affectedFacets) {
    Variant selectedVariant = androidModel.getSelectedVariant();
    if (variantToSelect.equals(selectedVariant.getName())) {
      return false;
    }
    androidModel.setSelectedVariantName(variantToSelect);
    androidModel.syncSelectedVariantAndTestArtifact(androidFacet);
    Module module = invokeCustomizers(androidFacet.getModule(), androidModel);

    for (AndroidLibrary library : androidModel.getSelectedMainCompileDependencies().getLibraries()) {
      String gradlePath = library.getProject();
      if (StringUtil.isEmpty(gradlePath)) {
        continue;
      }
      String projectVariant = library.getProjectVariant();
      if (StringUtil.isNotEmpty(projectVariant)) {
        ensureVariantIsSelected(module.getProject(), gradlePath, projectVariant, affectedFacets);
      }
    }
    return true;
  }

  private static boolean updateSelectedVariant(@NotNull NativeAndroidGradleFacet nativeAndroidFacet,
                                               @NotNull NativeAndroidGradleModel nativeAndroidModel,
                                               @NotNull String variantToSelect) {
    NativeVariant selectedVariant = nativeAndroidModel.getSelectedVariant();
    if (variantToSelect.equals(selectedVariant.getName())) {
      return false;
    }
    nativeAndroidModel.setSelectedVariantName(variantToSelect);
    invokeCustomizers(nativeAndroidFacet.getModule(), nativeAndroidModel);

    // TODO: Also update the dependent modules variants.
    return true;
  }

  private static void generateSourcesIfNeeded(@NotNull List<AndroidFacet> affectedFacets) {
    if (!affectedFacets.isEmpty()) {
      // We build only the selected variant. If user changes variant, we need to re-generate sources since the generated sources may not
      // be there.
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        Project project = affectedFacets.get(0).getModule().getProject();
        GradleProjectBuilder.getInstance(project).generateSourcesOnly(false);
      }
    }
  }

  @NotNull
  private static Module invokeCustomizers(@NotNull Module module, @NotNull AndroidGradleModel androidModel) {
    final IdeModifiableModelsProviderImpl modelsProvider = new IdeModifiableModelsProviderImpl(module.getProject());
    try {
      for (ModuleCustomizer<AndroidGradleModel> customizer : getCustomizers(androidModel.getProjectSystemId())) {
        customizer.customizeModule(module.getProject(), module, modelsProvider, androidModel);
      }
      modelsProvider.commit();
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
    return module;
  }

  @NotNull
  private static List<BuildVariantModuleCustomizer<AndroidGradleModel>> getCustomizers(@NotNull ProjectSystemId targetProjectSystemId) {
    return getCustomizers(targetProjectSystemId, BuildVariantModuleCustomizer.EP_NAME.getExtensions());
  }

  @VisibleForTesting
  @NotNull
  static List<BuildVariantModuleCustomizer<AndroidGradleModel>> getCustomizers(@NotNull ProjectSystemId targetProjectSystemId,
                                                                               @NotNull BuildVariantModuleCustomizer... allCustomizers) {
    List<BuildVariantModuleCustomizer<AndroidGradleModel>> customizers = Lists.newArrayList();
    for (BuildVariantModuleCustomizer customizer : allCustomizers) {
      // Supported model type must be AndroidGradleModel or subclass.
      if (AndroidGradleModel.class.isAssignableFrom(customizer.getSupportedModelType())) {
        // Build system should be ProjectSystemId.IDE or match the build system sent as parameter.
        ProjectSystemId projectSystemId = customizer.getProjectSystemId();
        if (Objects.equal(projectSystemId, targetProjectSystemId) || Objects.equal(projectSystemId, ProjectSystemId.IDE)) {
          //noinspection unchecked
          customizers.add(customizer);
        }
      }
    }
    return customizers;
  }

  @NotNull
  private static Module invokeCustomizers(@NotNull Module module, @NotNull NativeAndroidGradleModel nativeAndroidModel) {
    final IdeModifiableModelsProviderImpl modelsProvider = new IdeModifiableModelsProviderImpl(module.getProject());
    try {
      for (ModuleCustomizer<NativeAndroidGradleModel> customizer : getNativeAndroidCustomizers(nativeAndroidModel.getProjectSystemId())) {
        customizer.customizeModule(module.getProject(), module, modelsProvider, nativeAndroidModel);
      }
      modelsProvider.commit();
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
    return module;
  }

  @NotNull
  private static List<BuildVariantModuleCustomizer<NativeAndroidGradleModel>> getNativeAndroidCustomizers(
    @NotNull ProjectSystemId targetProjectSystemId) {
    return getNativeAndroidCustomizers(targetProjectSystemId, BuildVariantModuleCustomizer.EP_NAME.getExtensions());
  }

  @VisibleForTesting
  @NotNull
  static List<BuildVariantModuleCustomizer<NativeAndroidGradleModel>> getNativeAndroidCustomizers(
    @NotNull ProjectSystemId targetProjectSystemId,
    @NotNull BuildVariantModuleCustomizer... allCustomizers) {
    List<BuildVariantModuleCustomizer<NativeAndroidGradleModel>> customizers = Lists.newArrayList();
    for (BuildVariantModuleCustomizer customizer : allCustomizers) {
      // Supported model type must be NativeAndroidGradleModel or subclass.
      if (NativeAndroidGradleModel.class.isAssignableFrom(customizer.getSupportedModelType())) {
        // Build system should be ProjectSystemId.IDE or match the build system sent as parameter.
        ProjectSystemId projectSystemId = customizer.getProjectSystemId();
        if (Objects.equal(projectSystemId, targetProjectSystemId) || Objects.equal(projectSystemId, ProjectSystemId.IDE)) {
          //noinspection unchecked
          customizers.add(customizer);
        }
      }
    }
    return customizers;
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

    AndroidGradleModel androidModel = getAndroidModel(facet, variant);
    if (androidModel == null) {
      return;
    }

    if (!updateSelectedVariant(facet, androidModel, variant, affectedFacets)) {
      return;
    }
    affectedFacets.add(facet);
  }


  @Nullable
  private static AndroidGradleModel getAndroidModel(@NotNull AndroidFacet facet, @NotNull String variantToSelect) {
    AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
    if (androidModel == null) {
      logAndShowUpdateFailure(variantToSelect, String.format("Cannot find AndroidProject for module '%1$s'.", facet.getModule().getName()));
    }
    return androidModel;
  }

  @Nullable
  private static NativeAndroidGradleModel getNativeAndroidModel(@NotNull NativeAndroidGradleFacet facet, @NotNull String variantToSelect) {
    NativeAndroidGradleModel nativeAndroidModel = NativeAndroidGradleModel.get(facet);
    if (nativeAndroidModel == null) {
      logAndShowUpdateFailure(variantToSelect,
                              String.format("Cannot find NativeAndroidProject for module '%1$s'.", facet.getModule().getName()));
    }
    return nativeAndroidModel;
  }

  private static void logAndShowUpdateFailure(@NotNull String buildVariantName, @NotNull String reason) {
    String prefix = String.format("Unable to select build variant '%1$s':\n", buildVariantName);
    String msg = prefix + reason;
    LOG.error(msg);
    msg += ".\n\nConsult IDE log for more details (Help | Show Log)";
    Messages.showErrorDialog(msg, "Error");
  }
}
