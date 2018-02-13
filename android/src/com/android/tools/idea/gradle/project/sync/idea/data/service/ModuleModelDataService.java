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
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public abstract class ModuleModelDataService<T extends ModuleModel> extends AbstractProjectDataService<T, Void> {
  @Override
  public final void importData(@NotNull Collection<DataNode<T>> toImport,
                               @Nullable ProjectData projectData,
                               @NotNull Project project,
                               @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (toImport.isEmpty()) {
      // there can be other build systems which can use the same project elements for the import
      if(projectData != null && projectData.getOwner().equals(GradleUtil.GRADLE_SYSTEM_ID)) {
        onModelsNotFound(modelsProvider);
      }
      return;
    }
    try {
      importData(toImport, project, modelsProvider);
    }
    catch (Throwable e) {
      getLog().info(String.format("Failed to set up modules in project '%1$s'", project.getName()), e);
      String msg = e.getMessage();
      GradleSyncState.getInstance(project).syncFailed(isNotEmpty(msg) ? msg : e.getClass().getCanonicalName());
    }
  }

  protected void onModelsNotFound(@NotNull IdeModifiableModelsProvider modelsProvider) {
  }

  private void importData(@NotNull Collection<DataNode<T>> toImport,
                          @NotNull Project project,
                          @NotNull IdeModifiableModelsProvider modelsProvider) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() {
        if (project.isDisposed()) {
          return;
        }
        Map<String, T> modelsByName = indexByModuleName(toImport);
        importData(toImport, project, modelsProvider, modelsByName);
      }
    }.execute();
    Throwable error = result.getThrowable();
    if (error != null) {
      throw error;
    }
  }

  protected abstract void importData(@NotNull Collection<DataNode<T>> toImport,
                                     @NotNull Project project,
                                     @NotNull IdeModifiableModelsProvider modelsProvider,
                                     @NotNull Map<String, T> modelsByName);

  @NotNull
  private Map<String, T> indexByModuleName(@NotNull Collection<DataNode<T>> dataNodes) {
    if (dataNodes.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, T> index = new HashMap<>();
    for (DataNode<T> dataNode : dataNodes) {
      T model = dataNode.getData();
      index.put(model.getModuleName(), model);
    }
    return index;
  }

  @NotNull
  protected Logger getLog() {
    return Logger.getInstance(getClass());
  }
}
