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
package com.android.tools.idea.gradle.project.sync.setup.module.ndk;

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacetType;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetupStep;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class NdkFacetModuleSetupStep extends NdkModuleSetupStep {
  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull NdkModuleModel ndkModuleModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    NdkFacet facet = findFacet(module, ideModelsProvider, NdkFacet.getFacetTypeId());
    if (facet != null) {
      configureFacet(facet, ndkModuleModel);
    }
    else {
      // Module does not have Native Android facet. Create one and add it.
      ModifiableFacetModel model = ideModelsProvider.getModifiableFacetModel(module);
      NdkFacetType facetType = NdkFacet.getFacetType();
      facet = facetType.createFacet(module, NdkFacet.getFacetName(), facetType.createDefaultConfiguration(), null);
      model.addFacet(facet);
      configureFacet(facet, ndkModuleModel);
    }
  }

  private static void configureFacet(@NotNull NdkFacet facet, @NotNull NdkModuleModel model) {
    String selectedVariant = facet.getConfiguration().SELECTED_BUILD_VARIANT;
    if (isNotEmpty(selectedVariant)) {
      model.setSelectedVariantName(selectedVariant);
    }
    facet.setNdkModuleModel(model);
  }

  @Override
  public boolean invokeOnSkippedSync() {
    return true;
  }
}
