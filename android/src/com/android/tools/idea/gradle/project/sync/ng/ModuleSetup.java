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

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.java.model.ArtifactModel;
import com.android.java.model.JavaProject;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.*;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.project.model.ide.android.IdeNativeAndroidProject;
import com.android.tools.idea.gradle.project.model.ide.android.IdeNativeAndroidProjectImpl;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.ContentRootModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkFacetModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
                                 new VariantSelector(), new ProjectCleanup(), new ObsoleteModuleDisposer(project, modelsProvider),
                                 new NewJavaModuleSetup(), new IdeNativeAndroidProjectImpl.FactoryImpl(), new NewJavaModuleModelFactory(),
                                 new ArtifactModuleModelFactory(), new ExtraSyncModelExtensionManager(),
                                 new IdeDependenciesFactory());
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
    @NotNull private final JavaModuleSetup myJavaModuleSetup;
    @NotNull private final IdeNativeAndroidProject.Factory myNativeAndroidProjectFactory;
    @NotNull private final NewJavaModuleModelFactory myNewJavaModuleModelFactory;
    @NotNull private final ArtifactModuleModelFactory myArtifactModuleModelFactory;
    @NotNull private final ExtraSyncModelExtensionManager myExtraSyncModelExtensionManager;
    @NotNull private final IdeDependenciesFactory myDependenciesFactory;

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
                    @NotNull ObsoleteModuleDisposer moduleDisposer,
                    @NotNull JavaModuleSetup javaModuleSetup,
                    @NotNull IdeNativeAndroidProject.Factory nativeAndroidProjectFactory,
                    @NotNull NewJavaModuleModelFactory javaModuleModelFactory,
                    @NotNull ArtifactModuleModelFactory artifactModuleModelFactory,
                    @NotNull ExtraSyncModelExtensionManager extraSyncModelExtensionManager,
                    @NotNull IdeDependenciesFactory dependenciesFactory) {
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
      myJavaModuleSetup = javaModuleSetup;
      myNativeAndroidProjectFactory = nativeAndroidProjectFactory;
      myNewJavaModuleModelFactory = javaModuleModelFactory;
      myArtifactModuleModelFactory = artifactModuleModelFactory;
      myExtraSyncModelExtensionManager = extraSyncModelExtensionManager;
      myDependenciesFactory = dependenciesFactory;
    }

    @Override
    void setUpModules(@NotNull SyncAction.ProjectModels projectModels, @NotNull ProgressIndicator indicator) {
      notifyProgress(indicator, "Configuring modules");
      GlobalLibraryMap globalLibraryMap = projectModels.getGlobalLibraryMap();
      if (globalLibraryMap != null) {
        myDependenciesFactory.setupGlobalLibraryMap(globalLibraryMap);
      }
      createAndSetUpModules(projectModels, indicator);
      myAndroidModuleProcessor.processAndroidModels(myAndroidModules, indicator);
      myProjectCleanup.cleanUpProject(myProject, myModelsProvider, indicator);
      myModuleDisposer.disposeObsoleteModules(indicator);
    }

    private void createAndSetUpModules(@NotNull SyncAction.ProjectModels projectModels, @NotNull ProgressIndicator indicator) {
      boolean syncSkipped = mySyncState.isSyncSkipped();
      populateModuleBuildDirs(projectModels);
      for (String gradlePath : projectModels.getProjectPaths()) {
        createAndSetupModule(gradlePath, projectModels, indicator, syncSkipped);
      }
    }

    /**
     * Populate the map from project path to build directory for all modules.
     * It will be used to check if a {@link AndroidLibrary} is sub-module that wraps local aar.
     */
    private void populateModuleBuildDirs(@NotNull SyncAction.ProjectModels projectModels) {
      for (String projectPath : projectModels.getProjectPaths()) {
        SyncAction.ModuleModels moduleModels = projectModels.getModels(projectPath);
        if (moduleModels == null) {
          continue;
        }
        GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
        if (gradleProject != null) {
          myDependenciesFactory.findAndAddBuildFolderPath(gradleProject);
        }
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

      File moduleRootFolderPath = findModuleRootFolderPath(module);
      assert moduleRootFolderPath != null;

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
          // TODO: Figure out what to do with android modules without variants.
          // New Java library plugin relies on 'java' plugin, it does not return JavaProject models for android module.
          // setUpJavaModule(module, models, indicator, true /* Android project without variants */, syncSkipped);
        }
        return;
      }
      // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
      removeAndroidFacetFrom(module);

      NativeAndroidProject nativeAndroidProject = moduleModels.findModel(NativeAndroidProject.class);
      if (nativeAndroidProject != null) {
        IdeNativeAndroidProject copy = myNativeAndroidProjectFactory.create(nativeAndroidProject);
        NdkModuleModel ndkModuleModel = new NdkModuleModel(module.getName(), moduleRootFolderPath, copy);
        myNdkModuleSetup.setUpModule(module, myModelsProvider, ndkModuleModel, moduleModels, indicator, syncSkipped);
        return;
      }
      // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
      removeAllFacets(myModelsProvider.getModifiableFacetModel(module), NdkFacet.getFacetTypeId());

      // This is a Java module.
      JavaProject javaProject = moduleModels.findModel(JavaProject.class);
      GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
      if (gradleProject != null && javaProject != null) {
        JavaModuleModel javaModuleModel = myNewJavaModuleModelFactory.create(gradleProject, javaProject, false);
        myJavaModuleSetup.setUpModule(module, myModelsProvider, javaModuleModel, moduleModels, indicator, syncSkipped);
        myExtraSyncModelExtensionManager.setupExtraJavaModels(moduleModels, myProject, module, myModelsProvider);
        return;
      }

      // This is a Jar/Aar module or root module.
      ArtifactModel jarAarProject = moduleModels.findModel(ArtifactModel.class);
      if (gradleProject != null && jarAarProject != null) {
        JavaModuleModel javaModuleModel = myArtifactModuleModelFactory.create(gradleProject, jarAarProject);
        myJavaModuleSetup.setUpModule(module, myModelsProvider, javaModuleModel, moduleModels, indicator, syncSkipped);
      }
    }

    @Nullable
    private AndroidModuleModel createAndroidModel(@NotNull Module module, @NotNull AndroidProject androidProject) {
      Variant variantToSelect = myVariantSelector.findVariantToSelect(androidProject);
      if (variantToSelect != null) {
        File moduleRootFolderPath = findModuleRootFolderPath(module);
        if (moduleRootFolderPath != null) {
          return new AndroidModuleModel(module.getName(), moduleRootFolderPath, androidProject, variantToSelect.getName(),
                                        myDependenciesFactory);
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
