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

import com.android.tools.idea.gradle.project.model.ModuleModel;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ModuleModelDataService<T extends ModuleModel> extends AbstractProjectDataService<T, Void> {
  @Override
  public final void importData(@NotNull Collection<? extends DataNode<T>> toImport,
                               @Nullable ProjectData projectData,
                               @NotNull Project project,
                               @NotNull IdeModifiableModelsProvider modelsProvider) {
    WriteCommandAction.runWriteCommandAction(project, ()->  {
        if (project.isDisposed()) {
          return;
        }
        Map<String, DataNode<T>> modelsByModuleName = indexByModuleName(toImport, modelsProvider);
        importData(toImport, project, modelsProvider, modelsByModuleName);
    });
  }

  protected abstract void importData(@NotNull Collection<? extends DataNode<T>> toImport,
                                     @NotNull Project project,
                                     @NotNull IdeModifiableModelsProvider modelsProvider,
                                     @NotNull Map<String, DataNode<T>> modelsByModuleName);

  @NotNull
  private Map<String, DataNode<T>> indexByModuleName(@NotNull Collection<? extends DataNode<T>> dataNodes,
                                           @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (dataNodes.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, DataNode<T>> index = new HashMap<>();
    for (DataNode<T> dataNode : dataNodes) {
      T model = dataNode.getData();
      String moduleName = model.getModuleName();
      if (dataNode.getParent() != null) {
        ModuleData moduleData = (ModuleData)dataNode.getParent().getData();
        Module module = modelsProvider.findIdeModule(moduleData);
        if (module != null && !module.getName().equals(moduleName)) {
          // If the module name in modelsProvider is different from in moduleData, use module name in modelsProvider as key.
          // This happens when there are multiple *iml files for one module, which can be caused by opening a project created on a different machine,
          // or opening projects with both Intellij and Studio, or moving existing module to different locations.
          moduleName = module.getName();
        }
      }
      index.put(moduleName, dataNode);
    }
    return index;
  }

  @NotNull
  protected Logger getLog() {
    return Logger.getInstance(getClass());
  }
}
