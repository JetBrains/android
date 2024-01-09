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
import static com.android.tools.idea.gradle.util.BuildMode.BUNDLE;
import static com.android.tools.idea.gradle.util.BuildMode.CLEAN;
import static com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.idea.Projects;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.JavaModuleModelBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.XDebugSession;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link GradleBuildInvoker}.
 */
public class GradleBuildInvokerTest extends HeavyPlatformTestCase {
  private final FakeGradleTaskExecutor myGradleTaskExecutor = new FakeGradleTaskExecutor();
  @Mock private FileDocumentManager myFileDocumentManager;
  @Mock private NativeDebugSessionFinder myDebugSessionFinder;

  private Module[] myModules;
  @Mock private GradleTaskFinder myTaskFinder;

  AutoCloseable myCloseable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCloseable = MockitoAnnotations.openMocks(this);
  }

  private GradleBuildInvoker createBuildInvoker() {
    return createBuildInvoker(GradleTaskFinder.getInstance());
  }

  private GradleBuildInvoker createBuildInvoker(GradleTaskFinder taskFinder) {
    myModules = new Module[]{getModule()};
    return GradleBuildInvokerImpl.Companion.createBuildInvoker(myProject, myFileDocumentManager, myGradleTaskExecutor, myDebugSessionFinder,
                                                               taskFinder);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
      myCloseable.close();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testCleanUp() {
    setupTestProjectFromAndroidModel(myProject,
                                     getTempDir().createDir().toFile(),
                                     new AndroidModuleModelBuilder(":", "debug", new AndroidProjectBuilder()));
    GradleBuildInvoker buildInvoker = createBuildInvoker();
    // Invoke method to test.
    buildInvoker.cleanProject();
    GradleBuildInvoker.Request request = myGradleTaskExecutor.getLastRequest();
    assertThat(request).isNotNull();
    // Verify task list includes clean.
    assertThat(request.getGradleTasks()).containsExactly("clean");
    assertThat(request.getCommandLineArguments()).isEmpty();
    verifyInteractionWithMocks(CLEAN);
  }

  public void testCleanupWithNativeDebugSessionAndUserTerminatesSession() {
    setupTestProjectFromAndroidModel(myProject,
                                     getTempDir().createDir().toFile(),
                                     new AndroidModuleModelBuilder(":", "debug", new AndroidProjectBuilder()));
    GradleBuildInvoker buildInvoker = createBuildInvoker();

    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    when(myDebugSessionFinder.findNativeDebugSession()).thenReturn(nativeDebugSession);

    TestDialogManager.setTestDialog(TestDialog.OK);

    buildInvoker.cleanProject();

    verify(nativeDebugSession).stop(); // expect that the session was stopped.
  }

  public void testCleanupWithNativeDebugSessionAndUserDoesNotTerminateSession() {
    setupTestProjectFromAndroidModel(myProject,
                                     getTempDir().createDir().toFile(),
                                     new AndroidModuleModelBuilder(":", "debug", new AndroidProjectBuilder()));
    GradleBuildInvoker buildInvoker = createBuildInvoker();

    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    when(myDebugSessionFinder.findNativeDebugSession()).thenReturn(nativeDebugSession);

    TestDialogManager.setTestDialog(TestDialog.NO);

    buildInvoker.cleanProject();

    verify(nativeDebugSession, never()).stop(); // expect that the session was never stopped.
  }

  public void testCleanupWithNativeDebugSessionAndUserCancelsBuild() {
    setupTestProjectFromAndroidModel(myProject,
                                     getTempDir().createDir().toFile(),
                                     new AndroidModuleModelBuilder(":", "debug", new AndroidProjectBuilder()));
    GradleBuildInvoker buildInvoker = createBuildInvoker();

    XDebugSession nativeDebugSession = mock(XDebugSession.class);
    when(myDebugSessionFinder.findNativeDebugSession()).thenReturn(nativeDebugSession);

    TestDialogManager.setTestDialog(message -> Messages.CANCEL);

    buildInvoker.cleanProject();

    verify(nativeDebugSession, never()).stop(); // expect that the session was never stopped.

    GradleBuildInvoker.Request request = myGradleTaskExecutor.getLastRequest();
    assertNull(request); // Build was canceled, no request created.

    // If build was canceled, none of these methods should have been invoked.
    verify(myFileDocumentManager, never()).saveAllDocuments();
    assertThat(myGradleTaskExecutor.getInvoked()).isEqualTo(0);
  }

  public void testCompileJava() {
    setupTestProjectFromAndroidModel(myProject,
                                     Projects.getBaseDirPath(myProject),
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));

    GradleBuildInvoker buildInvoker = createBuildInvoker();
    buildInvoker.compileJava(
      ImmutableList.of(
        Objects.requireNonNull(gradleModule(myProject, ":app")),
        Objects.requireNonNull(gradleModule(myProject, ":lib"))
      ).toArray(new Module[0])
    );

    GradleBuildInvoker.Request request = myGradleTaskExecutor.getLastRequest();
    assertThat(request).isNotNull();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(ImmutableList.of(
      ":lib:compileDebugUnitTestSources",
      ":lib:compileDebugAndroidTestSources",
      ":lib:compileDebugSources",
      ":app:compileDebugUnitTestSources",
      ":app:compileDebugAndroidTestSources",
      ":app:compileDebugSources"
    ));

    verifyInteractionWithMocks(COMPILE_JAVA);
  }

  public void testAssembleWhenNoTasksToRun() {
    setupTestProjectFromAndroidModel(myProject,
                                     Projects.getBaseDirPath(myProject),
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()));
    var modules = ImmutableList.of(
      Objects.requireNonNull(gradleModule(myProject, ":app"))
    ).toArray(new Module[0]);
    when(myTaskFinder.findTasksToExecute(modules, ASSEMBLE, false)).thenReturn(ArrayListMultimap.create());
    GradleBuildInvoker buildInvoker = createBuildInvoker(myTaskFinder);
    ListenableFuture<AssembleInvocationResult> assembleResult = buildInvoker.assemble(
      modules
    );

    assertThat(assembleResult.isCancelled()).isTrue();
    GradleBuildInvoker.Request request = myGradleTaskExecutor.getLastRequest();
    assertThat(request).isNull();
    assertThat(myGradleTaskExecutor.getInvoked()).isEqualTo(0);
    assertThat(myGradleTaskExecutor.getLastRequest()).isNull();
  }

  public void testRequestsWithMultipleModes() {
    setupTestProjectFromAndroidModel(myProject,
                                     Projects.getBaseDirPath(myProject),
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));
    var modules = ImmutableList.of(
      Objects.requireNonNull(gradleModule(myProject, ":app")),
      Objects.requireNonNull(gradleModule(myProject, ":lib"))
    ).toArray(new Module[0]);
    GradleBuildInvoker buildInvoker = createBuildInvoker(myTaskFinder);
    var requests = List.of(GradleBuildInvoker.Request
              .builder(myProject, ProjectUtil.guessProjectDir(myProject).toNioPath().toFile(), List.of(), null)
              .setMode(BUNDLE)
              .build(),
            GradleBuildInvoker.Request
              .builder(myProject, ProjectUtil.guessProjectDir(myProject).toNioPath().toFile(), List.of(), null)
              .setMode(ASSEMBLE)
              .build());
    assertThrows(IllegalArgumentException.class,
                 "Each request requires the same not null build mode to be set",
                 () -> buildInvoker.executeAssembleTasks(modules, requests));
  }

  public void testBundleWhenNoTasksToRun() {
    setupTestProjectFromAndroidModel(myProject,
                                     Projects.getBaseDirPath(myProject),
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));
    var modules = ImmutableList.of(
      Objects.requireNonNull(gradleModule(myProject, ":app")),
      Objects.requireNonNull(gradleModule(myProject, ":lib"))
    ).toArray(new Module[0]);
    when(myTaskFinder.findTasksToExecute(modules, BUNDLE, false)).thenReturn(ArrayListMultimap.create());
    GradleBuildInvoker buildInvoker = createBuildInvoker(myTaskFinder);

    ListenableFuture<AssembleInvocationResult> bundleResult = buildInvoker.bundle(modules);
    assertThat(bundleResult.isCancelled()).isTrue();
    GradleBuildInvoker.Request request = myGradleTaskExecutor.getLastRequest();
    assertThat(request).isNull();
    assertThat(myGradleTaskExecutor.getInvoked()).isEqualTo(0);
    assertThat(myGradleTaskExecutor.getLastRequest()).isNull();
  }

  public void testAssemble() throws Exception {
    setupTestProjectFromAndroidModel(myProject,
                                     Projects.getBaseDirPath(myProject),
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));

    GradleBuildInvoker buildInvoker = createBuildInvoker();
    ListenableFuture<AssembleInvocationResult> assembleResult = buildInvoker.assemble(
      ImmutableList.of(
        Objects.requireNonNull(gradleModule(myProject, ":app")),
        Objects.requireNonNull(gradleModule(myProject, ":lib"))
      ).toArray(new Module[0])
    );

    GradleBuildInvoker.Request request = myGradleTaskExecutor.getLastRequest();
    assertThat(request).isNotNull();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(
      ImmutableList.of(":app:assembleDebug",
                       ":app:assembleDebugUnitTest",
                       ":app:assembleDebugAndroidTest",
                       ":lib:assembleDebug",
                       ":lib:assembleDebugUnitTest",
                       ":lib:assembleDebugAndroidTest"
      ));
    assertThat(request.getCommandLineArguments()).isEmpty();
    assertThat(assembleResult.get().getBuildMode()).isEqualTo(ASSEMBLE);

    verifyInteractionWithMocks(ASSEMBLE);
  }

  public void testAssembleWithCommandLineArgs() throws Exception {
    setupTestProjectFromAndroidModel(myProject,
                                     Projects.getBaseDirPath(myProject),
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));

    GradleBuildInvoker buildInvoker = createBuildInvoker();
    ListenableFuture<AssembleInvocationResult> assembleResult = buildInvoker.assemble(
      ImmutableList.of(
        Objects.requireNonNull(gradleModule(myProject, ":app")),
        Objects.requireNonNull(gradleModule(myProject, ":lib"))
      ).toArray(new Module[0])
    );

    GradleBuildInvoker.Request request = myGradleTaskExecutor.getLastRequest();
    assertThat(request).isNotNull();
    assertThat(request.getGradleTasks()).containsExactlyElementsIn(ImmutableList.of(
      ":app:assembleDebug",
      ":app:assembleDebugUnitTest",
      ":app:assembleDebugAndroidTest",
      ":lib:assembleDebug",
      ":lib:assembleDebugUnitTest",
      ":lib:assembleDebugAndroidTest"
    ));
    assertThat(assembleResult.get().getBuildMode()).isEqualTo(ASSEMBLE);

    verifyInteractionWithMocks(ASSEMBLE);
  }

  public void testExecuteTasksWaitForCompletionNoDispatch() {
    File projectPath = getTempDir().createDir().toFile();
    setupTestProjectFromAndroidModel(myProject,
                                     projectPath,
                                     JavaModuleModelBuilder.Companion.getRootModuleBuilder(),
                                     new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                     new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()));

    GradleBuildInvoker buildInvoker = createBuildInvoker();

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
    verify(myFileDocumentManager).saveAllDocuments();
    assertThat(myGradleTaskExecutor.getInvoked()).isEqualTo(1);
    assertThat(Objects.requireNonNull(myGradleTaskExecutor.getLastRequest()).getMode()).isEqualTo(buildMode);
  }

  @NotNull
  private static ListMultimap<Path, String> createTasksMap(@NotNull List<String> taskNames) {
    ListMultimap<Path, String> tasks = ArrayListMultimap.create();
    tasks.putAll(Paths.get("project_path"), taskNames);
    return tasks;
  }
}
