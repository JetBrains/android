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
package com.android.tools.idea.gradle.project.sync.setup;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Tests for {@link Facets}.
 */
public class FacetsTest extends HeavyPlatformTestCase {
  public void testRemoveAllFacetsWithAndroidFacets() throws Exception {
    createAndAddAndroidFacet(myModule);
    FacetManager facetManager = FacetManager.getInstance(myModule);
    assertEquals(1, facetManager.getFacetsByType(AndroidFacet.ID).size());

    IdeModifiableModelsProvider modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(getProject());
    ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(myModule);
    Facets.removeAllFacets(facetModel, AndroidFacet.ID);

    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    assertEquals(0, facetManager.getFacetsByType(AndroidFacet.ID).size());
  }

  public void testRemoveAllFacetsWithAndroidGradleFacets() throws Exception {
    createAndAddGradleFacet(myModule);
    FacetManager facetManager = FacetManager.getInstance(myModule);
    assertEquals(1, facetManager.getFacetsByType(GradleFacet.getFacetTypeId()).size());

    IdeModifiableModelsProvider modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(getProject());
    ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(myModule);
    Facets.removeAllFacets(facetModel, GradleFacet.getFacetTypeId());

    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    assertEquals(0, facetManager.getFacetsByType(GradleFacet.getFacetTypeId()).size());
  }
}
