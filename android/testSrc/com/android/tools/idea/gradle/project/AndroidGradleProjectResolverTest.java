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
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.BaseGradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.ProjectImportAction;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.List;

import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.expect;

/**
 * Tests for {@link AndroidGradleProjectResolver}.
 */
public class AndroidGradleProjectResolverTest extends TestCase {
  private ContentRootSourcePaths myExpectedSourcePaths;
  private IdeaProjectStub myIdeaProject;
  private AndroidProjectStub myAndroidProject;
  private AndroidGradleProjectResolver myProjectResolver;
  private IdeaModuleStub myUtilModule;
  private IdeaModuleStub ideaModuleWithAndroidProject;
  private ProjectResolverContext resolverCtx;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myExpectedSourcePaths = new ContentRootSourcePaths();

    myIdeaProject = new IdeaProjectStub("multiProject");
    myAndroidProject = TestProjects.createBasicProject(myIdeaProject.getRootDir());

    ideaModuleWithAndroidProject = myIdeaProject.addModule(myAndroidProject.getName(), "androidTask");
    myUtilModule = myIdeaProject.addModule("util", "compileJava", "jar", "classes");
    myIdeaProject.addModule("notReallyAGradleProject");

    ProjectImportAction.AllModels allModels = new ProjectImportAction.AllModels(myIdeaProject);
    allModels.addExtraProject(myAndroidProject, AndroidProject.class, ideaModuleWithAndroidProject);

    resolverCtx = new ProjectResolverContext(
      ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myIdeaProject.getName()),
      myIdeaProject.getBuildFile().getParentFile().getPath(),
      null,
      createMock(ProjectConnection.class),
      new ExternalSystemTaskNotificationListenerAdapter() {},
      true
    );

    resolverCtx.setModels(allModels);

    myProjectResolver = new AndroidGradleProjectResolver(createMock(ProjectImportErrorHandler.class));
    myProjectResolver.setProjectResolverContext(resolverCtx);

    BaseGradleProjectResolverExtension baseGradleProjectResolverExtension = new BaseGradleProjectResolverExtension();
    baseGradleProjectResolverExtension.setProjectResolverContext(resolverCtx);
    myProjectResolver.setNext(baseGradleProjectResolverExtension);
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

    // Verify project.
    ProjectData projectData = myProjectResolver.createProject();
    assertNotNull(projectData);
    DataNode<ProjectData> projectDataNode = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);
    assertEquals(myIdeaProject.getName(), projectData.getName());
    assertEquals(FileUtil.toSystemIndependentName(myIdeaProject.getRootDir().getAbsolutePath()),
                 projectData.getIdeProjectFileDirectoryPath());

    // Verify 'basic' module.
    ModuleData androidModuleData = myProjectResolver.createModule(ideaModuleWithAndroidProject, projectData);
    assertEquals(ideaModuleWithAndroidProject.getName(), androidModuleData.getName());

    // Verify content root in 'basic' module.
    DataNode<ModuleData> moduleDataNode = projectDataNode.createChild(ProjectKeys.MODULE, androidModuleData);
    myProjectResolver.populateModuleContentRoots(ideaModuleWithAndroidProject, moduleDataNode);
    List<DataNode<ContentRootData>> contentRoots = Lists
      .newArrayList(ExternalSystemApiUtil.getChildren(moduleDataNode, ProjectKeys.CONTENT_ROOT));
    assertEquals(1, contentRoots.size());

    String projectRootDirPath = FileUtil.toSystemIndependentName(myAndroidProject.getRootDir().getAbsolutePath());
    ContentRootData contentRootData = contentRoots.get(0).getData();
    assertEquals(projectRootDirPath, contentRootData.getRootPath());
    myExpectedSourcePaths.storeExpectedSourcePaths(myAndroidProject);
    assertCorrectStoredDirPaths(contentRootData, ExternalSystemSourceType.SOURCE);
    assertCorrectStoredDirPaths(contentRootData, ExternalSystemSourceType.TEST);

    // Verify 'util' module.
    ModuleData utilModuleData = myProjectResolver.createModule(myUtilModule, projectData);
    assertEquals(myUtilModule.getName(), utilModuleData.getName());

    // Verify content root in 'util' module.
    DataNode<ModuleData> utilModuleDataNode = projectDataNode.createChild(ProjectKeys.MODULE, utilModuleData);
    myProjectResolver.populateModuleContentRoots(myUtilModule, utilModuleDataNode);

    contentRoots = Lists.newArrayList(ExternalSystemApiUtil.getChildren(utilModuleDataNode, ProjectKeys.CONTENT_ROOT));
    assertEquals(1, contentRoots.size());

    String moduleRootDirPath = FileUtil.toSystemIndependentName(myUtilModule.getRootDir().getPath());
    contentRootData = contentRoots.get(0).getData();
    assertEquals(moduleRootDirPath, contentRootData.getRootPath());
  }

  private void assertCorrectStoredDirPaths(@NotNull ContentRootData contentRootData, @NotNull ExternalSystemSourceType sourceType) {
    myExpectedSourcePaths.assertCorrectStoredDirPaths(contentRootData.getPaths(sourceType), sourceType);
  }
}
