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
package com.android.tools.idea.gradle.project.build;

import static com.android.tools.idea.gradle.project.build.BuildStatus.FAILED;
import static com.android.tools.idea.gradle.project.build.BuildStatus.SUCCESS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightPlatformTestCase;
import java.io.File;
import org.mockito.Mock;

/**
 * Tests for {@link GradleBuildState}.
 */
public class GradleBuildStateTest extends LightPlatformTestCase {
  @Mock private GradleBuildListener myListener;

  private BuildContext myContext;
  private GradleBuildState myBuildState;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    GradleBuildInvoker.Request request = GradleBuildInvoker.Request.builder(project, new File(project.getBasePath()), "assembleDebug")
      .setMode(BuildMode.ASSEMBLE)
      .build();
    myContext = new BuildContext(request);
    GradleBuildState.subscribe(getProject(), myListener);

    myBuildState = GradleBuildState.getInstance(project);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myBuildState.clear();
    }
    finally {
      super.tearDown();
    }
  }

  public void testBuildStarted() {
    myBuildState.buildStarted(myContext);
    assertSame(myContext, myBuildState.getRunningBuildContext());
    assertTrue(myBuildState.isBuildInProgress());
    verify(myListener).buildStarted(myContext);
  }

  public void testBuildFinished() {
    GradleBuildState.BuildCompleter completer = myBuildState.buildStarted(myContext);

    completer.buildFinished(SUCCESS);
    assertNull(myBuildState.getRunningBuildContext());
    assertFalse(myBuildState.isBuildInProgress());
    verify(myListener).buildFinished(SUCCESS, myContext);
  }

  public void testModificationTracker() {
    final var initialModificationCount = myBuildState.getModificationTracker().getModificationCount();
    GradleBuildState.BuildCompleter completer = myBuildState.buildStarted(myContext);
    completer.buildFinished(SUCCESS);
    final var successfulBuildModificationCount = myBuildState.getModificationTracker().getModificationCount();
    completer = myBuildState.buildStarted(myContext);
    completer.buildFinished(FAILED);
    final var failedBuildModificationCount = myBuildState.getModificationTracker().getModificationCount();
    assertThat(successfulBuildModificationCount).isGreaterThan(initialModificationCount);
    assertThat(failedBuildModificationCount).isGreaterThan(successfulBuildModificationCount);
  }
}