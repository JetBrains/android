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

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;

import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class JavaModuleModelDataService extends ModuleModelDataService<JavaModuleModel> {
  @NotNull private final ModuleSetupContext.Factory myModuleSetupContextFactory;
  @NotNull private final JavaModuleSetup myModuleSetup;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public JavaModuleModelDataService() {
    this(new ModuleSetupContext.Factory(), new JavaModuleSetup());
  }

  @VisibleForTesting
  JavaModuleModelDataService(@NotNull ModuleSetupContext.Factory moduleSetupContextFactory,
                             @NotNull JavaModuleSetup moduleSetup) {
    myModuleSetupContextFactory = moduleSetupContextFactory;
    myModuleSetup = moduleSetup;
  }

  @Override
  @NotNull
  public Key<JavaModuleModel> getTargetDataKey() {
    return JAVA_MODULE_MODEL;
  }

  @Override
  protected void importData(@NotNull Collection<? extends DataNode<JavaModuleModel>> toImport,
                            @NotNull Project project,
                            @NotNull IdeModifiableModelsProvider modelsProvider,
                            @NotNull Map<String, DataNode<JavaModuleModel>> modelsByModuleName) {
    for (Module module : modelsProvider.getModules()) {
      DataNode<JavaModuleModel> javaModuleModelNode = modelsByModuleName.get(module.getName());
      if (javaModuleModelNode != null) {
        ModuleSetupContext context = myModuleSetupContextFactory.create(module, modelsProvider);
        myModuleSetup.setUpModule(context, javaModuleModelNode.getData());
      }
      else {
        ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(module);
        removeAllFacets(facetModel, JavaFacet.getFacetTypeId());      }
    }
  }
}
