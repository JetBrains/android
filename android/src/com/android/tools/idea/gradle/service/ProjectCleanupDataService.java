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
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.android.tools.idea.gradle.util.Projects.setModulesToDisposePostSync;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;

/**
 * Removes modules from the project that where not created by the "Sync with Gradle" action.
 */
public class ProjectCleanupDataService extends AbstractProjectDataService<ImportedModule, Void> {
  @Override
  @NotNull
  public Key<ImportedModule> getTargetDataKey() {
    return AndroidProjectKeys.IMPORTED_MODULE;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ImportedModule>> toImport,
                         @Nullable final ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final IdeModifiableModelsProvider modelsProvider) {
    // IntelliJ supports several gradle projects linked to one IDEA project it will be separate processes for these gradle projects importing
    // also IntelliJ does not prevent to mix gradle projects with non-gradle ones.
    // See https://youtrack.jetbrains.com/issue/IDEA-137433
    if(!isAndroidStudio()) {
      return;
    }

    Module[] modules = modelsProvider.getModules();
    if (modules.length != toImport.size()) {
      final Map<String, Module> modulesByName = Maps.newHashMap();
      for (Module module : modules) {
        modulesByName.put(module.getName(), module);
      }
      for (DataNode<ImportedModule> dataNode : toImport) {
        String importedModuleName = dataNode.getData().getName();
        modulesByName.remove(importedModuleName);
      }
      Collection<Module> modulesToDispose = Collections.emptyList();
      if (!modulesByName.isEmpty()) {
        modulesToDispose = modulesByName.values();
      }
      setModulesToDisposePostSync(project, modulesToDispose.isEmpty() ? null : modulesToDispose);
    }
  }
}
