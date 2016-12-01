/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Utility methods related to facets.
 */
public final class Facets {
  private Facets() {
  }

  @SafeVarargs
  public static <T extends Facet> void removeAllFacets(@NotNull ModifiableFacetModel facetModel, @NotNull FacetTypeId<T>... typeIds) {
    for (FacetTypeId<T> typeId : typeIds) {
      Collection<T> facets = facetModel.getFacetsByType(typeId);
      for (T facet : facets) {
        facetModel.removeFacet(facet);
      }
    }
  }

  /**
   * Tries to find a particular facet of a module from FacetManager, if can't find, fallback to find the modifying facet from
   * modelsProvider.
   */
  @Nullable
  public static <T extends Facet> T findFacet(@NotNull Module module,
                                              @NotNull IdeModifiableModelsProvider modelsProvider,
                                              @NotNull FacetTypeId<T> typeId) {
    T facet = FacetManager.getInstance(module).getFacetByType(typeId);
    if (facet == null) {
      // facet may be present, but not visible if ModifiableFacetModel has not been committed yet (e.g. in the case of a new project.)
      ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(module);
      facet = facetModel.getFacetByType(typeId);
    }
    return facet;
  }
}

