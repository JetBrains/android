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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link Projects}.
 */
public class ProjectsTest extends IdeaTestCase {
  public void testIsGradleProjectWithRegularProject() {
    assertFalse(Projects.requiresAndroidModel(myProject));
  }

  public void testIsGradleProject() {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
      facet.getProperties().ALLOW_USER_CONFIGURATION = false;
      facetModel.addFacet(facet);
    } finally {
      ApplicationManager.getApplication().runWriteAction(facetModel::commit);
    }

    assertTrue(Projects.requiresAndroidModel(myProject));
  }

  public void testGetSelectedModules() {
    addAndroidGradleFacet();

    DataContext dataContext = createMock(DataContext.class);
    Module[] data = {myModule};
    expect(dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY.getName())).andReturn(data);

    replay(dataContext);

    Module[] selectedModules = Projects.getModulesToBuildFromSelection(myProject, dataContext);
    assertSame(data, selectedModules);

    verify(dataContext);
  }

  private void addAndroidGradleFacet() {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidGradleFacet facet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
      facetModel.addFacet(facet);
    } finally {
      ApplicationManager.getApplication().runWriteAction(facetModel::commit);
    }
  }

  public void testGetSelectedModulesWithModuleWithoutAndroidGradleFacet() {
    DataContext dataContext = createMock(DataContext.class);
    Module[] data = {myModule};
    expect(dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY.getName())).andReturn(data);

    replay(dataContext);

    Module[] selectedModules = Projects.getModulesToBuildFromSelection(myProject, dataContext);
    assertNotSame(data, selectedModules);
    assertEquals(1, selectedModules.length);
    assertSame(myModule, selectedModules[0]);

    verify(dataContext);
  }
}
