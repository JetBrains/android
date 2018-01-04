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
import com.android.ide.common.gradle.model.IdeNativeAndroidProject;
import com.android.ide.common.gradle.model.IdeNativeAndroidProjectImpl;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.java.model.ArtifactModel;
import com.android.java.model.JavaProject;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.*;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.ModelNotFoundInCacheException;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.ng.AndroidModuleProcessor.MODULE_GRADLE_MODELS_KEY;
import static com.android.tools.idea.gradle.project.sync.ng.GradleSyncProgress.notifyProgress;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;
import static com.android.tools.idea.gradle.util.GradleProjects.findModuleRootFolderPath;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

abstract class ModuleSetup {
  abstract void setUpModules(@NotNull SyncProjectModels projectModels, @NotNull ProgressIndicator indicator);

  abstract void setUpModules(@NotNull CachedProjectModels projectModels, @NotNull ProgressIndicator indicator)
    throws ModelNotFoundInCacheException;

  static class Factory {
    @NotNull
    ModuleSetup create(@NotNull Project project,
                       @NotNull IdeModifiableModelsProvider modelsProvider) {
      return new ModuleSetupImpl(project,
                                 modelsProvider,
                                 ExtraGradleSyncModelsManager.getInstance(),
                                 new ModuleFactory(project, modelsProvider),
                                 new GradleModuleSetup(),
                                 new AndroidModuleSetup(),
                                 new NdkModuleSetup(),
                                 new JavaModuleSetup(),
                                 new AndroidModuleProcessor(project, modelsProvider),
                                 new VariantSelector(),
                                 new ProjectCleanup(),
                                 new ObsoleteModuleDisposer(project, modelsProvider),
                                 new CachedProjectModels.Factory(),
                                 new IdeNativeAndroidProjectImpl.FactoryImpl(),
                                 new JavaModuleModelFactory(),
                                 new IdeDependenciesFactory(),
                                 new ProjectDataNodeSetup(),
                                 new ModuleSetupContext.Factory(),
                                 new ModuleFinder.Factory());
    }
  }

  @VisibleForTesting
  static class ModuleSetupImpl extends ModuleSetup {
    @NotNull private final Project myProject;
    @NotNull private final IdeModifiableModelsProvider myModelsProvider;
    @NotNull private final ModuleFactory myModuleFactory;
    @NotNull private final GradleModuleSetup myGradleModuleSetup;
    @NotNull private final AndroidModuleSetup myAndroidModuleSetup;
    @NotNull private final NdkModuleSetup myNdkModuleSetup;
    @NotNull private final JavaModuleSetup myJavaModuleSetup;
    @NotNull private final AndroidModuleProcessor myAndroidModuleProcessor;
    @NotNull private final VariantSelector myVariantSelector;
    @NotNull private final ProjectCleanup myProjectCleanup;
    @NotNull private final ObsoleteModuleDisposer myModuleDisposer;
    @NotNull private final CachedProjectModels.Factory myCachedProjectModelsFactory;
    @NotNull private final IdeNativeAndroidProject.Factory myNativeAndroidProjectFactory;
    @NotNull private final JavaModuleModelFactory myJavaModuleModelFactory;
    @NotNull private final ExtraGradleSyncModelsManager myExtraModelsManager;
    @NotNull private final IdeDependenciesFactory myDependenciesFactory;
    @NotNull private final ProjectDataNodeSetup myProjectDataNodeSetup;
    @NotNull private final ModuleSetupContext.Factory myModuleSetupFactory;
    @NotNull private final ModuleFinder.Factory myModuleFinderFactory;

    @NotNull private final List<Module> myAndroidModules = new ArrayList<>();

    ModuleSetupImpl(@NotNull Project project,
                    @NotNull IdeModifiableModelsProvider modelsProvider,
                    @NotNull ExtraGradleSyncModelsManager extraModelsManager,
                    @NotNull ModuleFactory moduleFactory,
                    @NotNull GradleModuleSetup gradleModuleSetup,
                    @NotNull AndroidModuleSetup androidModuleSetup,
                    @NotNull NdkModuleSetup ndkModuleSetup,
                    @NotNull JavaModuleSetup javaModuleSetup,
                    @NotNull AndroidModuleProcessor androidModuleProcessor,
                    @NotNull VariantSelector variantSelector,
                    @NotNull ProjectCleanup projectCleanup,
                    @NotNull ObsoleteModuleDisposer moduleDisposer,
                    @NotNull CachedProjectModels.Factory cachedProjectModelsFactory,
                    @NotNull IdeNativeAndroidProject.Factory nativeAndroidProjectFactory,
                    @NotNull JavaModuleModelFactory javaModuleModelFactory,
                    @NotNull IdeDependenciesFactory dependenciesFactory,
                    @NotNull ProjectDataNodeSetup projectDataNodeSetup,
                    @NotNull ModuleSetupContext.Factory moduleSetupFactory,
                    @NotNull ModuleFinder.Factory moduleFinderFactory) {
      myProject = project;
      myModelsProvider = modelsProvider;
      myModuleFactory = moduleFactory;
      myGradleModuleSetup = gradleModuleSetup;
      myAndroidModuleSetup = androidModuleSetup;
      myAndroidModuleProcessor = androidModuleProcessor;
      myNdkModuleSetup = ndkModuleSetup;
      myVariantSelector = variantSelector;
      myProjectCleanup = projectCleanup;
      myModuleDisposer = moduleDisposer;
      myJavaModuleSetup = javaModuleSetup;
      myCachedProjectModelsFactory = cachedProjectModelsFactory;
      myNativeAndroidProjectFactory = nativeAndroidProjectFactory;
      myJavaModuleModelFactory = javaModuleModelFactory;
      myExtraModelsManager = extraModelsManager;
      myDependenciesFactory = dependenciesFactory;
      myProjectDataNodeSetup = projectDataNodeSetup;
      myModuleSetupFactory = moduleSetupFactory;
      myModuleFinderFactory = moduleFinderFactory;
    }

    @Override
    void setUpModules(@NotNull CachedProjectModels projectModels, @NotNull ProgressIndicator indicator)
      throws ModelNotFoundInCacheException {
      Application application = ApplicationManager.getApplication();
      if (!application.isUnitTestMode()) {
        // Tests always run in EDT
        assert !application.isDispatchThread();
      }

      notifyModuleConfigurationStarted(indicator);

      List<Module> modules = Arrays.asList(ModuleManager.getInstance(myProject).getModules());
      List<GradleFacet> gradleFacets = new ArrayList<>();

      ModuleFinder moduleFinder = myModuleFinderFactory.create(myProject);

      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(modules, indicator, true /* fail fast */, module -> {
        GradleFacet gradleFacet = GradleFacet.getInstance(module);
        if (gradleFacet != null) {
          String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
          if (isNotEmpty(gradlePath)) {
            moduleFinder.addModule(module, gradlePath);
            gradleFacets.add(gradleFacet);
          }
        }
        return true;
      });

      for (GradleFacet gradleFacet : gradleFacets) {
        String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        CachedModuleModels moduleModelsCache = projectModels.findCacheForModule(gradlePath);
        if (moduleModelsCache != null) {
          setUpModule(gradleFacet, moduleModelsCache, moduleFinder);
        }
      }
    }

    private void setUpModule(@NotNull GradleFacet gradleFacet,
                             @NotNull CachedModuleModels cache,
                             @NotNull ModuleFinder moduleFinder)
      throws ModelNotFoundInCacheException {
      Application application = ApplicationManager.getApplication();
      if (!application.isUnitTestMode()) {
        // Tests always run in EDT
        assert !application.isDispatchThread();
      }

      Module module = gradleFacet.getModule();
      GradleModuleModel gradleModel = cache.findModel(GradleModuleModel.class);
      if (gradleModel == null) {
        throw new ModelNotFoundInCacheException(GradleModuleModel.class);
      }
      myGradleModuleSetup.setUpModule(module, myModelsProvider, gradleModel);

      ModuleSetupContext context = myModuleSetupFactory.create(module, myModelsProvider, moduleFinder, cache);

      AndroidModuleModel androidModel = cache.findModel(AndroidModuleModel.class);
      if (androidModel != null) {
        myAndroidModuleSetup.setUpModule(context, androidModel, true /* sync skipped */);
        myAndroidModules.add(module);
        return;
      }

      NdkModuleModel ndkModel = cache.findModel(NdkModuleModel.class);
      if (ndkModel != null) {
        myNdkModuleSetup.setUpModule(context, ndkModel, true /* sync skipped */);
        return;
      }

      JavaModuleModel javaModel = cache.findModel(JavaModuleModel.class);
      if (javaModel != null) {
        myJavaModuleSetup.setUpModule(context, javaModel, true /* sync skipped */);
      }
    }

    @Override
    void setUpModules(@NotNull SyncProjectModels projectModels, @NotNull ProgressIndicator indicator) {
      notifyModuleConfigurationStarted(indicator);
      CachedProjectModels cache = myCachedProjectModelsFactory.createNew();

      GlobalLibraryMap globalLibraryMap = projectModels.getGlobalLibraryMap();
      if (globalLibraryMap != null) {
        myDependenciesFactory.setUpGlobalLibraryMap(globalLibraryMap);
      }
      createAndSetUpModules(projectModels, cache);
      myProjectDataNodeSetup.setupProjectDataNode(projectModels, myProject);
      myAndroidModuleProcessor.processAndroidModels(myAndroidModules);
      myProjectCleanup.cleanUpProject(myProject, myModelsProvider, indicator);
      myModuleDisposer.disposeObsoleteModules(indicator);

      cache.saveToDisk(myProject);
    }

    private static void notifyModuleConfigurationStarted(@NotNull ProgressIndicator indicator) {
      notifyProgress(indicator, "Configuring modules");
    }

    // TODO(alruiz): reconcile with https://github.com/JetBrains/intellij-community/commit/6d425f7
    private static final String ROOT_PROJECT_PATH_KEY = "external.root.project.path";

    private void createAndSetUpModules(@NotNull SyncProjectModels projectModels, @NotNull CachedProjectModels cache) {
      populateModuleBuildFolders(projectModels);
      List<ModuleSetupInfo> moduleSetupInfos = new ArrayList<>();

      String projectRootFolderPath = nullToEmpty(myProject.getBasePath());

      ModuleFinder moduleFinder = myModuleFinderFactory.create(myProject);
      for (String gradlePath : projectModels.getProjectPaths()) {
        GradleModuleModels moduleModels = projectModels.getModels(gradlePath);
        if (moduleModels != null) {
          Module module = myModuleFactory.createModule(moduleModels);

          // This is needed by GradleOrderEnumeratorHandler#addCustomModuleRoots. Without this option, sync will fail.
          module.setOption(ROOT_PROJECT_PATH_KEY, projectRootFolderPath);

          // Set up GradleFacet right away. This is necessary to set up inter-module dependencies.
          GradleModuleModel gradleModel = myGradleModuleSetup.setUpModule(module, myModelsProvider, moduleModels);
          CachedModuleModels cachedModels = cache.addModule(module, gradlePath);
          cachedModels.addModel(gradleModel);

          moduleFinder.addModule(module, gradleModel.getGradlePath());
          moduleSetupInfos.add(new ModuleSetupInfo(module, moduleModels, cachedModels));
        }
      }

      for (ModuleSetupInfo moduleSetupInfo : moduleSetupInfos) {
        setUpModule(moduleSetupInfo, moduleFinder);
      }
    }

    /**
     * Populate the map from project path to build folder for all modules.
     * It will be used to check if a {@link AndroidLibrary} is sub-module that wraps local aar.
     */
    private void populateModuleBuildFolders(@NotNull SyncProjectModels projectModels) {
      for (String projectPath : projectModels.getProjectPaths()) {
        GradleModuleModels moduleModels = projectModels.getModels(projectPath);
        if (moduleModels == null) {
          continue;
        }
        GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
        if (gradleProject != null) {
          try {
            myDependenciesFactory.findAndAddBuildFolderPath(gradleProject.getPath(), gradleProject.getBuildDirectory());
          }
          catch (UnsupportedOperationException exception) {
            // getBuildDirectory is available for Gradle versions older than 2.0.
            // For older versions of gradle, there's no way to get build directory.
          }
        }
      }
    }

    private void setUpModule(@NotNull ModuleSetupInfo setupInfo, @NotNull ModuleFinder moduleFinder) {
      Module module = setupInfo.module;
      GradleModuleModels moduleModels = setupInfo.moduleModels;
      CachedModuleModels cachedModels = setupInfo.cachedModels;

      module.putUserData(MODULE_GRADLE_MODELS_KEY, moduleModels);

      File moduleRootFolderPath = findModuleRootFolderPath(module);
      assert moduleRootFolderPath != null;

      ModuleSetupContext context = myModuleSetupFactory.create(module, myModelsProvider, moduleFinder, moduleModels);

      AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
      if (androidProject != null) {
        AndroidModuleModel androidModel = createAndroidModel(module, androidProject);
        if (androidModel != null) {
          myAndroidModuleSetup.setUpModule(context, androidModel, false /* sync not skipped */);
          myAndroidModules.add(module);
          cachedModels.addModel(androidModel);

          // "Native" projects also both AndroidProject and AndroidNativeProject
          NativeAndroidProject nativeAndroidProject = moduleModels.findModel(NativeAndroidProject.class);
          if (nativeAndroidProject != null) {
            IdeNativeAndroidProject copy = myNativeAndroidProjectFactory.create(nativeAndroidProject);
            NdkModuleModel ndkModel = new NdkModuleModel(module.getName(), moduleRootFolderPath, copy);
            myNdkModuleSetup.setUpModule(context, ndkModel, false /* sync not skipped */);
            cachedModels.addModel(ndkModel);
          }
        }
        else {
          // This is an Android module without variants. Treat as a non-buildable Java module.
          removeAndroidFacetFrom(module);
          GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
          assert gradleProject != null;

          JavaModuleModel javaModel = myJavaModuleModelFactory.create(gradleProject, androidProject);
          myJavaModuleSetup.setUpModule(context, javaModel, false /* sync not skipped */);
          cachedModels.addModel(javaModel);
        }
        return;
      }
      // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
      removeAndroidFacetFrom(module);
      // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
      removeAllFacets(myModelsProvider.getModifiableFacetModel(module), NdkFacet.getFacetTypeId());

      // This is a Java module.
      JavaProject javaProject = moduleModels.findModel(JavaProject.class);
      GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
      if (gradleProject != null && javaProject != null) {
        JavaModuleModel javaModel = myJavaModuleModelFactory.create(moduleRootFolderPath, gradleProject,
                                                                    javaProject /* regular Java module */);
        myJavaModuleSetup.setUpModule(context, javaModel, false /* sync not skipped */);
        cachedModels.addModel(javaModel);

        myExtraModelsManager.applyModelsToModule(moduleModels, module, myModelsProvider);
        myExtraModelsManager.addJavaModelsToCache(module, cachedModels);
        return;
      }

      // This is a Jar/Aar module or root module.
      ArtifactModel jarAarProject = moduleModels.findModel(ArtifactModel.class);
      if (gradleProject != null && jarAarProject != null) {
        JavaModuleModel javaModel = myJavaModuleModelFactory.create(moduleRootFolderPath, gradleProject, jarAarProject);
        myJavaModuleSetup.setUpModule(context, javaModel, false /* sync not skipped */);
        cachedModels.addModel(javaModel);
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

  private static class ModuleSetupInfo {
    @NotNull final Module module;
    @NotNull final GradleModuleModels moduleModels;
    @NotNull final CachedModuleModels cachedModels;

    ModuleSetupInfo(@NotNull Module module, @NotNull GradleModuleModels moduleModels, @NotNull CachedModuleModels cachedModels) {
      this.module = module;
      this.moduleModels = moduleModels;
      this.cachedModels = cachedModels;
    }
  }
}
