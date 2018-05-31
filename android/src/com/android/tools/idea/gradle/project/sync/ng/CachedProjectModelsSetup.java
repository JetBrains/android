/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.ModelNotFoundInCacheException;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

class CachedProjectModelsSetup extends ModuleSetup<CachedProjectModels> {
  @NotNull private final GradleModuleSetup myGradleModuleSetup;
  @NotNull private final AndroidModuleSetup myAndroidModuleSetup;
  @NotNull private final NdkModuleSetup myNdkModuleSetup;
  @NotNull private final JavaModuleSetup myJavaModuleSetup;
  @NotNull private final ModuleFinder.Factory myModuleFinderFactory;
  @NotNull private final CompositeBuildDataSetup myCompositeBuildDataSetup;

  CachedProjectModelsSetup(@NotNull Project project,
                           @NotNull IdeModifiableModelsProvider modelsProvider,
                           @NotNull GradleModuleSetup gradleModuleSetup,
                           @NotNull AndroidModuleSetup androidModuleSetup,
                           @NotNull NdkModuleSetup ndkModuleSetup,
                           @NotNull JavaModuleSetup javaModuleSetup,
                           @NotNull ModuleSetupContext.Factory moduleSetupFactory,
                           @NotNull ModuleFinder.Factory moduleFinderFactory,
                           @NotNull CompositeBuildDataSetup compositeBuildDataSetup) {
    super(project, modelsProvider, moduleSetupFactory);
    myGradleModuleSetup = gradleModuleSetup;
    myAndroidModuleSetup = androidModuleSetup;
    myNdkModuleSetup = ndkModuleSetup;
    myJavaModuleSetup = javaModuleSetup;
    myModuleFinderFactory = moduleFinderFactory;
    myCompositeBuildDataSetup = compositeBuildDataSetup;
  }

  @Override
  public void setUpModules(@NotNull CachedProjectModels projectModels, @NotNull ProgressIndicator indicator)
    throws ModelNotFoundInCacheException {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      // Tests always run in EDT
      assert !application.isDispatchThread();
    }

    notifyModuleConfigurationStarted(indicator);

    myCompositeBuildDataSetup.setupCompositeBuildData(projectModels, myProject);
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
      String moduleName = gradleFacet.getModule().getName();
      CachedModuleModels moduleModelsCache = projectModels.findCacheForModule(moduleName);
      if (moduleModelsCache != null) {
        setUpModule(gradleFacet, moduleModelsCache, moduleFinder);
      }
    }
  }

  private void setUpModule(@NotNull GradleFacet gradleFacet,
                           @NotNull CachedModuleModels cache,
                           @NotNull ModuleFinder moduleFinder) throws ModelNotFoundInCacheException {
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
}
