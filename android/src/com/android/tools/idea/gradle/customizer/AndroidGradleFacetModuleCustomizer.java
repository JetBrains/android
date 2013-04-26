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
package com.android.tools.idea.gradle.customizer;

import com.android.tools.idea.gradle.util.Facets;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adds the Android-Gradle facet to modules that need to be built using Gradle.
 */
public class AndroidGradleFacetModuleCustomizer implements ModuleCustomizer {
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    AndroidGradleFacet facet = Facets.getFirstFacet(module, AndroidGradleFacet.TYPE_ID);
    if (facet != null) {
      return;
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
  }
}
