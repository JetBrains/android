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

import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE;
import static com.android.tools.idea.gradle.util.BuildMode.CLEAN;
import static com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.JavaModuleModelBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.XDebugSession;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link GradleBuildInvoker}.
 */
public class GradleBuildInvokerTest extends HeavyPlatformTestCase {
  @Mock private FileDocumentManager myFileDocumentManager;
  @Mock private GradleTasksExecutor myTasksExecutor;
  @Mock private NativeDebugSessionFinder myDebugSessionFinder;

  private GradleTasksExecutorFactoryStub myTasksExecutorFactory;
  private Module[] myModules;
  private BuildSettings myBuildSettings;
  private GradleTaskFinder myTaskFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
  }

  private GradleBuildInvoker createBuildInvoker() {
    myTasksExecutorFactory = new GradleTasksExecutorFactoryStub(myTasksExecutor);
    myModules = new Module[]{getModule()};

    IdeComponents ideComponents = new IdeComponents(myProject);
    myTaskFinder = ideComponents.mockApplicationService(GradleTaskFinder.class);
    myBuildSettings = ideComponents.mockProjectService(BuildSettings.class);

    return new GradleBuildInvokerImpl(myProject, myFileDocumentManager, myTasksExecutorFactory, myDebugSessionFinder);
  }

  private GradleBuildInvoker createBuildInvokerForConfiguredProject() {
    myTasksExecutorFactory = new GradleTasksExecutorFactoryStub(myTasksExecutor);
    myModules = new Module[]{getModule()};

    IdeComponents ideComponents = new IdeComponents(myProject);
    myBuildSettings = ideComponents.mockProjectService(BuildSettings.class);

    return new GradleBuildInvokerImpl(myProject, myFileDocumentManager, myTasksExecutorFactory, myDebugSessionFinder);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
      myBuildSettings = null;
    }
  }

  public void testCleanUp() {
    GradleBuildInvoker buildInvoker = createBuildInvoker();
    // Invoke method to test.
    buildInvoker.cleanProject();
    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    // Verify task list includes clean.
    assertThat(request.getGradleTasks()).containsExactly("clean");
    assertThat(request.getCommandLineArguments()).isEmpty();
    verifyInteractionWithMocks(CLEAN);
  }

  public void testCleanupWithNativeDebugSessionAndUserTerminatesSession() {
    GradleBuildInvoker buildInvoker = createBuildInvoker();
    setUpTasksForSourceGeneration();

    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    when(myDebugSessionFinder.findNativeDebugSession()).thenReturn(nativeDebugSession);

    TestDialogManager.setTestDialog(TestDialog.OK);

    buildInvoker.cleanProject();

    verify(nativeDebugSession).stop(); // expect that the session was stopped.
  }

  public void testCleanupWithNativeDebugSessionAndUserDoesNotTerminateSession() {
    GradleBuildInvoker buildInvoker = createBuildInvoker();
    setUpTasksForSourceGeneration();

    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    when(myDebugSessionFinder.findNativeDebugSession()).thenReturn(nativeDebugSession);

    TestDialogManager.setTestDialog(TestDialog.NO);

    buildInvoker.cleanProject();

    verify(nativeDebugSession, never()).stop(); // expect that the session was never stopped.
  }

  public void testCleanupWithNativeDebugSessionAndUserCancelsBuild() {
    GradleBuildInvoker buildInvoker = createBuildInvoker();
    setUpTasksForSourceGeneration();

    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    when(myDebugSessionFinder.findNativeDebugSession()).thenReturn(nativeDebugSession);

    TestDialogManager.setTestDialog(new TestDialog() {
      @Override
      public int show(@NotNull String message) {
        return Messages.CANCEL;
      }
    });

    buildInvoker.cleanProject();

    verify(nativeDebugSession, never()).stop(); // expect that the session was never stopped.

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertNull(request); // Build was canceled, no request created.

    // If build was canceled, none of these methods should have been invoked.
    verify(myBuildSettings, never()).setBuildMode(any());
    verify(myFileDocumentManager, never()).saveAllDocuments();
    verify(myTasksExecutor, never()).queue();
  }

  @NotNull
  private List<String> setUpTasksForSourceGeneration() {
    List<String> tasks = Arrays.asList("sourceGenTask1", "sourceGenTask2");
    when(myTaskFinder.findTasksToExecute(myModules, SOURCE_GEN, TestCompileType.NONE)).thenReturn(createTasksMap(tasks));
    return tasks;
  }

  public void testCompileJava() {
    setupTestProjectFromAndroidModel(myProject,
                                     getTempDir().createDir().toFile(),
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));

    GradleBuildInvoker buildInvoker = createBuildInvokerForConfiguredProject();
    buildInvoker.compileJava(
      ImmutableList.of(
        gradleModule(myProject, ":app"),
        gradleModule(myProject, ":lib")
      ).toArray(new Module[0]),
      TestCompileType.ALL
    );

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(ImmutableList.of(
      ":lib:ideSetupTask1",
      ":lib:ideSetupTask2",
      ":lib:ideUnitTestSetupTask1",
      ":lib:ideUnitTestSetupTask2",
      ":lib:ideAndroidTestSetupTask1",
      ":lib:ideAndroidTestSetupTask2",
      ":lib:compileDebugUnitTestSources",
      ":lib:compileDebugAndroidTestSources",
      ":lib:compileDebugSources",
      ":app:ideSetupTask1",
      ":app:ideSetupTask2",
      ":app:ideUnitTestSetupTask1",
      ":app:ideUnitTestSetupTask2",
      ":app:ideAndroidTestSetupTask1",
      ":app:ideAndroidTestSetupTask2",
      ":app:compileDebugUnitTestSources",
      ":app:compileDebugAndroidTestSources",
      ":app:compileDebugSources"
    ));

    verifyInteractionWithMocks(COMPILE_JAVA);
  }

  public void testAssemble() {
    setupTestProjectFromAndroidModel(myProject,
                                     getTempDir().createDir().toFile(),
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));

    GradleBuildInvoker buildInvoker = createBuildInvokerForConfiguredProject();
    buildInvoker.assemble(
      ImmutableList.of(
        gradleModule(myProject, ":app"),
        gradleModule(myProject, ":lib")
      ).toArray(new Module[0]),
      TestCompileType.ALL,
      null);

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(ImmutableList.of(":lib:assembleDebug", ":app:assembleDebug"));
    assertThat(request.getCommandLineArguments()).isEmpty();

    verifyInteractionWithMocks(ASSEMBLE);
  }

  public void testAssembleWithCommandLineArgs() {
    setupTestProjectFromAndroidModel(myProject,
                                     getTempDir().createDir().toFile(),
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));

    GradleBuildInvoker buildInvoker = createBuildInvokerForConfiguredProject();
    buildInvoker.assemble(
      ImmutableList.of(
        gradleModule(myProject, ":app"),
        gradleModule(myProject, ":lib")
      ).toArray(new Module[0]),
      TestCompileType.ALL,
      null);

    GradleBuildInvoker.Request request = myTasksExecutorFactory.getRequest();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(ImmutableList.of(":lib:assembleDebug", ":app:assembleDebug"));

    verifyInteractionWithMocks(ASSEMBLE);
  }

  public void testExecuteTasksWaitForCompletionNoDispatch() throws InterruptedException {
    File projectPath = getTempDir().createDir().toFile();
    setupTestProjectFromAndroidModel(myProject,
                                     projectPath,
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));

    GradleBuildInvoker buildInvoker = createBuildInvokerForConfiguredProject();

    GradleBuildInvoker.Request request = GradleBuildInvoker.Request.builder(
      myProject,
      new File(projectPath, "build.gradle"),
      ":app:assembleDebug",
      ":lib:assembleDebug"
    )
      .waitForCompletion()
      .build();

    Semaphore sema = new Semaphore(0);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ListenableFuture<GradleInvocationResult> futureResult = buildInvoker.executeTasks(request);
      assertThat(futureResult.isDone()).isTrue();
      sema.release();
    });
    // Block until execute tasks has been run.
    do {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }
    while (!sema.tryAcquire());

    verify(myFileDocumentManager).saveAllDocuments();
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

  static class GradleTasksExecutorFactoryStub extends GradleTasksExecutorFactory {
    @NotNull private final GradleTasksExecutor myTasksExecutor;
    private GradleBuildInvoker.Request myRequest;

    GradleTasksExecutorFactoryStub(@NotNull GradleTasksExecutor tasksExecutor) {
      myTasksExecutor = tasksExecutor;
    }

    @Override
    @NotNull
    public GradleTasksExecutor create(@NotNull GradleBuildInvoker.Request request,
                                      @NotNull BuildStopper buildStopper,
                                      @NotNull ExternalSystemTaskNotificationListener listener,
                                      @NotNull SettableFuture<GradleInvocationResult> resultFuture) {
      myRequest = request;
      doAnswer(invocation -> {
        resultFuture.set(null);
        return null;
      }).when(myTasksExecutor).queue();
      return myTasksExecutor;
    }

    GradleBuildInvoker.Request getRequest() {
      return myRequest;
    }
  }
}
