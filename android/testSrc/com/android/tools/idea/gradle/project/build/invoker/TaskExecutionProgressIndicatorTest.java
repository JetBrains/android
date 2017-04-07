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

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link TaskExecutionProgressIndicator}.
 */
public class TaskExecutionProgressIndicatorTest {
  @Mock private BuildStopper myBuildStopper;

  private ExternalSystemTaskId myTaskId;

  @Before
  public void setUp() {
    initMocks(this);
    myTaskId = ExternalSystemTaskId.create(ProjectSystemId.IDE, ExternalSystemTaskType.EXECUTE_TASK, "");
  }

  @Test
  public void cancel() {
    MyTaskExecutionProgressIndicator indicator = new MyTaskExecutionProgressIndicator(myTaskId, myBuildStopper);
    indicator.cancel();

    verify(myBuildStopper).attemptToStopBuild(myTaskId, null);
    assertTrue(indicator.onCancelInvoked);
  }

  private static class MyTaskExecutionProgressIndicator extends TaskExecutionProgressIndicator {
    boolean onCancelInvoked;

    MyTaskExecutionProgressIndicator(@NotNull ExternalSystemTaskId taskId,
                                     @NotNull BuildStopper buildStopper) {
      super(taskId, buildStopper);
    }

    @Override
    void onCancel() {
      onCancelInvoked = true;
    }
  }
}