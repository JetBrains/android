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

import com.android.tools.idea.gradle.ContentRootSourcePaths;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.gradle.tooling.BuildActionExecuter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link AndroidGradleProjectResolver}.
 */
public class AndroidGradleProjectResolverTest extends TestCase {
  private ContentRootSourcePaths myExpectedSourcePaths;
  private IdeaProjectStub myIdeaProject;
  private AndroidProjectStub myAndroidProject;
  private BuildActionExecuter<ProjectImportAction.AllModels> myBuildActionExecutor;
  private GradleExecutionHelperDouble myHelper;
  private AndroidGradleProjectResolver myProjectResolver;
  private IdeaModuleStub myUtilModule;
  private ProjectImportAction.AllModels myAllModels;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myExpectedSourcePaths = new ContentRootSourcePaths();

    myIdeaProject = new IdeaProjectStub("multiProject");
    myAndroidProject = TestProjects.createBasicProject(myIdeaProject.getRootDir());

    IdeaModuleStub ideaModuleWithAndroidProject = myIdeaProject.addModule(myAndroidProject.getName(), "androidTask");
    myUtilModule = myIdeaProject.addModule("util", "compileJava", "jar", "classes");
    myIdeaProject.addModule("notReallyAGradleProject");


    myAllModels = new ProjectImportAction.AllModels(myIdeaProject);
    myAllModels.addAndroidProject(myAndroidProject, ideaModuleWithAndroidProject);

    //noinspection unchecked
    myBuildActionExecutor = createMock(BuildActionExecuter.class);
    myHelper = GradleExecutionHelperDouble.newMock();
    myProjectResolver = new AndroidGradleProjectResolver(myHelper, createMock(ProjectImportErrorHandler.class));
  }

  @Override
  protected void tearDown() throws Exception {
    if (myIdeaProject != null) {
      myIdeaProject.dispose();
    }
    super.tearDown();
  }

  @SuppressWarnings("unchecked")
  public void testResolveProjectInfo() {
    // Quick test of isIdeaTask
    assertTrue(AndroidGradleProjectResolver.isIdeaTask("idea"));
    assertTrue(AndroidGradleProjectResolver.isIdeaTask("ideaFoo"));
    assertFalse(AndroidGradleProjectResolver.isIdeaTask("ideal"));


    // Record mock expectations.
    expect(myBuildActionExecutor.run()).andReturn(myAllModels);

    replay(myBuildActionExecutor, myHelper);

    // Code under test.
    String projectPath = myIdeaProject.getBuildFile().getParentFile().getPath();
    DataNode<ProjectData> projectInfo =
      myProjectResolver.resolveProjectInfo(projectPath, myBuildActionExecutor);

    // Verify mock expectations.
    verify(myBuildActionExecutor, myHelper);

    // Verify project.
    assertNotNull(projectInfo);
    ProjectData projectData = projectInfo.getData();
    assertEquals(myIdeaProject.getName(), projectData.getName());
    assertEquals(FileUtil.toSystemIndependentName(myIdeaProject.getRootDir().getAbsolutePath()),
                 projectData.getIdeProjectFileDirectoryPath());

    // Verify modules.
    List<DataNode<ModuleData>> modules = Lists.newArrayList(ExternalSystemApiUtil.getChildren(projectInfo, ProjectKeys.MODULE));
    assertEquals("Module count", 2, modules.size());

    // Verify 'basic' module.
    DataNode<ModuleData> moduleInfo = modules.get(0);
    ModuleData moduleData = moduleInfo.getData();
    assertEquals(myAndroidProject.getName(), moduleData.getName());

    // Verify content root in 'basic' module.
    List<DataNode<ContentRootData>> contentRoots = Lists.newArrayList(ExternalSystemApiUtil.getChildren(moduleInfo, ProjectKeys.CONTENT_ROOT));
    assertEquals(1, contentRoots.size());

    String projectRootDirPath = FileUtil.toSystemIndependentName(myAndroidProject.getRootDir().getAbsolutePath());
    ContentRootData contentRootData = contentRoots.get(0).getData();
    assertEquals(projectRootDirPath, contentRootData.getRootPath());
    myExpectedSourcePaths.storeExpectedSourcePaths(myAndroidProject);
    assertCorrectStoredDirPaths(contentRootData, ExternalSystemSourceType.SOURCE);
    assertCorrectStoredDirPaths(contentRootData, ExternalSystemSourceType.TEST);

    // Verify 'util' module.
    moduleInfo = modules.get(1);
    moduleData = moduleInfo.getData();
    assertEquals(myUtilModule.getName(), moduleData.getName());

    // Verify content root in 'util' module.
    contentRoots = Lists.newArrayList(ExternalSystemApiUtil.getChildren(moduleInfo, ProjectKeys.CONTENT_ROOT));
    assertEquals(1, contentRoots.size());

    String moduleRootDirPath = FileUtil.toSystemIndependentName(myUtilModule.getRootDir().getPath());
    contentRootData = contentRoots.get(0).getData();
    assertEquals(moduleRootDirPath, contentRootData.getRootPath());
  }

  private void assertCorrectStoredDirPaths(@NotNull ContentRootData contentRootData, @NotNull ExternalSystemSourceType sourceType) {
    myExpectedSourcePaths.assertCorrectStoredDirPaths(contentRootData.getPaths(sourceType), sourceType);
  }
}
