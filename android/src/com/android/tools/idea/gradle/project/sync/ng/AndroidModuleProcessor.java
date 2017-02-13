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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.android.DependenciesAndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;

class AndroidModuleProcessor {
  static Key<SyncAction.ModuleModels> MODULE_GRADLE_MODELS_KEY = Key.create("module.gradle.models");

  @NotNull private final Project myProject;
  @NotNull private final IdeModifiableModelsProvider myModelsProvider;
  @NotNull private final GradleSyncState mySyncState;
  @NotNull private final AndroidModuleValidator.Factory myModuleValidatorFactory;
  @NotNull private final AndroidModuleSetup myAndroidModuleSetup;

  AndroidModuleProcessor(@NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    this(project, modelsProvider, GradleSyncState.getInstance(project), new AndroidModuleValidator.Factory(),
         new AndroidModuleSetup(new DependenciesAndroidModuleSetupStep()));
  }

  public AndroidModuleProcessor(@NotNull Project project,
                                @NotNull IdeModifiableModelsProvider modelsProvider,
                                @NotNull GradleSyncState syncState,
                                @NotNull AndroidModuleValidator.Factory moduleValidatorFactory,
                                @NotNull AndroidModuleSetup moduleSetup) {
    myProject = project;
    myModelsProvider = modelsProvider;
    mySyncState = syncState;
    myModuleValidatorFactory = moduleValidatorFactory;
    myAndroidModuleSetup = moduleSetup;
  }

  void processAndroidModels(@NotNull List<Module> androidModules, @NotNull ProgressIndicator indicator) {
    AndroidModuleValidator moduleValidator = myModuleValidatorFactory.create(myProject);
    for (Module module : androidModules) {
      AndroidModuleModel androidModel = findAndroidModel(module);
      if (androidModel != null) {
        SyncAction.ModuleModels moduleModels = module.getUserData(MODULE_GRADLE_MODELS_KEY);
        assert moduleModels != null;
        // We need to set up dependencies once all modules are created.
        boolean syncSkipped = mySyncState.isSyncSkipped();
        myAndroidModuleSetup.setUpModule(module, myModelsProvider, androidModel, moduleModels, indicator, syncSkipped);
        moduleValidator.validate(module, androidModel);
      }
    }
    moduleValidator.fixAndReportFoundIssues();
  }

  @Nullable
  private AndroidModuleModel findAndroidModel(@NotNull Module module) {
    AndroidFacet facet = findFacet(module, myModelsProvider, AndroidFacet.ID);
    return facet != null ? AndroidModuleModel.get(facet) : null;
  }
}
