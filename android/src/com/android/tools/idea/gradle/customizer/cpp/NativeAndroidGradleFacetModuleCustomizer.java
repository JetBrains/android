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
package com.android.tools.idea.gradle.customizer.cpp;

import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacetType;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.Facets.findFacet;
import static com.android.tools.idea.gradle.util.Facets.removeAllFacetsOfType;

/**
 * Adds the Native Android Gradle facet to modules imported from {@link NativeAndroidProject}s.
 */
public class NativeAndroidGradleFacetModuleCustomizer implements ModuleCustomizer<NativeAndroidGradleModel> {
  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull Module module,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @Nullable NativeAndroidGradleModel nativeAndroidModel) {
    if (nativeAndroidModel == null) {
      removeAllFacetsOfType(NativeAndroidGradleFacet.TYPE_ID, modelsProvider.getModifiableFacetModel(module));
    }
    else {
      NativeAndroidGradleFacet facet = findFacet(module, modelsProvider, NativeAndroidGradleFacet.TYPE_ID);
      if (facet != null) {
        configureFacet(facet, nativeAndroidModel);
      }
      else {
        // Module does not have Native Android facet. Create one and add it.
        ModifiableFacetModel model = modelsProvider.getModifiableFacetModel(module);
        NativeAndroidGradleFacetType facetType = NativeAndroidGradleFacet.getFacetType();
        facet = facetType.createFacet(module, NativeAndroidGradleFacet.NAME, facetType.createDefaultConfiguration(), null);
        model.addFacet(facet);
        configureFacet(facet, nativeAndroidModel);
      }
    }
  }

  private static void configureFacet(@NotNull NativeAndroidGradleFacet facet, @NotNull NativeAndroidGradleModel nativeAndroidModel) {
    facet.setNativeAndroidGradleModel(nativeAndroidModel);
  }
}
