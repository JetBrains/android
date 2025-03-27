/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.qsync.projectstructure;

import com.android.AndroidProjectTypes;
import com.android.builder.model.AndroidProject;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetProperties;

/** Adds the Android facet to modules imported from {@link AndroidProject}s. */
public class AndroidFacetModuleCustomizer {

  private final IdeModifiableModelsProvider models;

  public AndroidFacetModuleCustomizer(IdeModifiableModelsProvider models) {
    this.models = models;
  }

  public void createAndroidFacet(Module module, boolean isApp) {
    ModifiableFacetModel modifiableFacetModel = models.getModifiableFacetModel(module);
    AndroidFacet facet = modifiableFacetModel.getFacetByType(AndroidFacet.ID);
    FacetManager facetManager = FacetManager.getInstance(module);
    if (facet != null) {
      configureFacet(facet, isApp);
    } else {
      // Module does not have Android facet. Create one and add it.
      facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
      modifiableFacetModel.addFacet(facet);
      configureFacet(facet, isApp);
    }
  }

  private static void configureFacet(AndroidFacet facet, boolean isApp) {
    AndroidFacetProperties facetState = facet.getProperties();
    facetState.ALLOW_USER_CONFIGURATION = false;
    facetState.PROJECT_TYPE =
        isApp ? AndroidProjectTypes.PROJECT_TYPE_APP : AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
    facetState.MANIFEST_FILE_RELATIVE_PATH = "";
    facetState.RES_FOLDER_RELATIVE_PATH = "";
    facetState.ASSETS_FOLDER_RELATIVE_PATH = "";
  }
}
