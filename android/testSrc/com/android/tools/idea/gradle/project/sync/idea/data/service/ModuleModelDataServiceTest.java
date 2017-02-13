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

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ModuleModelDataService}.
 */
public class ModuleModelDataServiceTest extends IdeaTestCase {
  public void testImportDataWithEmptyDataNodeCollection() {
    Collection<DataNode<NdkModuleModel>> toImport = new ArrayList<>();
    Project project = getProject();
    IdeModifiableModelsProviderImpl modelsProvider = new IdeModifiableModelsProviderImpl(project);

    MyModuleModelDataService dataService = mock(MyModuleModelDataService.class);
    dataService.importData(toImport, mock(ProjectData.class), project, modelsProvider);

    verify(dataService, never()).importData(same(toImport), same(project), same(modelsProvider), any());
  }

  private static class MyModuleModelDataService extends ModuleModelDataService<NdkModuleModel> {
    @Override
    @NotNull
    public Key<NdkModuleModel> getTargetDataKey() {
      return NDK_MODEL;
    }

    @Override
    protected void importData(@NotNull Collection<DataNode<NdkModuleModel>> toImport,
                              @NotNull Project project,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @NotNull Map<String, NdkModuleModel> modelsByName) {
    }
  }
}