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

import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static org.easymock.EasyMock.*;

/**
 * Tests for {@link GradleProjects}.
 */
public class GradleProjectsTest extends IdeaTestCase {
  public void testIsGradleProjectWithRegularProject() {
    assertFalse(AndroidProjectInfo.getInstance(myProject).requiresAndroidModel());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
      // Setup the project and the module as a Gradle project system so that their roots could be determined.
      ExternalSystemModulePropertyManager
        .getInstance(myModule)
        .setExternalOptions(
          GradleUtil.GRADLE_SYSTEM_ID,
          new ModuleData(":", GradleUtil.GRADLE_SYSTEM_ID, StdModuleTypes.JAVA.getId(), myProject.getBaseDir().getName(),
                         myProject.getBasePath(), myProject.getBasePath()),
          new ProjectData(GradleUtil.GRADLE_SYSTEM_ID, myProject.getName(), myProject.getBasePath(), myProject.getBasePath()));
  }

  public void testIsGradleProject() {
    AndroidFacet facet = createAndAddAndroidFacet(myModule);
    facet.getProperties().ALLOW_USER_CONFIGURATION = false;

    assertTrue(AndroidProjectInfo.getInstance(myProject).requiresAndroidModel());
  }

  public void testGetSelectedModules() {
    createAndAddGradleFacet(myModule);

    DataContext dataContext = createMock(DataContext.class);
    Module[] data = {myModule};
    expect(dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY.getName())).andReturn(data);

    replay(dataContext);

    Module[] selectedModules = GradleProjects.getModulesToBuildFromSelection(myProject, dataContext);
    assertSame(data, selectedModules);

    verify(dataContext);
  }

  public void testGetSelectedModulesWithModuleWithoutAndroidGradleFacet() {
    DataContext dataContext = createMock(DataContext.class);
    Module[] data = {myModule};
    expect(dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY.getName())).andReturn(data);

    replay(dataContext);

    Module[] selectedModules = GradleProjects.getModulesToBuildFromSelection(myProject, dataContext);
    assertNotSame(data, selectedModules);
    assertEquals(1, selectedModules.length);
    assertSame(myModule, selectedModules[0]);

    verify(dataContext);
  }
}
