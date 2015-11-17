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
import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.NativeAndroidProjectStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.android.newProject.AndroidModuleBuilder;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.service.project.BaseGradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;

import java.util.Collection;

import static com.android.tools.idea.gradle.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.AndroidProjectKeys.GRADLE_MODEL;
import static com.android.tools.idea.gradle.AndroidProjectKeys.NATIVE_ANDROID_MODEL;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.*;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

/**
 * Tests for {@link AndroidGradleProjectResolver}.
 */
public class AndroidGradleProjectResolverIdeaTest extends IdeaTestCase {
  private IdeaProjectStub myIdeaProject;
  private AndroidProjectStub myAndroidProject;
  private NativeAndroidProjectStub myNativeAndroidProject;

  private ProjectResolverContext myResolverCtx;
  private AndroidGradleProjectResolver myProjectResolver;

  private IdeaModuleStub myAndroidModule;
  private IdeaModuleStub myNativeAndroidModule;
  private IdeaModuleStub myJavaUtilModule;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIdeaProject = new IdeaProjectStub("multiProject");
    myAndroidProject = TestProjects.createBasicProject(myIdeaProject.getRootDir());
    myNativeAndroidProject = TestProjects.createNativeProject(myIdeaProject.getRootDir());

    myAndroidModule = myIdeaProject.addModule(myAndroidProject.getName(), "androidTask");
    myNativeAndroidModule = myIdeaProject.addModule(myNativeAndroidProject.getName(), "nativeAndroidTask");
    myJavaUtilModule = myIdeaProject.addModule("util", "compileJava", "jar", "classes");
    myIdeaProject.addModule("notReallyAGradleProject");

    ProjectImportAction.AllModels allModels = new ProjectImportAction.AllModels(myIdeaProject);
    allModels.addExtraProject(myAndroidProject, AndroidProject.class, myAndroidModule);
    allModels.addExtraProject(myNativeAndroidProject, NativeAndroidProject.class, myNativeAndroidModule);

    ExternalSystemTaskId id = ExternalSystemTaskId.create(SYSTEM_ID, RESOLVE_PROJECT, myIdeaProject.getName());
    String projectPath = FileUtil.toSystemDependentName(myIdeaProject.getBuildFile().getParent());
    ExternalSystemTaskNotificationListener notificationListener = new ExternalSystemTaskNotificationListenerAdapter() {
    };
    myResolverCtx = new ProjectResolverContext(id, projectPath, null, createMock(ProjectConnection.class), notificationListener, true);
    myResolverCtx.setModels(allModels);

    myProjectResolver = new AndroidGradleProjectResolver(createMock(ProjectImportErrorHandler.class));
    myProjectResolver.setProjectResolverContext(myResolverCtx);

    GradleProjectResolverExtension next = new BaseGradleProjectResolverExtension();
    next.setProjectResolverContext(myResolverCtx);
    myProjectResolver.setNext(next);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myIdeaProject != null) {
        myIdeaProject.dispose();
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testCreateModuleWithOldModelVersion() {
    AndroidProject androidProject = createMock(AndroidProject.class);
    ProjectImportAction.AllModels allModels = new ProjectImportAction.AllModels(myIdeaProject);
    allModels.addExtraProject(androidProject, AndroidProject.class, myAndroidModule);
    myResolverCtx.setModels(allModels);

    expect(androidProject.getModelVersion()).andStubReturn("0.0.1");
    replay(androidProject);

    try {
      ProjectData project = myProjectResolver.createProject();
      myProjectResolver.createModule(myAndroidModule, project);
      fail();
    }
    catch (IllegalStateException e) {
    }

    verify(androidProject);
  }

  public void testPopulateModuleContentRootsWithAndroidProject() {
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<ProjectData>(ProjectKeys.PROJECT, project, null);
    ModuleData module = myProjectResolver.createModule(myAndroidModule, project);
    DataNode<ModuleData> moduleDataNode = projectNode.createChild(ProjectKeys.MODULE, module);

    myProjectResolver.populateModuleContentRoots(myAndroidModule, moduleDataNode);

    // Verify module has AndroidGradleModel.
    Collection<DataNode<AndroidGradleModel>> androidProjectNodes = getChildren(moduleDataNode, ANDROID_MODEL);
    assertEquals(1, androidProjectNodes.size());
    DataNode<AndroidGradleModel> androidProjectNode = getFirstItem(androidProjectNodes);
    assertNotNull(androidProjectNode);
    assertSame(myAndroidProject, androidProjectNode.getData().getAndroidProject());

    // Verify module has IdeaGradleProject.
    Collection<DataNode<GradleModel>> gradleProjects = getChildren(moduleDataNode, GRADLE_MODEL);
    assertEquals(1, gradleProjects.size());
    DataNode<GradleModel> gradleProjectNode = getFirstItem(gradleProjects);
    assertNotNull(gradleProjectNode);
    assertEquals(myAndroidModule.getGradleProject().getPath(), gradleProjectNode.getData().getGradlePath());
  }

  public void testPopulateModuleContentRootsWithNativeAndroidProject() {
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<ProjectData>(ProjectKeys.PROJECT, project, null);
    ModuleData module = myProjectResolver.createModule(myNativeAndroidModule, project);
    DataNode<ModuleData> moduleDataNode = projectNode.createChild(ProjectKeys.MODULE, module);

    myProjectResolver.populateModuleContentRoots(myNativeAndroidModule, moduleDataNode);

    // Verify module does not have AndroidGradleModel.
    Collection<DataNode<AndroidGradleModel>> androidProjectNodes = getChildren(moduleDataNode, ANDROID_MODEL);
    assertEquals(0, androidProjectNodes.size());

    // Verify module has NativeAndroidGradleModel.
    Collection<DataNode<NativeAndroidGradleModel>> nativeAndroidProjectNodes = getChildren(moduleDataNode, NATIVE_ANDROID_MODEL);
    assertEquals(1, nativeAndroidProjectNodes.size());
    DataNode<NativeAndroidGradleModel> nativeAndroidProjectNode = getFirstItem(nativeAndroidProjectNodes);
    assertNotNull(nativeAndroidProjectNode);
    assertSame(myNativeAndroidProject, nativeAndroidProjectNode.getData().getNativeAndroidProject());

    // Verify module has IdeaGradleProject.
    Collection<DataNode<GradleModel>> gradleProjects = getChildren(moduleDataNode, GRADLE_MODEL);
    assertEquals(1, gradleProjects.size());
    DataNode<GradleModel> gradleProjectNode = getFirstItem(gradleProjects);
    assertNotNull(gradleProjectNode);
    assertEquals(myNativeAndroidModule.getGradleProject().getPath(), gradleProjectNode.getData().getGradlePath());
  }

  public void testPopulateModuleContentRootsWithJavaProject() {
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<ProjectData>(ProjectKeys.PROJECT, project, null);
    ModuleData module = myProjectResolver.createModule(myJavaUtilModule, project);
    DataNode<ModuleData> moduleDataNode = projectNode.createChild(ProjectKeys.MODULE, module);

    myProjectResolver.populateModuleContentRoots(myJavaUtilModule, moduleDataNode);

    // Verify module does not have AndroidGradleModel.
    Collection<DataNode<AndroidGradleModel>> androidProjectNodes = getChildren(moduleDataNode, ANDROID_MODEL);
    assertEquals(0, androidProjectNodes.size());

    // Verify module has IdeaGradleProject.
    Collection<DataNode<GradleModel>> gradleProjects = getChildren(moduleDataNode, GRADLE_MODEL);
    assertEquals(1, gradleProjects.size());
    DataNode<GradleModel> gradleProjectNode = getFirstItem(gradleProjects);
    assertNotNull(gradleProjectNode);
    assertEquals(myJavaUtilModule.getGradleProject().getPath(), gradleProjectNode.getData().getGradlePath());
  }
}
