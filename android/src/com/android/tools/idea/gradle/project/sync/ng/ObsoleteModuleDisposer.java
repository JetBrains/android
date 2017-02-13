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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleDisposer;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.android.tools.idea.gradle.project.sync.ng.AndroidModuleProcessor.MODULE_GRADLE_MODELS_KEY;

class ObsoleteModuleDisposer {
  @NotNull private final Project myProject;
  @NotNull private final ModuleDisposer myModuleDisposer;
  @NotNull private final IdeModifiableModelsProvider myModelsProvider;

  ObsoleteModuleDisposer(@NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    this(project, modelsProvider, new ModuleDisposer());
  }

  @VisibleForTesting
  ObsoleteModuleDisposer(@NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider,
                         @NotNull ModuleDisposer moduleDisposer) {
    myProject = project;
    myModelsProvider = modelsProvider;
    myModuleDisposer = moduleDisposer;
  }

  void disposeObsoleteModules(@NotNull ProgressIndicator indicator) {
    if (!myModuleDisposer.canDisposeModules(myProject)) {
      return;
    }
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
    myModuleDisposer.disposeModules(modulesToDispose, myProject, myModelsProvider);
  }

}
