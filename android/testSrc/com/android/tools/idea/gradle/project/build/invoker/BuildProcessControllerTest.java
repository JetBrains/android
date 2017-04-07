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

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildProcessController}.
 */
public class BuildProcessControllerTest {
  @Mock private BuildStopper myBuildStopper;
  @Mock private ProgressIndicator myProgressIndicator;

  private ExternalSystemTaskId myId;
  private BuildProcessController myController;

  @Before
  public void setUp() {
    initMocks(this);
    myId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, EXECUTE_TASK, "id");
    myController = new BuildProcessController(myId, myBuildStopper, myProgressIndicator);
  }

  @Test
  public void stopProcess() throws Exception {
    myController.stopProcess();
    verify(myBuildStopper).attemptToStopBuild(myId, myProgressIndicator);
  }

  @Test
  public void isProcessStoppedWithRunningProgressIndicator() throws Exception {
    when(myProgressIndicator.isRunning()).thenReturn(true);
    assertFalse(myController.isProcessStopped());
  }

  @Test
  public void isProcessStoppedWithNotRunningProgressIndicator() throws Exception {
    when(myProgressIndicator.isRunning()).thenReturn(false);
    assertTrue(myController.isProcessStopped());
  }
}