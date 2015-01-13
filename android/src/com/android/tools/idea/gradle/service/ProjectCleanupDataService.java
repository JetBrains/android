/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.service;

import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.ImportedModule;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * Removes modules from the project that where not created by the "Sync with Gradle" action.
 */
public class ProjectCleanupDataService implements ProjectDataService<ImportedModule, Void> {
  @Override
  @NotNull
  public Key<ImportedModule> getTargetDataKey() {
    return AndroidProjectKeys.IMPORTED_MODULE;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ImportedModule>> toImport, @NotNull Project project, boolean synchronous) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();
    if (modules.length != toImport.size()) {
      final Map<String, Module> modulesByName = Maps.newHashMap();
      for (Module module : modules) {
        modulesByName.put(module.getName(), module);
      }
      for (DataNode<ImportedModule> dataNode : toImport) {
        String importedModuleName = dataNode.getData().getName();
        modulesByName.remove(importedModuleName);
      }
      if (!modulesByName.isEmpty()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
            try {
              for (Module module : modulesByName.values()) {
                moduleModel.disposeModule(module);
              }
            }
            finally {
              moduleModel.commit();
            }
          }
        });
      }
    }
  }

  @Override
  public void removeData(@NotNull Collection<? extends Void> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
