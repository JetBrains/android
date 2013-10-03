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
package com.android.tools.idea.gradle.project;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import junit.framework.TestCase;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.idea.IdeaProject;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link ProjectImportAction}.
 */
public class ProjectImportActionTest extends TestCase {
  private BuildController myBuildController;
  private IdeaProjectStub myIdeaProject;
  private ProjectImportAction myAction;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myBuildController = createMock(BuildController.class);
    myIdeaProject = new IdeaProjectStub("aProject");
    myAction = new ProjectImportAction();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myIdeaProject != null) {
      myIdeaProject.dispose();
    }
    super.tearDown();
  }

  public void testExecuteWithIdeaProjectContainingAndroidProjects() {
    AndroidProjectStub androidProject = TestProjects.createBasicProject(myIdeaProject.getRootDir());

    IdeaModuleStub androidModule = myIdeaProject.addModule(androidProject.getName(), "androidTask");
    IdeaModuleStub javaModule = myIdeaProject.addModule("util", "compileJava", "jar", "classes");

    expect(myBuildController.getModel(IdeaProject.class)).andReturn(myIdeaProject);
    expect(myBuildController.getModel(androidModule, AndroidProject.class)).andReturn(androidProject);

    replay(myBuildController);

    // Code to test.
    ProjectImportAction.AllModels allModels = myAction.execute(myBuildController);
    assertNotNull(allModels);
    assertSame(androidProject, allModels.getAndroidProject(androidModule));
    assertNull(allModels.getAndroidProject(javaModule));
    assertTrue(allModels.hasAndroidProjects());

    verify(myBuildController);
  }

  public void testExecuteWithIdeaProjectNotContainingAndroidProjects() {
    myIdeaProject.addModule("util", "compileJava", "jar", "classes");

    expect(myBuildController.getModel(IdeaProject.class)).andReturn(myIdeaProject);
    replay(myBuildController);

    // Code to test.
    ProjectImportAction.AllModels allModels = myAction.execute(myBuildController);
    assertNull(allModels);

    verify(myBuildController);
  }
}
