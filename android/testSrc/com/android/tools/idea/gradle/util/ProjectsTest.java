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

import com.android.tools.idea.AndroidTestCaseHelper;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

/**
 * Tests for {@link Projects}.
 */
public class ProjectsTest extends IdeaTestCase {
  public void testGetJavaHome() {
    Sdk jdk = AndroidTestCaseHelper.createAndSetJdk(myProject);
    File javaHome = Projects.getJavaHome(myProject);
    assertNotNull(javaHome);
    assertEquals(jdk.getHomePath(), javaHome.getPath());
  }

  public void testIsGradleProjectWithNonGradleProject() {
    assertFalse(Projects.isGradleProject(myProject));
  }

  public void testIsGradleProjectWithGradleProject() {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidGradleFacet facet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
      facetModel.addFacet(facet);
    } finally {
      facetModel.commit();
    }
    assertTrue(Projects.isGradleProject(myProject));
  }
}
