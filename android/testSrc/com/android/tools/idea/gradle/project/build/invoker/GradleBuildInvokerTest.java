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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.BuildMode.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleBuildInvoker}.
 */
public class GradleBuildInvokerTest extends IdeaTestCase {
  @Mock private FileDocumentManager myFileDocumentManager;
  @Mock private GradleTasksExecutor myTasksExecutor;
  @Mock private NativeDebugSessionFinder myDebugSessionFinder;

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

    myBuildInvoker = new GradleBuildInvoker(myProject, myFileDocumentManager, myTasksExecutorFactory, myDebugSessionFinder);
  }

  @Override
  protected void tearDown() throws Exception {
    Messages.setTestDialog(TestDialog.DEFAULT);
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testCleanUp() {
    List<String> tasks = setUpTasksForSourceGeneration();

    myBuildInvoker.cleanProject();

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    List<String> expectedTasks = new ArrayList<>(tasks);
    expectedTasks.add(0, "clean");
    assertThat(request.getGradleTasks()).containsExactly(expectedTasks.toArray());
    assertThat(request.getCommandLineArguments()).containsExactly("-Pandroid.injected.generateSourcesOnly=true");

    verifyInteractionWithMocks(CLEAN);
  }

  public void testCleanupWithNativeDebugSessionAndUserTerminatesSession() {
    setUpTasksForSourceGeneration();

    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    when(myDebugSessionFinder.findNativeDebugSession()).thenReturn(nativeDebugSession);

    Messages.setTestDialog(TestDialog.OK);

    myBuildInvoker.cleanProject();

    verify(nativeDebugSession).stop(); // expect that the session was stopped.
  }

  public void testCleanupWithNativeDebugSessionAndUserDoesNotTerminateSession() {
    setUpTasksForSourceGeneration();

    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    when(myDebugSessionFinder.findNativeDebugSession()).thenReturn(nativeDebugSession);

    Messages.setTestDialog(TestDialog.NO);

    myBuildInvoker.cleanProject();

    verify(nativeDebugSession, never()).stop(); // expect that the session was never stopped.
  }

  public void testCleanupWithNativeDebugSessionAndUserCancelsBuild() {
    setUpTasksForSourceGeneration();

    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    when(myDebugSessionFinder.findNativeDebugSession()).thenReturn(nativeDebugSession);

    Messages.setTestDialog(new TestDialog() {
      @Override
      public int show(String message) {
        return Messages.CANCEL;
      }
    });

    myBuildInvoker.cleanProject();

    verify(nativeDebugSession, never()).stop(); // expect that the session was never stopped.

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertNull(request); // Build was canceled, no request created.

    // If build was canceled, none of these methods should have been invoked.
    verify(myBuildSettings, never()).setBuildMode(any());
    verify(myFileDocumentManager, never()).saveAllDocuments();
    verify(myTasksExecutor, never()).queue();

  }

  public void testCleanAndGenerateSources() {
    List<String> tasks = setUpTasksForSourceGeneration();

    myBuildInvoker.cleanAndGenerateSources();

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();

    List<String> expectedTasks = new ArrayList<>(tasks);
    expectedTasks.add(0, "clean");
    assertThat(request.getGradleTasks()).containsExactly(expectedTasks.toArray());
    assertThat(request.getCommandLineArguments()).containsExactly("-Pandroid.injected.generateSourcesOnly=true");

    verifyInteractionWithMocks(SOURCE_GEN);
  }

  public void testGenerateSources() {
    List<String> tasks = setUpTasksForSourceGeneration();

    myBuildInvoker.generateSources();

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(tasks);
    assertThat(request.getCommandLineArguments()).containsExactly("-Pandroid.injected.generateSourcesOnly=true");

    verifyInteractionWithMocks(SOURCE_GEN);
  }

  @NotNull
  private List<String> setUpTasksForSourceGeneration() {
    List<String> tasks = Arrays.asList("sourceGenTask1", "sourceGenTask2");
    File projectPath = getBaseDirPath(getProject());
    when(myTaskFinder.findTasksToExecute(projectPath, myModules, SOURCE_GEN, TestCompileType.NONE)).thenReturn(createTasksMap(tasks));
    return tasks;
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