/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.ContentRootModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkFacetModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.project.sync.ng.AndroidModuleProcessor.MODULE_GRADLE_MODELS_KEY;
import static com.android.tools.idea.gradle.project.sync.ng.GradleSyncProgress.notifyProgress;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;
import static com.android.tools.idea.gradle.util.Projects.findModuleRootFolderPath;

abstract class ModuleSetup {
  abstract void setUpModules(@NotNull SyncAction.ProjectModels projectModels, @NotNull ProgressIndicator indicator);

  static class Factory {
    @NotNull
    ModuleSetup create(@NotNull Project project, @NotNull IdeModifiableModelsProvider modelsProvider) {
      return new ModuleSetupImpl(project, modelsProvider, GradleSyncState.getInstance(project), new ModuleFactory(project, modelsProvider),
                                 new GradleModuleSetup(), new NewAndroidModuleSetup(), new AndroidModuleProcessor(project, modelsProvider),
                                 new NdkModuleSetup(new NdkFacetModuleSetupStep(), new ContentRootModuleSetupStep()),
                                 new VariantSelector(), new ProjectCleanup(), new ObsoleteModuleDisposer(project, modelsProvider));
    }
  }

  @VisibleForTesting
  static class ModuleSetupImpl extends ModuleSetup {
    @NotNull private final Project myProject;
    @NotNull private final IdeModifiableModelsProvider myModelsProvider;
    @NotNull private final GradleSyncState mySyncState;
    @NotNull private final ModuleFactory myModuleFactory;
    @NotNull private final GradleModuleSetup myGradleModuleSetup;
    @NotNull private final AndroidModuleSetup myNewAndroidModuleSetup;
    @NotNull private final AndroidModuleProcessor myAndroidModuleProcessor;
    @NotNull private final NdkModuleSetup myNdkModuleSetup;
    @NotNull private final VariantSelector myVariantSelector;
    @NotNull private final ProjectCleanup myProjectCleanup;
    @NotNull private final ObsoleteModuleDisposer myModuleDisposer;

    @NotNull private final List<Module> myAndroidModules = new ArrayList<>();

    ModuleSetupImpl(@NotNull Project project,
                    @NotNull IdeModifiableModelsProvider modelsProvider,
                    @NotNull GradleSyncState syncState,
                    @NotNull ModuleFactory moduleFactory,
                    @NotNull GradleModuleSetup gradleModuleSetup,
                    @NotNull AndroidModuleSetup newAndroidModuleSetup,
                    @NotNull AndroidModuleProcessor androidModuleProcessor,
                    @NotNull NdkModuleSetup ndkModuleSetup,
                    @NotNull VariantSelector variantSelector,
                    @NotNull ProjectCleanup projectCleanup,
                    @NotNull ObsoleteModuleDisposer moduleDisposer) {
      myProject = project;
      myModelsProvider = modelsProvider;
      mySyncState = syncState;
      myModuleFactory = moduleFactory;
      myGradleModuleSetup = gradleModuleSetup;
      myNewAndroidModuleSetup = newAndroidModuleSetup;
      myAndroidModuleProcessor = androidModuleProcessor;
      myNdkModuleSetup = ndkModuleSetup;
      myVariantSelector = variantSelector;
      myProjectCleanup = projectCleanup;
      myModuleDisposer = moduleDisposer;
    }

    @Override
    void setUpModules(@NotNull SyncAction.ProjectModels projectModels, @NotNull ProgressIndicator indicator) {
      notifyProgress(indicator, "Configuring modules");

      createAndSetUpModules(projectModels, indicator);
      myAndroidModuleProcessor.processAndroidModels(myAndroidModules, indicator);
      myProjectCleanup.cleanUpProject(myProject, myModelsProvider, indicator);
      myModuleDisposer.disposeObsoleteModules(indicator);
    }

    private void createAndSetUpModules(@NotNull SyncAction.ProjectModels projectModels, @NotNull ProgressIndicator indicator) {
      boolean syncSkipped = mySyncState.isSyncSkipped();
      for (String gradlePath : projectModels.getProjectPaths()) {
        createAndSetupModule(gradlePath, projectModels, indicator, syncSkipped);
      }
    }

    private void createAndSetupModule(@NotNull String gradlePath,
                                      @NotNull SyncAction.ProjectModels projectModels,
                                      @NotNull ProgressIndicator indicator,
                                      boolean syncSkipped) {
      SyncAction.ModuleModels moduleModels = projectModels.getModels(gradlePath);
      if (moduleModels == null) {
        return;
      }
      Module module = myModuleFactory.createModule(moduleModels);
      module.putUserData(MODULE_GRADLE_MODELS_KEY, moduleModels);

      boolean isProjectRootFolder = false;

      File moduleRootFolderPath = findModuleRootFolderPath(module);
      assert moduleRootFolderPath != null;

      File gradleSettingsFile = new File(moduleRootFolderPath, FN_SETTINGS_GRADLE);
      if (gradleSettingsFile.isFile() &&
          !moduleModels.hasModel(AndroidProject.class) &&
          !moduleModels.hasModel(NativeAndroidProject.class)) {
        // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
        // compile it using Gradle. We still need to create the module to display files inside it.
        isProjectRootFolder = true;
      }

      myGradleModuleSetup.setUpModule(module, myModelsProvider, moduleModels);

      AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
      if (androidProject != null) {
        AndroidModuleModel androidModel = createAndroidModel(module, androidProject);
        if (androidModel != null) {
          myNewAndroidModuleSetup.setUpModule(module, myModelsProvider, androidModel, moduleModels, indicator, syncSkipped);
          myAndroidModules.add(module);
        }
        else {
          // This is an Android module without variants. Treat as a non-buildable Java module.
          removeAndroidFacetFrom(module);
          //setUpJavaModule(module, models, indicator, true /* Android project without variants */, syncSkipped);
        }
        return;
      }
      // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
      removeAndroidFacetFrom(module);

      NativeAndroidProject nativeAndroidProject = moduleModels.findModel(NativeAndroidProject.class);
      if (nativeAndroidProject != null) {
        NdkModuleModel ndkModuleModel = new NdkModuleModel(module.getName(), moduleRootFolderPath, nativeAndroidProject);
        myNdkModuleSetup.setUpModule(module, myModelsProvider, ndkModuleModel, moduleModels, indicator, syncSkipped);
        return;
      }
      // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
      removeAllFacets(myModelsProvider.getModifiableFacetModel(module), NdkFacet.getFacetTypeId());

      if (!isProjectRootFolder) {
        // TODO: enable when new "Java library" model is implemented
        //setUpJavaModule(module, models, indicator, false /* Regular Java module */, syncSkipped);
      }
    }

    @Nullable
    private AndroidModuleModel createAndroidModel(@NotNull Module module, @NotNull AndroidProject androidProject) {
      Variant variantToSelect = myVariantSelector.findVariantToSelect(androidProject);
      if (variantToSelect != null) {
        File moduleRootFolderPath = findModuleRootFolderPath(module);
        if (moduleRootFolderPath != null) {
          return new AndroidModuleModel(module.getName(), moduleRootFolderPath, androidProject, variantToSelect.getName());
        }
      }
      // If an Android project does not have variants, it would be impossible to build. This is a possible but invalid use case.
      // For now we are going to treat this case as a Java library module, because everywhere in the IDE (e.g. run configurations,
      // editors, test support, variants tool window, project building, etc.) we have the assumption that there is at least one variant
      // per Android project, and changing that in the code base is too risky, for very little benefit.
      // See https://code.google.com/p/android/issues/detail?id=170722
      return null;
    }

    private void removeAndroidFacetFrom(@NotNull Module module) {
      removeAllFacets(myModelsProvider.getModifiableFacetModel(module), AndroidFacet.ID);
    }
  }
}
