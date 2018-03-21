/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker;

import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.BuildMode.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleBuildInvoker}.
 */
public class GradleBuildInvokerTest extends IdeaTestCase {
  @Mock private FileDocumentManager myFileDocumentManager;
  @Mock private GradleTasksExecutor myTasksExecutor;

  private IdeComponents myIdeComponents;
  private GradleTasksExecutorFactoryStub myTasksExecutorFactory;
  private Module[] myModules;
  private BuildSettings myBuildSettings;
  private GradleTaskFinder myTaskFinder;
  private GradleBuildInvoker myBuildInvoker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myTasksExecutorFactory = new GradleTasksExecutorFactoryStub(myTasksExecutor);
    myModules = new Module[]{getModule()};

    myIdeComponents = new IdeComponents(myProject);
    myTaskFinder = myIdeComponents.mockService(GradleTaskFinder.class);
    myBuildSettings = myIdeComponents.mockProjectService(BuildSettings.class);

    myBuildInvoker = new GradleBuildInvoker(myProject, myFileDocumentManager, myTasksExecutorFactory);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testCleanUp() {
    List<String> originalTasks = Arrays.asList("sourceGenTask1", "sourceGenTask2");
    File projectPath = getBaseDirPath(getProject());
    when(myTaskFinder.findTasksToExecute(projectPath, myModules, SOURCE_GEN, TestCompileType.NONE)).thenReturn(createTasksMap(originalTasks));

    myBuildInvoker.cleanProject();

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactly("clean", "sourceGenTask1", "sourceGenTask2");
    assertThat(request.getCommandLineArguments()).containsExactly("-Pandroid.injected.generateSourcesOnly=true");

    verifyInteractionWithMocks(CLEAN);
  }

  public void testCleanAndGenerateSources() {
    List<String> originalTasks = Arrays.asList("sourceGenTask1", "sourceGenTask2");
    File projectPath = getBaseDirPath(getProject());
    when(myTaskFinder.findTasksToExecute(projectPath, myModules, SOURCE_GEN, TestCompileType.NONE)).thenReturn(createTasksMap(originalTasks));

    myBuildInvoker.cleanAndGenerateSources();

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactly("clean", "sourceGenTask1", "sourceGenTask2");
    assertThat(request.getCommandLineArguments()).containsExactly("-Pandroid.injected.generateSourcesOnly=true");

    verifyInteractionWithMocks(SOURCE_GEN);
  }

  public void testGenerateSources() {
    List<String> tasks = Arrays.asList("sourceGenTask1", "sourceGenTask2");
    File projectPath = getBaseDirPath(getProject());
    when(myTaskFinder.findTasksToExecute(projectPath, myModules, SOURCE_GEN, TestCompileType.NONE)).thenReturn(createTasksMap(tasks));

    myBuildInvoker.generateSources();

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(tasks);
    assertThat(request.getCommandLineArguments()).containsExactly("-Pandroid.injected.generateSourcesOnly=true");

    verifyInteractionWithMocks(SOURCE_GEN);
  }

  public void testCompileJava() {
    List<String> tasks = Arrays.asList("compileJavaTask1", "compileJavaTask2");
    File projectPath = getBaseDirPath(getProject());
    when(myTaskFinder.findTasksToExecute(projectPath, myModules, COMPILE_JAVA, TestCompileType.ALL)).thenReturn(createTasksMap(tasks));

    myBuildInvoker.compileJava(myModules, TestCompileType.ALL);

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(tasks);
    assertThat(request.getCommandLineArguments()).isEmpty();

    verifyInteractionWithMocks(COMPILE_JAVA);
  }

  public void testAssemble() {
    List<String> tasks = Arrays.asList("assembleTask1", "assembleTask2");
    File projectPath = getBaseDirPath(getProject());
    when(myTaskFinder.findTasksToExecute(projectPath, myModules, ASSEMBLE, TestCompileType.ALL)).thenReturn(createTasksMap(tasks));

    myBuildInvoker.assemble(myModules, TestCompileType.ALL);

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(tasks);
    assertThat(request.getCommandLineArguments()).isEmpty();

    verifyInteractionWithMocks(ASSEMBLE);
  }

  public void testAssembleWithCommandLineArgs() {
    List<String> tasks = Arrays.asList("assembleTask1", "assembleTask2");
    File projectPath = getBaseDirPath(getProject());
    when(myTaskFinder.findTasksToExecute(projectPath, myModules, ASSEMBLE, TestCompileType.ALL)).thenReturn(createTasksMap(tasks));

    List<String> commandLineArgs = Arrays.asList("commandLineArg1", "commandLineArg2");

    myBuildInvoker.assemble(myModules, TestCompileType.ALL, commandLineArgs, null);

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(tasks);
    assertThat(request.getCommandLineArguments()).containsExactlyElementsIn(commandLineArgs);

    verifyInteractionWithMocks(ASSEMBLE);
  }

  private void verifyInteractionWithMocks(@NotNull BuildMode buildMode) {
    verify(myBuildSettings).setBuildMode(buildMode);
    verify(myFileDocumentManager).saveAllDocuments();
    verify(myTasksExecutor).queue();
  }

  @NotNull
  private static ListMultimap<Path, String> createTasksMap(@NotNull List<String> taskNames) {
    ListMultimap<Path, String> tasks = ArrayListMultimap.create();
    tasks.putAll(Paths.get("project_path"), taskNames);
    return tasks;
  }

  private static class GradleTasksExecutorFactoryStub extends GradleTasksExecutorFactory {
    @NotNull private final GradleTasksExecutor myTasksExecutor;
    private GradleBuildInvoker.Request myRequest;

    GradleTasksExecutorFactoryStub(@NotNull GradleTasksExecutor tasksExecutor) {
      myTasksExecutor = tasksExecutor;
    }

    @Override
    @NotNull
    public GradleTasksExecutor create(@NotNull GradleBuildInvoker.Request request, @NotNull BuildStopper buildStopper) {
      myRequest = request;
      return myTasksExecutor;
    }

    GradleBuildInvoker.Request getRequest() {
      return myRequest;
    }
  }
}