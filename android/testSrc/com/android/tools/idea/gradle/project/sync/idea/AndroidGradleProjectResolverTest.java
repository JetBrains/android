/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea;

import static com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver.BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.PROJECT;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.IdeaJavaModuleModelFactory;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.LegacyIdeaProjectModelAdapter;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.mockito.Mock;

/**
 * Tests for {@link AndroidGradleProjectResolver}.
 */
public class AndroidGradleProjectResolverTest extends LightPlatformTestCase {
  @Mock private CommandLineArgs myCommandLineArgs;
  @Mock private ProjectFinder myProjectFinder;
  @Mock private NativeAndroidProject myNativeAndroidProject;

  private IdeaProjectStub myProjectModel;
  private IdeaModuleStub myAndroidModuleModel;
  private IdeaModuleStub myNativeAndroidModuleModel;
  private IdeaModuleStub myJavaModuleModel;

  private AndroidProjectStub myAndroidProjectStub;

  private ProjectResolverContext myResolverCtx;
  private AndroidGradleProjectResolver myProjectResolver;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    IdeaJavaModuleModelFactory myIdeaJavaModuleModelFactory = new IdeaJavaModuleModelFactory();
    myProjectModel = new IdeaProjectStub("multiProject");
    myAndroidProjectStub = TestProjects.createBasicProject(myProjectModel.getRootDir());

    when(myNativeAndroidProject.getName()).thenReturn("app");

    myAndroidModuleModel = myProjectModel.addModule(myAndroidProjectStub.getName(), "androidTask");
    myNativeAndroidModuleModel = myProjectModel.addModule(myNativeAndroidProject.getName(), "nativeAndroidTask");
    myJavaModuleModel = myProjectModel.addModule("util", "compileJava", "jar", "classes");
    myProjectModel.addModule("notReallyAGradleProject");

    ProjectImportAction.AllModels allModels = new ProjectImportAction.AllModels(myProjectModel);
    allModels.addModel(myAndroidProjectStub, AndroidProject.class, myAndroidModuleModel);
    allModels.addModel(myNativeAndroidProject, NativeAndroidProject.class, myNativeAndroidModuleModel);

    ExternalSystemTaskId id = ExternalSystemTaskId.create(SYSTEM_ID, RESOLVE_PROJECT, myProjectModel.getName());
    String projectPath = toSystemDependentName(myProjectModel.getBuildFile().getParent());
    ExternalSystemTaskNotificationListener notificationListener = new ExternalSystemTaskNotificationListenerAdapter() {
    };
    myResolverCtx = new DefaultProjectResolverContext(id, projectPath, null, mock(ProjectConnection.class), notificationListener, null, true) {
      @Override
      public boolean isResolveModulePerSourceSet() {
        return false;
      }
    };
    myResolverCtx.setModels(allModels);

    myProjectResolver = new AndroidGradleProjectResolver(myCommandLineArgs, myProjectFinder, myIdeaJavaModuleModelFactory);
    myProjectResolver.setProjectResolverContext(myResolverCtx);

    GradleProjectResolverExtension next = new CommonGradleProjectResolverExtension();
    next.setProjectResolverContext(myResolverCtx);
    myProjectResolver.setNext(next);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myProjectModel != null) {
        myProjectModel.dispose();
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testCreateModuleWithOldModelVersion() {
    AndroidProject androidProject = mock(AndroidProject.class);
    ProjectImportAction.AllModels allModels = new ProjectImportAction.AllModels(myProjectModel);
    allModels.addModel(androidProject, AndroidProject.class, myAndroidModuleModel);
    myResolverCtx.setModels(allModels);

    when(androidProject.getModelVersion()).thenReturn("0.0.1");
    DataNode<ProjectData> projectDataNode = createProjectNode();

    try {
      myProjectResolver.createModule(myAndroidModuleModel, projectDataNode);
      fail("Expecting IllegalStateException to be thrown");
    }
    catch (IllegalStateException e) {
      assertThat(e.getMessage()).startsWith("The project is using an unsupported version of the Android Gradle plug-in (0.0.1)");
    }
  }

  public void testPopulateModuleContentRootsWithJavaProject() {
    DataNode<ProjectData> projectNode = createProjectNode();
    DataNode<ModuleData> moduleDataNode = myProjectResolver.createModule(myJavaModuleModel, projectNode);

    myProjectResolver.populateModuleContentRoots(myJavaModuleModel, moduleDataNode);

    // Verify module does not have AndroidGradleModel.
    Collection<DataNode<AndroidModuleModel>> androidModelNodes = getChildren(moduleDataNode, ANDROID_MODEL);
    assertThat(androidModelNodes).isEmpty();

    // Verify module has IdeaGradleProject.
    Collection<DataNode<GradleModuleModel>> gradleModelNodes = getChildren(moduleDataNode, GRADLE_MODULE_MODEL);
    assertThat(gradleModelNodes).hasSize(1);

    DataNode<GradleModuleModel> gradleModelNode = getFirstItem(gradleModelNodes);
    assertNotNull(gradleModelNode);
    assertEquals(myJavaModuleModel.getGradleProject().getPath(), gradleModelNode.getData().getGradlePath());
  }

  public void testGetExtraCommandLineArgs() {
    Project project = getProject();
    when(myProjectFinder.findProject(myResolverCtx)).thenReturn(project);

    List<String> commandLineArgs = Arrays.asList("arg1", "arg2");
    when(myCommandLineArgs.get(project)).thenReturn(commandLineArgs);

    List<String> actual = myProjectResolver.getExtraCommandLineArgs();
    assertSame(commandLineArgs, actual);

    verify(myProjectFinder).findProject(myResolverCtx);
    verify(myCommandLineArgs).get(project);
  }

  public void testPopulateModuleTasks() {
    DataNode<ProjectData> projectNode = createProjectNode();
    DataNode<ModuleData> moduleDataNode = myProjectResolver.createModule(myJavaModuleModel, projectNode);

    IdeaProjectStub includedProject = new IdeaProjectStub("includedProject");
    IdeaModuleStub includedModule = includedProject.addModule("lib", "clean", "jar");
    myResolverCtx.getModels().getIncludedBuilds().add(new LegacyIdeaProjectModelAdapter(includedProject));

    // Verify that task data for non-included module.
    Collection<TaskData> taskData = myProjectResolver.populateModuleTasks(includedModule, moduleDataNode, projectNode);
    assertThat(ContainerUtil.map(taskData, TaskData::getName)).containsExactly("clean", "jar");

    // Verify that task data for non-included module.
    taskData = myProjectResolver.populateModuleTasks(myJavaModuleModel, moduleDataNode, projectNode);
    Collection<String> taskDataNames = ContainerUtil.map(taskData, TaskData::getName);
    assertThat(taskDataNames).containsExactly("compileJava", "jar", "classes");
  }

  public void testCorrectGroupName() {
    // Ensure AbstractModuleDataService is initialised.
    new AbstractModuleDataService<ModuleData>() {
      @NotNull
      @Override
      public Key<ModuleData> getTargetDataKey() {
        return null;
      }
    };
    assertThat(NotificationGroup.findRegisteredGroup(BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME)).isNotNull();
  }

  @NotNull
  private DataNode<ProjectData> createProjectNode() {
    final String projectDirPath = myResolverCtx.getProjectPath();
    String projectName = myProjectModel.getName();
    ProjectData project = new ProjectData(SYSTEM_ID, projectName, projectDirPath, projectDirPath);
    return new DataNode<>(PROJECT, project, null);
  }
}
