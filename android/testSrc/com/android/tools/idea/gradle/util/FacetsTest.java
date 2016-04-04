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
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Tests for {@link Facets}.
 */
public class FacetsTest extends IdeaTestCase {
  public void testRemoveAllFacetsWithAndroidFacets() throws Exception {
    final FacetManager facetManager = FacetManager.getInstance(myModule);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          ModifiableFacetModel model = facetManager.createModifiableModel();
          try {
            AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
            model.addFacet(facet);
          }
          finally {
            model.commit();
          }
        }
    });
    assertEquals(1, facetManager.getFacetsByType(AndroidFacet.ID).size());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Facets.removeAllFacetsOfType(myModule, AndroidFacet.ID);
      }
    });

    assertEquals(0, facetManager.getFacetsByType(AndroidFacet.ID).size());
  }

  public void testRemoveAllFacetsWithAndroidGradleFacets() throws Exception {
    final FacetManager facetManager = FacetManager.getInstance(myModule);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ModifiableFacetModel model = facetManager.createModifiableModel();
        try {
          AndroidGradleFacet facet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
          model.addFacet(facet);
        }
        finally {
          model.commit();
        }
      }
    });

    assertEquals(1, facetManager.getFacetsByType(AndroidGradleFacet.TYPE_ID).size());

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Facets.removeAllFacetsOfType(myModule, AndroidGradleFacet.TYPE_ID);
      }
    });

    assertEquals(0, facetManager.getFacetsByType(AndroidGradleFacet.TYPE_ID).size());
  }
}
