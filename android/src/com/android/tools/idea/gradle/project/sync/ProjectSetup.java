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

import com.android.tools.idea.gradle.project.sync.setup.module.ModuleSetup;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeProjectChangeAction;

abstract class ProjectSetup {
  abstract void setUpProject(@NotNull SyncAction.ProjectModels models, @NotNull ProgressIndicator indicator);

  abstract void commit(boolean synchronous);

  static class Factory {
    @NotNull
    ProjectSetup create(@NotNull Project project) {
      return new ProjectSetupImpl(project);
    }
  }

  private static class ProjectSetupImpl extends ProjectSetup {
    private static Key<SyncAction.ModuleModels> MODULE_GRADLE_MODELS_KEY = Key.create("module.gradle.models");

    @NotNull private final Project myProject;
    @NotNull private final IdeModifiableModelsProvider myModelsProvider;
    @NotNull private final ModuleFactory myModuleFactory;
    @NotNull private final ModuleSetup myModuleSetup;

    ProjectSetupImpl(@NotNull Project project) {
      myProject = project;
      myModelsProvider = new IdeModifiableModelsProviderImpl(myProject);
      myModuleFactory = new ModuleFactory(myProject, myModelsProvider);
      myModuleSetup = new ModuleSetup(myModelsProvider);
    }

    @Override
    void setUpProject(@NotNull SyncAction.ProjectModels models, @NotNull ProgressIndicator indicator) {
      createModules(models, indicator);
      setUpModules(indicator);
    }

    private void createModules(@NotNull SyncAction.ProjectModels projectModels, @NotNull ProgressIndicator indicator) {
      indicator.setText2("Creating modules");

      for (IdeaModule ideaModule : projectModels.getProject().getModules()) {
        // We need to create all modules before setting them up, in case we find inter-module dependencies.
        SyncAction.ModuleModels moduleModels = projectModels.getModels(ideaModule);
        Module module = myModuleFactory.createModule(ideaModule, moduleModels);
        module.putUserData(MODULE_GRADLE_MODELS_KEY, moduleModels);
      }
    }

    void setUpModules(@NotNull ProgressIndicator indicator) {
      for (Module module : myModelsProvider.getModules()) {
        SyncAction.ModuleModels moduleModels = module.getUserData(MODULE_GRADLE_MODELS_KEY);
        if (moduleModels == null) {
          // This module was created in the last sync action. Mark this module for deletion.
          // TODO remove module
          continue;
        }
        myModuleSetup.setUpModule(module, moduleModels, indicator);
        module.putUserData(MODULE_GRADLE_MODELS_KEY, null);
      }
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
