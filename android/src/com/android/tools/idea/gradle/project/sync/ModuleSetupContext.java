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
package com.android.tools.idea.gradle.project.sync;

import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.sync.ng.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.setup.Facets;
import com.android.tools.idea.gradle.project.sync.setup.module.ModulesByGradlePath;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class ModuleSetupContext {
  @VisibleForTesting
  static final Key<ModulesByGradlePath> MODULES_BY_GRADLE_PATH_KEY = Key.create("gradle.sync.modules.by.gradle.path");

  @NotNull private final Module myModule;
  @NotNull private final IdeModifiableModelsProvider myIdeModelsProvider;

  @Nullable private final GradleModuleModels myGradleModels;

  @VisibleForTesting
  ModuleSetupContext(@NotNull Module module,
                     @NotNull IdeModifiableModelsProvider ideModelsProvider,
                     @Nullable GradleModuleModels gradleModels) {
    myModule = module;
    myIdeModelsProvider = ideModelsProvider;
    myGradleModels = gradleModels;
  }

  @Nullable
  public ModulesByGradlePath getModulesByGradlePath() {
    ModulesByGradlePath modulesByGradlePath = myModule.getProject().getUserData(MODULES_BY_GRADLE_PATH_KEY);

    if (modulesByGradlePath == null) {
      ModulesByGradlePath temp = new ModulesByGradlePath();
      List<Module> modules = Arrays.asList(myIdeModelsProvider.getModules());
      JobLauncher.getInstance().invokeConcurrentlyUnderProgress(modules, null, true /* fail fast */, module -> {
        GradleFacet gradleFacet = GradleFacet.getInstance(module, myIdeModelsProvider);
        if (gradleFacet != null) {
          temp.addModule(module, gradleFacet.getConfiguration().GRADLE_PROJECT_PATH);
        }
        return true;
      });
      modulesByGradlePath = temp;
      store(modulesByGradlePath);
    }

    return modulesByGradlePath;
  }

  private void store(ModulesByGradlePath modulesByGradlePath) {
    myModule.getProject().putUserData(MODULES_BY_GRADLE_PATH_KEY, modulesByGradlePath);
  }

  public boolean hasNativeModel() {
    if (myGradleModels != null) {
      return myGradleModels.findModel(NativeAndroidProject.class) != null;
    }
    NdkFacet facet = Facets.findFacet(myModule, myIdeModelsProvider, NdkFacet.getFacetType().getId());
    return facet != null && facet.getNdkModuleModel() != null;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public IdeModifiableModelsProvider getIdeModelsProvider() {
    return myIdeModelsProvider;
  }

  @NotNull
  public ModifiableRootModel getModifiableRootModel() {
    return myIdeModelsProvider.getModifiableRootModel(myModule);
  }

  public static class Factory {
    @NotNull
    public ModuleSetupContext create(@NotNull Module module, @NotNull IdeModifiableModelsProvider ideModelsProvider) {
      return new ModuleSetupContext(module, ideModelsProvider, null);
    }

    @NotNull
    public ModuleSetupContext create(@NotNull Module module,
                                     @NotNull IdeModifiableModelsProvider ideModelsProvider,
                                     @NotNull ModulesByGradlePath modulesByGradlePath,
                                     @NotNull GradleModuleModels gradleModels) {
      ModuleSetupContext context = new ModuleSetupContext(module, ideModelsProvider, gradleModels);
      context.store(modulesByGradlePath);
      return context;
    }
  }

  public static void removeSyncContextDataFrom(@NotNull Project project) {
    project.putUserData(MODULES_BY_GRADLE_PATH_KEY, null);
  }
}
