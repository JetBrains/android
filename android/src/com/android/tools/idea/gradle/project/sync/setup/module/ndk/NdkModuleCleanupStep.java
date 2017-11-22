/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleCleanupStep;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;
import static com.android.tools.idea.gradle.project.sync.setup.Facets.removeAllFacets;
import static com.android.tools.idea.gradle.project.sync.setup.module.common.ContentEntriesSetup.removeExistingContentEntries;

public class NdkModuleCleanupStep extends ModuleCleanupStep {
  @Override
  public void cleanUpModule(@NotNull Module module, @NotNull IdeModifiableModelsProvider ideModelsProvider) {
    // See https://code.google.com/p/android/issues/detail?id=229806
    ModifiableFacetModel facetModel = ideModelsProvider.getModifiableFacetModel(module);
    removeAllFacets(facetModel, NdkFacet.getFacetTypeId());

    // remove existing content entries only in android modules
    GradleFacet facet = findFacet(module, ideModelsProvider, GradleFacet.getFacetTypeId());
    if (facet != null) {
      ModifiableRootModel moduleModel = ideModelsProvider.getModifiableRootModel(module);
      removeExistingContentEntries(moduleModel);
    }
  }
}
