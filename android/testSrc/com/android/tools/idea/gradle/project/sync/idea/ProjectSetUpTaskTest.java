/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ServiceContainerUtil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

public class ProjectSetUpTaskTest extends AndroidGradleTestCase {
  // Verifies that calling onFailure because of invoking sync while other is running does not change GradleSyncState
  // See b/75005810
  public void testOnFailureDoubleSync() {
    // Simulate a sync is already running
    Project project = getProject();
    GradleSyncState mockSyncState = mock(GradleSyncState.class);
    ServiceContainerUtil
      .replaceService(project, GradleSyncState.class, mockSyncState, getTestRootDisposable());

    ProjectSetUpTask setUpTask = new ProjectSetUpTask(project, new PostSyncProjectSetup.Request(), null, true);
    setUpTask.onFailure(ExternalSystemBundle.message("error.resolve.already.running", project.getProjectFilePath()), null);

    // Verify nothing was done to GradleSyncState
    verifyZeroInteractions(mockSyncState);
  }
}
