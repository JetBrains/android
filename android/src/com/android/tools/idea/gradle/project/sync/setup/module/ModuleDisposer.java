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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.post.project.DisposedModules;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;

public class ModuleDisposer {
  @NotNull private final IdeInfo myIdeInfo;

  public ModuleDisposer() {
    this(IdeInfo.getInstance());
  }

  @VisibleForTesting
  ModuleDisposer(@NotNull IdeInfo ideInfo) {
    myIdeInfo = ideInfo;
  }

  public void disposeModules(@NotNull Collection<Module> modules,
                             @NotNull Project project,
                             @NotNull IdeModifiableModelsProvider ideModelsProvider) {
    if (!modules.isEmpty()) {
      ModifiableModuleModel moduleModel = ideModelsProvider.getModifiableModuleModel();

      List<File> imlFilesToRemove = new ArrayList<>();

      for (Module toDispose : modules) {
        File imlFilePath = toSystemDependentPath(toDispose.getModuleFilePath());
        imlFilesToRemove.add(imlFilePath);
        moduleModel.disposeModule(toDispose);
      }

      DisposedModules.getInstance(project).markImlFilesForDeletion(imlFilesToRemove);
    }
  }

  public boolean canDisposeModules(@NotNull Project project) {
    // IntelliJ supports several gradle projects linked to one IDEA project it will be separate processes for these gradle projects importing
    // also IntelliJ does not prevent to mix gradle projects with non-gradle ones.
    // See https://youtrack.jetbrains.com/issue/IDEA-137433
    return myIdeInfo.isAndroidStudio() && !GradleSyncState.getInstance(project).lastSyncFailedOrHasIssues();
  }
}
