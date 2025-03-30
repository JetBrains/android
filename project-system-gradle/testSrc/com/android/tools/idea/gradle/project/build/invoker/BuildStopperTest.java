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
package com.android.tools.idea.gradle.project.build.invoker;

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.progress.ProgressIndicator;
import org.gradle.tooling.CancellationTokenSource;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/**
 * Tests for {@link BuildStopper}.
 */
public class BuildStopperTest {
  @Mock private CancellationTokenSource myTokenSource;

  private ExternalSystemTaskId myId;
  private BuildStopper myMapping;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    myId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, EXECUTE_TASK, "id");
    myMapping = new BuildStopper();
  }

  @Test
  public void addRemoveAndContains() {
    assertFalse(myMapping.contains(myId));

    myMapping.register(myId, myTokenSource);
    assertSame(myTokenSource, myMapping.get(myId));

    assertTrue(myMapping.contains(myId));

    myMapping.remove(myId);
    assertFalse(myMapping.contains(myId));
  }

  @Test
  public void stopBuildWithStoredToken() {
    myMapping.register(myId, myTokenSource);
    myMapping.attemptToStopBuild(myId, null);
    verify(myTokenSource, times(1)).cancel();
  }

  @Test
  public void stopBuildWithStoredTokenAndRunningProgressIndicator() {
    ProgressIndicator progressIndicator = mock(ProgressIndicator.class);
    when(progressIndicator.isRunning()).thenReturn(true);

    myMapping.register(myId, myTokenSource);
    myMapping.attemptToStopBuild(myId, progressIndicator);

    verify(myTokenSource, times(1)).cancel();
    verify(progressIndicator, times(1)).setText("Stopping Gradle build...");
    verify(progressIndicator, times(1)).cancel();
  }

  @Test
  public void stopBuildWithStoredTokenAndCancelledProgressIndicator() {
    ProgressIndicator progressIndicator = mock(ProgressIndicator.class);
    when(progressIndicator.isCanceled()).thenReturn(true);

    myMapping.register(myId, myTokenSource);
    myMapping.attemptToStopBuild(myId, progressIndicator);

    verify(myTokenSource, never()).cancel();
    verify(progressIndicator, never()).cancel();
  }

  @Test
  public void stopBuildWithoutStoredToken() {
    myMapping.remove(myId);
    myMapping.attemptToStopBuild(myId, null);
    verify(myTokenSource, never()).cancel();
  }
}