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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ImportedModule;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleDisposer;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.IMPORTED_MODULE;

/**
 * Removes modules from the project that where not created by the "Sync with Gradle" action.
 */
public class ModuleDisposalDataService extends AbstractProjectDataService<ImportedModule, Void> {
  @NotNull private final ModuleDisposer myModuleDisposer;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public ModuleDisposalDataService() {
    this(new ModuleDisposer());
  }

  @VisibleForTesting
  ModuleDisposalDataService(@NotNull ModuleDisposer moduleDisposer) {
    myModuleDisposer = moduleDisposer;
  }

  @Override
  @NotNull
  public Key<ImportedModule> getTargetDataKey() {
    return IMPORTED_MODULE;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ImportedModule>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    // IntelliJ supports several gradle projects linked to one IDEA project it will be separate processes for these gradle projects importing
    // also IntelliJ does not prevent to mix gradle projects with non-gradle ones.
    // See https://youtrack.jetbrains.com/issue/IDEA-137433
    if (toImport.isEmpty() || !myModuleDisposer.canDisposeModules(project)) {
      return;
    }

    Module[] modules = modelsProvider.getModules();
    if (modules.length != toImport.size()) {
      Map<String, Module> modulesByName = new HashMap<>();
      for (Module module : modules) {
        modulesByName.put(module.getName(), module);
      }
      for (DataNode<ImportedModule> dataNode : toImport) {
        ImportedModule importedModule = dataNode.getData();
        modulesByName.remove(importedModule.getName());
      }

      Collection<Module> modulesToDispose = new ArrayList<>(modulesByName.values());
      myModuleDisposer.disposeModules(modulesToDispose, project, modelsProvider);
    }
  }
}
