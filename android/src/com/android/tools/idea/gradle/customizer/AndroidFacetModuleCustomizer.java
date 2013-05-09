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

import com.android.build.gradle.model.Variant;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.util.Facets;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.base.Strings;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

/**
 * Adds the Android facet to modules imported from {@link com.android.build.gradle.model.AndroidProject}s.
 */
public class AndroidFacetModuleCustomizer implements ModuleCustomizer {
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    if (ideaAndroidProject != null) {
      AndroidFacet facet = Facets.getFirstFacet(module, AndroidFacet.ID);
      if (facet != null) {
        configureFacet(facet, ideaAndroidProject);
      }
      else {
        // Module does not have Android facet. Create one and add it.
        FacetManager facetManager = FacetManager.getInstance(module);
        ModifiableFacetModel model = facetManager.createModifiableModel();
        try {
          facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
          model.addFacet(facet);
          configureFacet(facet, ideaAndroidProject);
        } finally {
          model.commit();
        }
      }
      BuildVariantView buildVariantView = BuildVariantView.getInstance(project);
      buildVariantView.updateContents();
    }
  }

  private static void configureFacet(@NotNull AndroidFacet facet, @NotNull IdeaAndroidProject ideaAndroidProject) {
    facet.setIdeaAndroidProject(ideaAndroidProject);
    JpsAndroidModuleProperties facetState = facet.getConfiguration().getState();
    facetState.ALLOW_USER_CONFIGURATION = false;
    syncSelectedVariant(facetState, ideaAndroidProject);
  }

  private static void syncSelectedVariant(@NotNull JpsAndroidModuleProperties facetState, @NotNull IdeaAndroidProject ideaAndroidProject) {
    if (!Strings.isNullOrEmpty(facetState.SELECTED_BUILD_VARIANT)) {
      ideaAndroidProject.setSelectedVariantName(facetState.SELECTED_BUILD_VARIANT);
      return;
    }
    Variant selectedVariant = ideaAndroidProject.getSelectedVariant();
    facetState.SELECTED_BUILD_VARIANT = selectedVariant.getName();
  }
}
