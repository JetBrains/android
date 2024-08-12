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
package com.google.idea.blaze.android.sync.projectstructure;

import com.android.AndroidProjectTypes;
import com.android.builder.model.AndroidProject;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.jetbrains.android.facet.AndroidFacetPropertiesCompat;

/** Adds the Android facet to modules imported from {@link AndroidProject}s. */
public class AndroidFacetModuleCustomizer {

  public static void createAndroidFacet(Module module, boolean isApp) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      configureFacet(facet, isApp);
    } else {
      // Module does not have Android facet. Create one and add it.
      FacetManager facetManager = FacetManager.getInstance(module);
      ModifiableFacetModel model = facetManager.createModifiableModel();
      try {
        facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
        model.addFacet(facet);
        configureFacet(facet, isApp);
      } finally {
        model.commit();
      }
    }
  }

  public static void removeAndroidFacet(Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      ModifiableFacetModel facetModel = FacetManager.getInstance(module).createModifiableModel();
      facetModel.removeFacet(facet);
      facetModel.commit();
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
    AndroidFacetPropertiesCompat.enableSourcesAutogeneration(facetState, false);
  }
}
