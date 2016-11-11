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
package com.android.tools.idea.gradle.project.sync.setup.module.cpp;

import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.project.facet.cpp.NativeAndroidGradleFacet;
import com.android.tools.idea.gradle.project.facet.cpp.NativeAndroidGradleFacetType;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.module.CppModuleSetupStep;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.Facets.findFacet;
import static com.android.tools.idea.gradle.util.Facets.removeAllFacetsOfType;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class CppAndroidFacetModuleSetupStep extends CppModuleSetupStep {
  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull NativeAndroidGradleModel androidModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    NativeAndroidGradleFacet facet = findFacet(module, ideModelsProvider, NativeAndroidGradleFacet.TYPE_ID);
    if (facet != null) {
      configureFacet(facet, androidModel);
    }
    else {
      // Module does not have Native Android facet. Create one and add it.
      ModifiableFacetModel model = ideModelsProvider.getModifiableFacetModel(module);
      NativeAndroidGradleFacetType facetType = NativeAndroidGradleFacet.getFacetType();
      facet = facetType.createFacet(module, NativeAndroidGradleFacet.NAME, facetType.createDefaultConfiguration(), null);
      model.addFacet(facet);
      configureFacet(facet, androidModel);
    }
  }

  private static void configureFacet(@NotNull NativeAndroidGradleFacet facet, @NotNull NativeAndroidGradleModel androidModel) {
    String selectedVariant = facet.getConfiguration().SELECTED_BUILD_VARIANT;
    if (isNotEmpty(selectedVariant)) {
      androidModel.setSelectedVariantName(selectedVariant);
    }
    facet.setNativeAndroidGradleModel(androidModel);
  }

  @Override
  protected void gradleModelNotFound(@NotNull Module module, @NotNull IdeModifiableModelsProvider ideModelsProvider) {
    ModifiableFacetModel facetModel = ideModelsProvider.getModifiableFacetModel(module);
    removeAllFacetsOfType(NativeAndroidGradleFacet.TYPE_ID, facetModel);
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Native Android Facet setup";
  }
}
