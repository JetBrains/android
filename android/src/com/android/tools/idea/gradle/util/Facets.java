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

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Utility methods related to facets.
 */
public final class Facets {
  private Facets() {
  }

  /**
   * Retrieves the first found facet of the given type from the given module.
   *
   * @param module the given module.
   * @param typeId the given facet ID.
   * @param <T> the type of facet.
   * @return the first found facet in the given module.
   */
  @Nullable
  public static <T extends Facet> T getFirstFacet(@NotNull Module module, @NotNull FacetTypeId<T> typeId) {
    FacetManager facetManager = FacetManager.getInstance(module);
    Collection<T> facets = facetManager.getFacetsByType(typeId);
    return ContainerUtil.getFirstItem(facets);
  }

  /**
   * Retrieves the Android-Gradle facet from the given module. If the given module does not have it, this method will create a new one.
   *
   * @param module the given module.
   * @return the Android-Gradle facet from the given module.
   */
  @NotNull
  public static AndroidGradleFacet getAndroidGradleFacet(Module module) {
    AndroidGradleFacet facet = getFirstFacet(module, AndroidGradleFacet.TYPE_ID);
    if (facet != null) {
      return facet;
    }

    // Module does not have Android-Gradle facet. Create one and add it.
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      facet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
      model.addFacet(facet);
    } finally {
      model.commit();
    }
    return facet;
  }
}
