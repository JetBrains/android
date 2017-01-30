/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleDisposer;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.android.*;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.ContentRootModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkFacetModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.project.sync.GradleSyncProgress.notifyProgress;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;
import static com.android.tools.idea.gradle.util.Projects.findModuleRootFolderPath;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeProjectChangeAction;

abstract class ProjectSetup {
  abstract void setUpProject(@NotNull SyncAction.ProjectModels models, @NotNull ProgressIndicator indicator);

  abstract void commit(boolean synchronous);

  static class Factory {
    @NotNull
    ProjectSetup create(@NotNull Project project) {
      return new ProjectSetupImpl(project, new IdeModifiableModelsProviderImpl(project));
    }
  }

  @VisibleForTesting
  static class ProjectSetupImpl extends ProjectSetup {
    private static Key<SyncAction.ModuleModels> MODULE_GRADLE_MODELS_KEY = Key.create("module.gradle.models");

    @NotNull private final Project myProject;
    @NotNull private final IdeModifiableModelsProvider myModelsProvider;
    @NotNull private final IdeInfo myIdeInfo;
    @NotNull private final GradleSyncState mySyncState;
    @NotNull private final ModuleFactory myModuleFactory;
    @NotNull private final GradleModuleSetup myGradleModuleSetup;
    @NotNull private final AndroidModuleSetup myNewAndroidModuleSetup;
    @NotNull private final AndroidModuleSetup myDependenciesAndroidModuleSetup;
    @NotNull private final NdkModuleSetup myNdkModuleSetup;
    @NotNull private final VariantSelector myVariantSelector;
    @NotNull private final AndroidModuleValidator.Factory myModuleValidatorFactory;
    @NotNull private final ModuleDisposer myModuleDisposer;

    ProjectSetupImpl(@NotNull Project project, @NotNull IdeModifiableModelsProvider modelsProvider) {
      this(project, modelsProvider, IdeInfo.getInstance(), GradleSyncState.getInstance(project), new ModuleFactory(project, modelsProvider),
           new GradleModuleSetup(), createNewAndroidModuleSetup(), new AndroidModuleSetup(new DependenciesAndroidModuleSetupStep()),
           new NdkModuleSetup(new NdkFacetModuleSetupStep(), new ContentRootModuleSetupStep()),
           new VariantSelector(), new AndroidModuleValidator.Factory(), new ModuleDisposer());
    }

    @NotNull
    private static AndroidModuleSetup createNewAndroidModuleSetup() {
      // These are the steps to setup Android modules when they are first created.
      // These steps can be executed on module creation because, unlike dependency setup, they don't depend on the rest of the project's
      // modules.
      return new AndroidModuleSetup(new AndroidFacetModuleSetupStep(), new SdkModuleSetupStep(), new JdkModuleSetupStep(),
                                    new ContentRootsModuleSetupStep(), new CompilerOutputModuleSetupStep());
    }

    @VisibleForTesting
    ProjectSetupImpl(@NotNull Project project,
                     @NotNull IdeModifiableModelsProvider modelsProvider,
                     @NotNull IdeInfo ideInfo,
                     @NotNull GradleSyncState syncState,
                     @NotNull ModuleFactory moduleFactory,
                     @NotNull GradleModuleSetup gradleModuleSetup,
                     @NotNull AndroidModuleSetup newAndroidModuleSetup,
                     @NotNull AndroidModuleSetup dependenciesAndroidModuleSetup,
                     @NotNull NdkModuleSetup ndkModuleSetup,
                     @NotNull VariantSelector variantSelector,
                     @NotNull AndroidModuleValidator.Factory moduleValidatorFactory,
                     @NotNull ModuleDisposer moduleDisposer) {
      myProject = project;
      myModelsProvider = modelsProvider;
      myIdeInfo = ideInfo;
      mySyncState = syncState;
      myModuleFactory = moduleFactory;
      myGradleModuleSetup = gradleModuleSetup;
      myNewAndroidModuleSetup = newAndroidModuleSetup;
      myDependenciesAndroidModuleSetup = dependenciesAndroidModuleSetup;
      myNdkModuleSetup = ndkModuleSetup;
      myVariantSelector = variantSelector;
      myModuleValidatorFactory = moduleValidatorFactory;
      myModuleDisposer = moduleDisposer;
    }

    @Override
    void setUpProject(@NotNull SyncAction.ProjectModels models, @NotNull ProgressIndicator indicator) {
      createModules(models, indicator);
    }

    private void createModules(@NotNull SyncAction.ProjectModels projectModels, @NotNull ProgressIndicator indicator) {
      notifyProgress(indicator, "Configuring modules");

      List<Module> androidModules = new ArrayList<>();
      boolean syncSkipped = GradleSyncState.getInstance(myProject).isSyncSkipped();

      for (String gradlePath : projectModels.getProjectPaths()) {
        SyncAction.ModuleModels moduleModels = projectModels.getModels(gradlePath);

        if (moduleModels != null) {
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
              androidModules.add(module);
            }
            else {
              // This is an Android module without variants. Treat as a non-buildable Java module.
              removeAndroidFacetFrom(module);
              //setUpJavaModule(module, models, indicator, true /* Android project without variants */, syncSkipped);
            }
            continue;
          }
          // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
          removeAndroidFacetFrom(module);

          NativeAndroidProject nativeAndroidProject = moduleModels.findModel(NativeAndroidProject.class);
          if (nativeAndroidProject != null) {
            NdkModuleModel ndkModuleModel = new NdkModuleModel(module.getName(), moduleRootFolderPath, nativeAndroidProject);
            myNdkModuleSetup.setUpModule(module, myModelsProvider, ndkModuleModel, moduleModels, indicator, syncSkipped);
            continue;
          }
          // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
          removeAllFacets(myModelsProvider.getModifiableFacetModel(module), NdkFacet.getFacetTypeId());

          if (!isProjectRootFolder) {
            // TODO: enable when new "Java library" model is implemented
            //setUpJavaModule(module, models, indicator, false /* Regular Java module */, syncSkipped);
          }
        }
      }

      AndroidModuleValidator moduleValidator = myModuleValidatorFactory.create(myProject);
      for (Module module : androidModules) {
        AndroidModuleModel androidModel = findAndroidModel(module);
        if (androidModel != null) {
          SyncAction.ModuleModels moduleModels = module.getUserData(MODULE_GRADLE_MODELS_KEY);
          assert moduleModels != null;
          // We need to set up dependencies once all modules are created.
          myDependenciesAndroidModuleSetup.setUpModule(module, myModelsProvider, androidModel, moduleModels, indicator, syncSkipped);
          moduleValidator.validate(module, androidModel);
        }
      }
      moduleValidator.fixAndReportFoundIssues();

      if (myIdeInfo.isAndroidStudio() || mySyncState.lastSyncFailedOrHasIssues()) {
        // Dispose modules that do not have models.
        List<Module> modulesToDispose = new CopyOnWriteArrayList<>();
        List<Module> modules = Arrays.asList(myModelsProvider.getModules());
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(modules, indicator, true, module -> {
          SyncAction.ModuleModels moduleModels = module.getUserData(MODULE_GRADLE_MODELS_KEY);
          if (moduleModels == null) {
            modulesToDispose.add(module);
          }
          else {
            module.putUserData(MODULE_GRADLE_MODELS_KEY, null);
          }
          return true;
        });
        myModuleDisposer.disposeModulesAndMarkImlFilesForDeletion(modulesToDispose, myProject, myModelsProvider);
      }
    }

    private void removeAndroidFacetFrom(@NotNull Module module) {
      removeAllFacets(myModelsProvider.getModifiableFacetModel(module), AndroidFacet.ID);
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

    @Nullable
    private AndroidModuleModel findAndroidModel(@NotNull Module module) {
      AndroidFacet facet = findFacet(module, myModelsProvider, AndroidFacet.ID);
      return facet != null ? AndroidModuleModel.get(facet) : null;
    }

    @Override
    public void commit(boolean synchronous) {
      try {
        executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(myProject) {
          @Override
          public void execute() {
            myModelsProvider.commit();
          }
        });
      }
      catch (Throwable e) {
        executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(myProject) {
          @Override
          public void execute() {
            myModelsProvider.dispose();
          }
        });
      }
    }
  }
}
