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
package com.android.tools.idea.mlkit;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.intellij.testFramework.RunsInEdt;
import org.junit.Rule;
import org.junit.Test;

public class ProjectMlModelFileTrackerTest {

  @Rule
  public final AndroidGradleProjectRule myProjectRule = new AndroidGradleProjectRule();

  @Test
  @RunsInEdt
  public void testProjectSyncWithAGPVersionUpdate() {
    myProjectRule.load(SIMPLE_APPLICATION);
    ProjectMlModelFileTracker mlModelFileTracker = new ProjectMlModelFileTracker(myProjectRule.getProject());
    mlModelFileTracker.setGradleVersion(GradleVersion.parse("3.6.0"));
    long count = mlModelFileTracker.getModificationCount();

    myProjectRule.getProject().getMessageBus().syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(
      ProjectSystemSyncManager.SyncResult.SUCCESS);

    // Verify modification count bumped up.
    assertThat(mlModelFileTracker.getModificationCount()).isEqualTo(count + 1);
  }

  @Test
  @RunsInEdt
  public void testProjectSyncWithNullAGPVersionUpdate() {
    myProjectRule.load(SIMPLE_APPLICATION);
    ProjectMlModelFileTracker mlModelFileTracker = new ProjectMlModelFileTracker(myProjectRule.getProject());
    mlModelFileTracker.setGradleVersion(null);
    long count = mlModelFileTracker.getModificationCount();

    myProjectRule.getProject().getMessageBus().syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(
      ProjectSystemSyncManager.SyncResult.SUCCESS);

    // Verify modification count bumped up.
    assertThat(mlModelFileTracker.getModificationCount()).isEqualTo(count + 1);
  }
}
