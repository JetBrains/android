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
import com.android.tools.idea.gradle.model.android.AndroidProjectStub;
import com.android.tools.idea.gradle.model.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.model.gradle.IdeaProjectStub;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import junit.framework.TestCase;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.List;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link MultiProjectResolverStrategy}.
 */
public class MultiProjectResolverStrategyTest extends TestCase {
  private ContentRootSourcePaths myExpectedSourcePaths;
  private IdeaProjectStub myIdeaProject;
  private AndroidProjectStub myAndroidProject;
  private ExternalSystemTaskId myId;
  private ProjectConnection myConnection;
  private GradleExecutionHelperDouble myHelper;
  private GradleExecutionSettings mySettings;
  private MultiProjectResolverStrategy myStrategy;
  private IdeaModuleStub myUtilModule;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myExpectedSourcePaths = new ContentRootSourcePaths();
    myIdeaProject = new IdeaProjectStub("multiProject");
    myAndroidProject = TestProjects.createBasicProject(myIdeaProject.getRootDir());
    myIdeaProject.addModule(myAndroidProject.getName());
    myUtilModule = myIdeaProject.addModule("util");
    myId = ExternalSystemTaskId.create(ExternalSystemTaskType.RESOLVE_PROJECT);
    myConnection = createMock(ProjectConnection.class);
    myHelper = GradleExecutionHelperDouble.newMock();
    mySettings = createMock(GradleExecutionSettings.class);
    myStrategy = new MultiProjectResolverStrategy(myHelper);
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
    // Record mock expectations.
    ModelBuilder<IdeaProject> ideaProjectModelBuilder = createMock(ModelBuilder.class);
    myHelper.getModelBuilder(IdeaProject.class, myId, mySettings, myConnection);
    expectLastCall().andReturn(ideaProjectModelBuilder);

    // Simulate retrieval of the top-level IdeaProject.
    expect(ideaProjectModelBuilder.get()).andReturn(myIdeaProject);

    // Simulate retrieval of AndroidProject from IdeaModule 'basic'
    myHelper.setExecutionResult(myAndroidProject);

    replay(myConnection, myHelper, ideaProjectModelBuilder);

    // Code under test.
    String projectPath = myIdeaProject.getBuildFile().getAbsolutePath();
    DataNode<ProjectData> projectInfo = myStrategy.resolveProjectInfo(myId, projectPath, mySettings, myConnection);

    // Verify mock expectations.
    verify(myConnection, myHelper, ideaProjectModelBuilder);

    // Verify project.
    assertNotNull(projectInfo);
    ProjectData projectData = projectInfo.getData();
    assertEquals(myIdeaProject.getName(), projectData.getName());
    assertEquals(myIdeaProject.getRootDir().getAbsolutePath(), projectData.getProjectFileDirectoryPath());

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

    String projectRootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    ContentRootData contentRootData = contentRoots.get(0).getData();
    assertEquals(projectRootDirPath, contentRootData.getRootPath());
    myExpectedSourcePaths.storeExpectedSourcePaths(myAndroidProject);
    myExpectedSourcePaths.assertCorrectSourceDirectoryPaths(contentRootData);

    // Verify 'util' module.
    moduleInfo = modules.get(1);
    moduleData = moduleInfo.getData();
    assertEquals(myUtilModule.getName(), moduleData.getName());

    // Verify content root in 'util' module.
    contentRoots = Lists.newArrayList(ExternalSystemApiUtil.getChildren(moduleInfo, ProjectKeys.CONTENT_ROOT));
    assertEquals(1, contentRoots.size());

    String moduleRootDirPath = myUtilModule.getRootDir().getPath();
    contentRootData = contentRoots.get(0).getData();
    assertEquals(moduleRootDirPath, contentRootData.getRootPath());
  }
}
