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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class ProjectSetUpTaskTest extends AndroidGradleTestCase {
  @Mock private GradleSyncState mySyncState;
  @Mock private ExternalSystemTaskId myTaskId;
  private ProjectSetUpTask mySetupTask;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    new IdeComponents(getProject()).replaceProjectService(GradleSyncState.class, mySyncState);
    mySetupTask = new ProjectSetUpTask(getProject(), new PostSyncProjectSetup.Request(), null);
  }

  // Verifies that calling onFailure because of invoking sync while other is running does not change GradleSyncState
  // See b/75005810
  public void testOnFailureDoubleSync() {
    // Simulate a sync is already running
    Project project = getProject();
    mySetupTask.onFailure(myTaskId, ExternalSystemBundle.message("error.resolve.already.running", project.getProjectFilePath()), null);

    // Verify nothing was done to GradleSyncState
    verifyZeroInteractions(mySyncState);
  }

  public void testOnFailureCreatesFinishBuildEvent() {
    SyncViewManager syncViewManager = mock(SyncViewManager.class);
    ArgumentCaptor<BuildEvent> eventCaptor = ArgumentCaptor.forClass(BuildEvent.class);
    new IdeComponents(getProject()).replaceProjectService(SyncViewManager.class, syncViewManager);

    // Invoke method to test.
    mySetupTask.onFailure(myTaskId, "sync failed", null);

    // Verify that FinishBuildEvent is created.
    verify(syncViewManager, times(1)).onEvent(eq(myTaskId), eventCaptor.capture());
    List<BuildEvent> capturedEvents = eventCaptor.getAllValues();
    assertThat(capturedEvents).hasSize(1);
    assertThat(capturedEvents.get(0)).isInstanceOf(FinishBuildEvent.class);
  }
}
