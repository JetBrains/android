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

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacetType;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class NdkModuleModelDataService extends ModuleModelDataService<NdkModuleModel> {

  public NdkModuleModelDataService() { }

  @Override
  @NotNull
  public Key<NdkModuleModel> getTargetDataKey() {
    return NDK_MODEL;
  }

  @Override
  protected void importData(@NotNull Collection<? extends DataNode<NdkModuleModel>> toImport,
                            @NotNull Project project,
                            @NotNull IdeModifiableModelsProvider modelsProvider,
                            @NotNull Map<String, DataNode<NdkModuleModel>> modelsByModuleName) {
    for (Module module : modelsProvider.getModules()) {
      DataNode<NdkModuleModel> ndkModuleModelNode = modelsByModuleName.get(module.getName());
      if (ndkModuleModelNode != null) {
        NdkFacet facet = findFacet(module, modelsProvider, NdkFacet.getFacetTypeId());
        if (facet == null) {
          // Module does not have Native Android facet. Create one and add it.
          ModifiableFacetModel model = modelsProvider.getModifiableFacetModel(module);
          NdkFacetType facetType = NdkFacet.getFacetType();
          facet = facetType.createFacet(module, NdkFacet.getFacetName(), facetType.createDefaultConfiguration(), null);
          //noinspection UnstableApiUsage
          model.addFacet(facet, ExternalSystemApiUtil.toExternalSource(GradleConstants.SYSTEM_ID));
        }
        facet.setNdkModuleModel(ndkModuleModelNode.getData());
      }
      else {
        ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(module);
        removeAllFacets(facetModel, NdkFacet.getFacetTypeId());
      }
    }
  }
}
