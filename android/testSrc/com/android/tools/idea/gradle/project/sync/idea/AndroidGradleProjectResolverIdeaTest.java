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

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.PROJECT;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getChildren;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.stream.Collectors.toList;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.IdeNativeAndroidProject;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.IdeaJavaModuleModelFactory;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.kapt.idea.KaptGradleModel;
import org.jetbrains.kotlin.kapt.idea.KaptSourceSetModel;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.service.project.BaseGradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.mockito.Mock;

/**
 * Tests for {@link AndroidGradleProjectResolver}.
 */
public class AndroidGradleProjectResolverIdeaTest extends PlatformTestCase {
  @Mock private CommandLineArgs myCommandLineArgs;
  @Mock private ProjectImportErrorHandler myErrorHandler;
  @Mock private ProjectFinder myProjectFinder;
  @Mock private VariantSelector myVariantSelector;
  @Mock private IdeNativeAndroidProject.Factory myNativeAndroidProjectFactory;
  @Mock private NativeAndroidProject myNativeAndroidProject;
  @Mock private IdeNativeAndroidProject myIdeNativeAndroidProject;

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
    when(myNativeAndroidProjectFactory.create(myNativeAndroidProject)).thenReturn(myIdeNativeAndroidProject);

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
    myResolverCtx = new DefaultProjectResolverContext(id, projectPath, null, mock(ProjectConnection.class), notificationListener, true);
    myResolverCtx.setModels(allModels);

    myProjectResolver = new AndroidGradleProjectResolver(myCommandLineArgs, myErrorHandler, myProjectFinder, myVariantSelector,
                                                         myNativeAndroidProjectFactory, myIdeaJavaModuleModelFactory,
                                                         new IdeDependenciesFactory());
    myProjectResolver.setProjectResolverContext(myResolverCtx);

    GradleProjectResolverExtension next = new BaseGradleProjectResolverExtension();
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
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectDataNode = new DataNode<>(PROJECT, project, null);

    try {
      myProjectResolver.createModule(myAndroidModuleModel, projectDataNode);
      fail("Expecting IllegalStateException to be thrown");
    }
    catch (IllegalStateException e) {
      assertThat(e.getMessage()).startsWith("The project is using an unsupported version of the Android Gradle plug-in (0.0.1)");
    }
  }

  public void testSyncIssuesPropagatedOnJavaModules() {
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<>(PROJECT, project, null);
    DataNode<ModuleData> moduleDataNode = myProjectResolver.createModule(myAndroidModuleModel, projectNode);

    SyncIssue syncIssue = mock(SyncIssue.class);
    myAndroidProjectStub.setSyncIssues(syncIssue);

    ProjectImportAction.AllModels allModels = new ProjectImportAction.AllModels(myProjectModel);
    allModels.addModel(myAndroidProjectStub, AndroidProject.class, myAndroidModuleModel);
    myResolverCtx.setModels(allModels);

    myProjectResolver.populateModuleContentRoots(myAndroidModuleModel, moduleDataNode);

    Collection<DataNode<AndroidModuleModel>> androidModelNodes = getChildren(moduleDataNode, ANDROID_MODEL);
    assertThat(androidModelNodes).isEmpty();

    Collection<DataNode<JavaModuleModel>> javaModelNodes = getChildren(moduleDataNode, JAVA_MODULE_MODEL);
    assertSize(1, javaModelNodes);
    JavaModuleModel javaModuleModel = javaModelNodes.iterator().next().getData();
    SyncIssue issue = javaModuleModel.getSyncIssues().iterator().next();
    assertThat(issue.getMessage()).isEqualTo(syncIssue.getMessage());
    assertThat(issue.getData()).isEqualTo(syncIssue.getData());
    assertThat(issue.getSeverity()).isEqualTo(syncIssue.getSeverity());
    assertThat(issue.getType()).isEqualTo(syncIssue.getType());
    assertThat(issue.getMultiLineMessage()).isEqualTo(syncIssue.getMultiLineMessage());
  }

  public void testPopulateModuleContentRootsWithNativeAndroidProject() {
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<>(PROJECT, project, null);
    DataNode<ModuleData> moduleDataNode = myProjectResolver.createModule(myNativeAndroidModuleModel, projectNode);
    myProjectResolver.populateModuleContentRoots(myNativeAndroidModuleModel, moduleDataNode);

    // Verify module does not have AndroidGradleModel.
    Collection<DataNode<AndroidModuleModel>> androidModelNodes = getChildren(moduleDataNode, ANDROID_MODEL);
    assertThat(androidModelNodes).isEmpty();

    // Verify module has NativeAndroidGradleModel.
    Collection<DataNode<NdkModuleModel>> ndkModuleModelNodes = getChildren(moduleDataNode, NDK_MODEL);
    assertThat(ndkModuleModelNodes).hasSize(1);

    DataNode<NdkModuleModel> nativeAndroidModelNode = getFirstItem(ndkModuleModelNodes);
    assertNotNull(nativeAndroidModelNode);
    assertSame(myIdeNativeAndroidProject, nativeAndroidModelNode.getData().getAndroidProject());

    // Verify module has IdeaGradleProject.
    Collection<DataNode<GradleModuleModel>> gradleModelNodes = getChildren(moduleDataNode, GRADLE_MODULE_MODEL);
    assertThat(gradleModelNodes).hasSize(1);

    DataNode<GradleModuleModel> gradleModelNode = getFirstItem(gradleModelNodes);
    assertNotNull(gradleModelNode);
    assertEquals(myNativeAndroidModuleModel.getGradleProject().getPath(), gradleModelNode.getData().getGradlePath());
  }

  public void testPopulateModuleContentRootsWithJavaProject() {
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<>(PROJECT, project, null);
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
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<>(PROJECT, project, null);
    DataNode<ModuleData> moduleDataNode = myProjectResolver.createModule(myJavaModuleModel, projectNode);

    IdeaProjectStub includedProject = new IdeaProjectStub("includedProject");
    IdeaModuleStub includedModule = includedProject.addModule("lib", "clean", "jar");
    myResolverCtx.getModels().getIncludedBuilds().add(includedProject);

    // Verify that task data for non-included module.
    Collection<TaskData> taskData = myProjectResolver.populateModuleTasks(includedModule, moduleDataNode, projectNode);
    assertThat(taskData.stream().map(TaskData::getName).collect(toList())).containsExactly("clean", "jar");

    // Verify that task data for non-included module.
    taskData = myProjectResolver.populateModuleTasks(myJavaModuleModel, moduleDataNode, projectNode);
    Collection<String> taskDataNames = taskData.stream().map(TaskData::getName).collect(toList());
    assertThat(taskDataNames).containsExactly("compileJava", "jar", "classes");
  }

  public void testKaptSourcesAreAddedToAndroidModuleModel() {
    ProjectData project = myProjectResolver.createProject();
    DataNode<ProjectData> projectNode = new DataNode<>(PROJECT, project, null);
    DataNode<ModuleData> moduleDataNode = myProjectResolver.createModule(myAndroidModuleModel, projectNode);

    File debugGeneratedSourceFile = new File("/gen/debug");
    File releaseGeneratedSourceFile = new File("/gen/release");

    KaptGradleModel mockKaptModel = new KaptGradleModel() {
      @Override
      public boolean isEnabled() {
        return true;
      }

      @NotNull
      @Override
      public File getBuildDirectory() {
        return null;
      }

      @NotNull
      @Override
      public List<KaptSourceSetModel> getSourceSets() {
        KaptSourceSetModel debugSetModel = mock(KaptSourceSetModel.class);
        when(debugSetModel.getGeneratedKotlinSourcesDirFile()).thenReturn(debugGeneratedSourceFile);
        when(debugSetModel.getSourceSetName()).thenReturn("debug");
        KaptSourceSetModel releaseSetModel = mock(KaptSourceSetModel.class);
        when(releaseSetModel.getGeneratedKotlinSourcesDirFile()).thenReturn(releaseGeneratedSourceFile);
        when(releaseSetModel.getSourceSetName()).thenReturn("debug");
        return ImmutableList.of(debugSetModel, releaseSetModel);
      }
    };

    when(myVariantSelector.findVariantToSelect(any())).thenReturn(myAndroidProjectStub.getFirstVariant());

    ProjectImportAction.AllModels allModels = new ProjectImportAction.AllModels(myProjectModel);
    allModels.addModel(myAndroidProjectStub, AndroidProject.class, myAndroidModuleModel);
    allModels.addModel(mockKaptModel, KaptGradleModel.class, myAndroidModuleModel);
    myResolverCtx.setModels(allModels);

    myProjectResolver.populateModuleContentRoots(myAndroidModuleModel, moduleDataNode);

    Collection<DataNode<AndroidModuleModel>> androidModelNodes = getChildren(moduleDataNode, ANDROID_MODEL);
    assertThat(androidModelNodes).hasSize(1);
    AndroidModuleModel androidModuleModel = androidModelNodes.iterator().next().getData();
    Variant variant = androidModuleModel.findVariantByName("debug");
    assertThat(variant.getMainArtifact().getGeneratedSourceFolders()).contains(debugGeneratedSourceFile);
  }
}
