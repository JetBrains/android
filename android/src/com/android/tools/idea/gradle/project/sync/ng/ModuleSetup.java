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

import com.android.ide.common.gradle.model.IdeNativeAndroidProjectImpl;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.project.model.JavaModuleModelFactory;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.ModelNotFoundInCacheException;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanup;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

abstract class ModuleSetup {
  abstract void setUpModules(@NotNull SyncProjectModels projectModels, @NotNull ProgressIndicator indicator);

  abstract void setUpModules(@NotNull CachedProjectModels projectModels, @NotNull ProgressIndicator indicator)
    throws ModelNotFoundInCacheException;

  static class Factory {
    @NotNull
    ModuleSetup create(@NotNull Project project,
                       @NotNull IdeModifiableModelsProvider modelsProvider) {
      IdeDependenciesFactory dependenciesFactory = new IdeDependenciesFactory();
      return new ModuleSetupImpl(project,
                                 modelsProvider,
                                 dependenciesFactory,
                                 ExtraGradleSyncModelsManager.getInstance(),
                                 new ModuleFactory(project, modelsProvider),
                                 new GradleModuleSetup(),
                                 new AndroidModuleSetup(),
                                 new NdkModuleSetup(),
                                 new JavaModuleSetup(),
                                 new AndroidModuleProcessor(project, modelsProvider),
                                 new AndroidModelFactory(new VariantSelector(), dependenciesFactory),
                                 new ProjectCleanup(),
                                 new ObsoleteModuleDisposer(project, modelsProvider),
                                 new CachedProjectModels.Factory(),
                                 new IdeNativeAndroidProjectImpl.FactoryImpl(),
                                 new JavaModuleModelFactory(),
                                 new ProjectDataNodeSetup(),
                                 new ModuleSetupContext.Factory(),
                                 new ModuleFinder.Factory(),
                                 new CompositeBuildDataSetup());
    }
  }
}
