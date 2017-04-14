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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeProjectChangeAction;

abstract class ProjectSetup {
  abstract void setUpProject(@NotNull SyncAction.ProjectModels models, @NotNull ProgressIndicator indicator);

  abstract void commit();

  static class Factory {
    @NotNull
    ProjectSetup create(@NotNull Project project) {
      return new ProjectSetupImpl(project, new IdeModifiableModelsProviderImpl(project), new ModuleSetup.Factory());
    }
  }

  @VisibleForTesting
  static class ProjectSetupImpl extends ProjectSetup {
    @NotNull private final Project myProject;
    @NotNull private final IdeModifiableModelsProvider myModelsProvider;
    @NotNull private final ModuleSetup.Factory myModuleSetupFactory;

    ProjectSetupImpl(@NotNull Project project,
                     @NotNull IdeModifiableModelsProvider modelsProvider,
                     @NotNull ModuleSetup.Factory moduleSetupFactory) {
      myProject = project;
      myModelsProvider = modelsProvider;
      myModuleSetupFactory = moduleSetupFactory;
    }

    @Override
    void setUpProject(@NotNull SyncAction.ProjectModels models, @NotNull ProgressIndicator indicator) {
      ModuleSetup moduleSetup = myModuleSetupFactory.create(myProject, myModelsProvider);
      try {
        executeProjectChangeAction(true /* synchronous */, new DisposeAwareProjectChange(myProject) {
          @Override
          public void execute() {
            moduleSetup.setUpModules(models, indicator);
          }
        });
      }
      catch (Throwable e) {
        disposeChanges();
        throw e;
      }
    }

    @Override
    void commit() {
      try {
        executeProjectChangeAction(true /* synchronous */, new DisposeAwareProjectChange(myProject) {
          @Override
          public void execute() {
            myModelsProvider.commit();
          }
        });
      }
      catch (Throwable e) {
        disposeChanges();
        throw e;
      }
    }

    private void disposeChanges() {
      executeProjectChangeAction(true /* synchronous */, new DisposeAwareProjectChange(myProject) {
        @Override
        public void execute() {
          myModelsProvider.dispose();
        }
      });
    }
  }
}
