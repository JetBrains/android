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

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Applies Gradle settings to the modules of an Android project.
 */
public class GradleModuleModelDataService extends ModuleModelDataService<GradleModuleModel> {
  @NotNull private final GradleModuleSetup myModuleSetup;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public GradleModuleModelDataService() {
    this(new GradleModuleSetup());
  }

  @VisibleForTesting
  GradleModuleModelDataService(@NotNull GradleModuleSetup moduleSetup) {
    myModuleSetup = moduleSetup;
  }

  @Override
  @NotNull
  public Key<GradleModuleModel> getTargetDataKey() {
    return GRADLE_MODULE_MODEL;
  }

  @Override
  protected void importData(@NotNull Collection<? extends DataNode<GradleModuleModel>> toImport,
                            @NotNull Project project,
                            @NotNull IdeModifiableModelsProvider modelsProvider,
                            @NotNull Map<String, DataNode<GradleModuleModel>> modelsByModuleName) {
    for (Module module : modelsProvider.getModules()) {
        DataNode<GradleModuleModel> gradleModuleModelDataNode = modelsByModuleName.get(module.getName());
      if (gradleModuleModelDataNode != null) {
        myModuleSetup.setUpModule(module, modelsProvider, gradleModuleModelDataNode.getData());
      }
    }
  }

  @Override
  public void removeData(Computable<? extends Collection<? extends Module>> toRemoveComputable,
                         @NotNull Collection<? extends DataNode<GradleModuleModel>> toIgnore,
                         @NotNull ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    for (Module module : toRemoveComputable.get()) {
      ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(module);
      removeAllFacets(facetModel, GradleFacet.getFacetTypeId());
    }
  }
}
