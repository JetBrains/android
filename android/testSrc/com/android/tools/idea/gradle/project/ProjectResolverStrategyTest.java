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

import com.android.build.gradle.model.AndroidProject;
import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.ContentRootSourcePaths;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.model.android.AndroidProjectStub;
import com.android.tools.idea.gradle.model.android.VariantStub;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import junit.framework.TestCase;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;

import java.util.Collection;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link ProjectResolverStrategy}.
 */
public class ProjectResolverStrategyTest extends TestCase {
  private ContentRootSourcePaths myExpectedSourcePaths;
  private AndroidProjectStub myAndroidProject;

  private ExternalSystemTaskId myId;
  private ProjectConnection myConnection;
  private GradleExecutionHelper myHelper;
  private ModelBuilder<AndroidProject> myModelBuilder;

  private ProjectResolverStrategy myStrategy;

  @SuppressWarnings("unchecked")
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myExpectedSourcePaths = new ContentRootSourcePaths();
    myAndroidProject = TestProjects.createBasicProject();
    myId = ExternalSystemTaskId.create(ExternalSystemTaskType.RESOLVE_PROJECT);
    myConnection = createMock(ProjectConnection.class);
    myHelper = createMock(GradleExecutionHelper.class);
    myModelBuilder = createMock(ModelBuilder.class);
    myStrategy = new ProjectResolverStrategy(myHelper);
  }
  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  public void testResolveProjectInfo() {
    // Record mock expectations.
    myHelper.getModelBuilder(AndroidProject.class, myId, null, myConnection);
    expectLastCall().andReturn(myModelBuilder);

    expect(myModelBuilder.get()).andReturn(myAndroidProject);

    replay(myConnection, myHelper, myModelBuilder);

    // Code under test.
    String projectPath = myAndroidProject.getBuildFile().getAbsolutePath();
    DataNode<ProjectData> projectInfo = myStrategy.resolveProjectInfo(myId, projectPath, null, myConnection);

    // Verify mock expectations.
    verify(myConnection, myHelper, myModelBuilder);

    // Verify project.
    assertNotNull(projectInfo);
    ProjectData projectData = projectInfo.getData();
    assertEquals(myAndroidProject.getName(), projectData.getName());
    assertEquals(myAndroidProject.getRootDir().getAbsolutePath(), projectData.getProjectFileDirectoryPath());

    // Verify module.
    Collection<DataNode<ModuleData>> modules = ExternalSystemUtil.getChildren(projectInfo, ProjectKeys.MODULE);
    assertEquals("Module count", 1, modules.size());
    DataNode<ModuleData> moduleInfo = modules.iterator().next();
    ModuleData moduleData = moduleInfo.getData();
    assertEquals(myAndroidProject.getName(), moduleData.getName());

    // Verify that IdeaAndroidProject was stored in module.
    Collection<DataNode<IdeaAndroidProject>> ideAndroidProjects =
      ExternalSystemUtil.getChildren(moduleInfo, AndroidProjectKeys.IDE_ANDROID_PROJECT);
    assertEquals(1, ideAndroidProjects.size());
    IdeaAndroidProject ideaAndroidProject = ideAndroidProjects.iterator().next().getData();
    assertSame(myAndroidProject, ideaAndroidProject.getDelegate());

    // Verify that there is a selected build variant.
    VariantStub selectedVariant = myAndroidProject.getFirstVariant();
    assertNotNull(selectedVariant);
    assertEquals(selectedVariant.getName(), ideaAndroidProject.getSelectedVariantName());

    // Verify content root.
    Collection<DataNode<ContentRootData>> contentRoots = ExternalSystemUtil.getChildren(moduleInfo, ProjectKeys.CONTENT_ROOT);
    assertEquals(1, contentRoots.size());

    String projectRootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    ContentRootData contentRootData = contentRoots.iterator().next().getData();
    assertEquals(projectRootDirPath, contentRootData.getRootPath());
    myExpectedSourcePaths.storeExpectedSourcePaths(myAndroidProject);
    myExpectedSourcePaths.assertCorrectSourceDirectoryPaths(contentRootData);
  }
}
