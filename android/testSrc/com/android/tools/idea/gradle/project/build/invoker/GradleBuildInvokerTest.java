/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Request;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.TestCompileType;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.BuildMode.*;
import static com.android.tools.idea.testing.TestProjectPaths.LOCAL_AARS_AS_MODULES;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GradleBuildInvoker}.
 */
public class GradleBuildInvokerTest extends AndroidGradleTestCase {
  private GradleTasksExecutorStub myTasksExecutor;
  private GradleTasksExecutorFactoryStub myTaskExecutorFactory;
  private GradleBuildInvoker myInvoker;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myTasksExecutor = new GradleTasksExecutorStub(getProject());
    myTaskExecutorFactory = new GradleTasksExecutorFactoryStub(myTasksExecutor);

    myInvoker = new GradleBuildInvoker(getProject(), myTaskExecutorFactory);
  }

  // Following are common tests for all GradleInvoker test cases
  public void testAssembleTranslate() throws Exception {
    loadSimpleApplication();
    myTaskExecutorFactory.setExpectedTasks("assembleTranslate");

    myInvoker.assembleTranslate();

    assertIsCurrent(ASSEMBLE_TRANSLATE);
    myTasksExecutor.assertWasExecuted();
  }

  public void testCompileJava_forUnitTests() throws Exception {
    loadSimpleApplication();
    myTaskExecutorFactory.setExpectedTasks(":app:mockableAndroidJar", ":app:prepareDebugUnitTestDependencies", ":app:generateDebugSources",
                                           ":app:compileDebugUnitTestSources");

    myInvoker.compileJava(getAppModule(), TestCompileType.UNIT_TESTS);

    assertIsCurrent(COMPILE_JAVA);
    myTasksExecutor.assertWasExecuted();
  }

  public void testAssembleWithSuccessfulSync() throws Exception {
    loadSimpleApplication();
    simulateLastSyncFailed(false);

    myTaskExecutorFactory.setExpectedTasks(":app:assembleDebug");

    myInvoker.assemble(getAppModule(), TestCompileType.NONE);

    assertIsCurrent(ASSEMBLE);
    myTasksExecutor.assertWasExecuted();
  }

  public void testAssembleWithFailedSync() throws Exception {
    loadSimpleApplication();
    simulateLastSyncFailed(true);

    myTaskExecutorFactory.setExpectedTasks("assemble");

    myInvoker.assemble(getAppModule(), TestCompileType.NONE);

    assertIsCurrent(ASSEMBLE);
    myTasksExecutor.assertWasExecuted();
  }

  public void testAssemble_forAndroidTests() throws Exception {
    loadSimpleApplication();
    simulateLastSyncFailed(false);

    myTaskExecutorFactory.setExpectedTasks(":app:assembleDebug", ":app:assembleDebugAndroidTest");

    myInvoker.assemble(getAppModule(), TestCompileType.ANDROID_TESTS);

    assertIsCurrent(ASSEMBLE);
    myTasksExecutor.assertWasExecuted();
  }

  private void simulateLastSyncFailed(boolean failed) {
    GradleSyncState syncState = IdeComponents.replaceServiceWithMock(getProject(), GradleSyncState.class);
    when(syncState.lastSyncFailed()).thenReturn(failed);
  }

  public void testCleanProject() throws Exception {
    loadSimpleApplication();
    myTaskExecutorFactory.setExpectedTasks("clean", ":app:generateDebugSources", ":app:generateDebugAndroidTestSources",
                                           ":app:mockableAndroidJar", ":app:prepareDebugUnitTestDependencies");

    myInvoker.cleanProject();

    // "clean" should be the first task.
    assertEquals("clean", myTaskExecutorFactory.getRequestedTasks().get(0));
    assertIsCurrent(CLEAN);
    myTasksExecutor.assertWasExecuted();
  }

  public void testGenerateSources() throws Exception {
    loadSimpleApplication();
    myTaskExecutorFactory.setExpectedTasks(":app:generateDebugSources", ":app:generateDebugAndroidTestSources", ":app:mockableAndroidJar",
                                           ":app:prepareDebugUnitTestDependencies");
    myInvoker.generateSources(false /* do not clean */);

    assertIsCurrent(SOURCE_GEN);
    myTasksExecutor.assertWasExecuted();
  }

  public void testGenerateSourcesWithClean() throws Exception {
    loadSimpleApplication();
    myTaskExecutorFactory.setExpectedTasks("clean", ":app:generateDebugSources", ":app:generateDebugAndroidTestSources",
                                           ":app:mockableAndroidJar", ":app:prepareDebugUnitTestDependencies");

    myInvoker.generateSources(true /* clean */);

    // "clean" should be the first task.
    assertEquals("clean", myTaskExecutorFactory.getRequestedTasks().get(0));
    assertIsCurrent(SOURCE_GEN);
    myTasksExecutor.assertWasExecuted();
  }

  public void testCompileJava() throws Exception {
    loadSimpleApplication();
    myTaskExecutorFactory.setExpectedTasks(":app:generateDebugSources", ":app:generateDebugAndroidTestSources", ":app:mockableAndroidJar",
                                           ":app:prepareDebugUnitTestDependencies", ":app:compileDebugSources",
                                           ":app:compileDebugAndroidTestSources", ":app:compileDebugUnitTestSources");

    myInvoker.compileJava(getAppModule(), TestCompileType.NONE);

    assertIsCurrent(COMPILE_JAVA);
    myTasksExecutor.assertWasExecuted();
  }

  @NotNull
  private Module[] getAppModule() {
    return new Module[]{myModules.getAppModule()};
  }

  public void testRebuild() throws Exception {
    loadSimpleApplication();
    myTaskExecutorFactory.setExpectedTasks("clean", ":app:generateDebugSources", ":app:generateDebugAndroidTestSources",
                                           ":app:mockableAndroidJar", ":app:prepareDebugUnitTestDependencies", ":app:compileDebugSources",
                                           ":app:compileDebugAndroidTestSources", ":app:compileDebugUnitTestSources");

    myInvoker.rebuild();

    // "clean" should be the first task.
    assertEquals("clean", myTaskExecutorFactory.getRequestedTasks().get(0));

    assertIsCurrent(REBUILD);
    myTasksExecutor.assertWasExecuted();
  }

  public void testNoTaskForAarModule() throws Exception {
    loadProject(LOCAL_AARS_AS_MODULES);
    Module module = myModules.getModule("library-debug");

    myInvoker.compileJava(new Module[]{module}, TestCompileType.UNIT_TESTS);
  }

  private void assertIsCurrent(@NotNull BuildMode buildMode) {
    assertEquals(buildMode, BuildSettings.getInstance(getProject()).getBuildMode());
  }

  private static class GradleTasksExecutorFactoryStub extends GradleTasksExecutor.Factory {
    @NotNull private final GradleTasksExecutor myTasksExecutor;

    @NotNull private List<String> myExpectedTasks = Collections.emptyList();
    @NotNull private List<String> myRequestedTasks = Collections.emptyList();

    GradleTasksExecutorFactoryStub(@NotNull GradleTasksExecutor tasksExecutor) {
      myTasksExecutor = tasksExecutor;
    }

    void setExpectedTasks(@NotNull String... expectedTasks) {
      myExpectedTasks = Arrays.asList(expectedTasks);
    }

    @Override
    @NotNull
    GradleTasksExecutor create(@NotNull Request request,
                               @NotNull BuildStopper buildStopper) {
      myRequestedTasks = request.getGradleTasks();
      assertThat(myExpectedTasks).containsExactlyElementsIn(myRequestedTasks);
      return myTasksExecutor;
    }

    @NotNull
    List<String> getRequestedTasks() {
      return myRequestedTasks;
    }
  }

  private static class GradleTasksExecutorStub extends GradleTasksExecutor {
    private boolean myExecuted;

    private GradleTasksExecutorStub(@NotNull Project project) {
      super(project);
    }

    @Override
    public void queueAndWaitForCompletion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myExecuted = true;
    }

    void assertWasExecuted() {
      assertTrue("Gradle tasks were executed", myExecuted);
    }
  }
}
