/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.testFramework.ProjectRule;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class DefaultStudioProgramRunnerTest {

  @Rule public ProjectRule myProjectRule = new ProjectRule();
  @Mock private AndroidExecutionTarget target;
  private AndroidRunConfiguration runConfig;
  @Mock private RunProfile runProfile;
  private ProjectSystemSyncManager syncManager;
  public boolean isSyncInProgress = false;
  public boolean isSyncNeeded = false;
  private DefaultStudioProgramRunner myRunner;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    syncManager = new ProjectSystemSyncManager() {
      @NotNull
      @Override
      public SyncResult getLastSyncResult() {
        throw(new IllegalStateException("getLastSyncResult unimplemented"));
      }

      @NotNull
      @Override
      public ListenableFuture<SyncResult> syncProject(@NotNull SyncReason reason) {
        throw(new IllegalStateException("syncProject unimplemented"));
      }

      @Override
      public boolean isSyncInProgress() {
        return isSyncInProgress;
      }

      @Override
      public boolean isSyncNeeded() {
        return isSyncNeeded;
      }
    };
    runConfig = new AndroidRunConfiguration(myProjectRule.getProject(), AndroidRunConfigurationType.getInstance().getFactory());
  }

  @Before
  public void newDefaultStudioProgramRunner() {
    myRunner = new DefaultStudioProgramRunner(project -> syncManager, (project, profile) -> target);
  }

  /**
   * Checks if the program runner is disabled when the project is being synced or needs syncing.
   */
  @Test
  public void canRun() {
    Mockito.when(target.getRunningDevices()).thenReturn(Collections.emptyList());

    // Check that the program runner doesn't support non-AndroidRunConfigurationBase profiles.
    isSyncInProgress = false;
    isSyncNeeded = false;
    Assert.assertFalse(myRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, runProfile));

    // Check that the program runner can run when Gradle is ready.
    isSyncInProgress = false;
    isSyncNeeded = false;
    Assert.assertTrue(myRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfig));

    // Check that the program runner cannot run when Gradle is syncing.
    isSyncInProgress = true;
    isSyncNeeded = false;
    Assert.assertFalse(myRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfig));

    // Check that the program runner cannot run when Gradle needs syncing.
    isSyncInProgress = false;
    isSyncNeeded = true;
    Assert.assertFalse(myRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfig));

    // Check that the program runner cannot run when Gradle is completely out of whack.
    isSyncInProgress = true;
    isSyncNeeded = true;
    Assert.assertFalse(myRunner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfig));
  }
}
