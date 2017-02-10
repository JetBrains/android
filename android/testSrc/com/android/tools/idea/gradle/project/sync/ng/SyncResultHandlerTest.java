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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SyncResultHandler}.
 */
public class SyncResultHandlerTest extends IdeaTestCase {
  @Mock private SyncExecutionCallback mySyncCallback;
  @Mock private GradleSyncListener mySyncListener;
  @Mock private GradleSyncState mySyncState;
  @Mock private ProjectSetup.Factory myProjectSetupFactory;
  @Mock private PostSyncProjectSetup myPostSyncProjectSetup;

  private ProgressIndicator myIndicator;
  private SyncResultHandler myResultHandler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myIndicator = new MockProgressIndicator();
    myResultHandler = new SyncResultHandler(getProject(), mySyncState, myProjectSetupFactory, myPostSyncProjectSetup);
  }

  public void testOnSyncFinished() {
    Project project = getProject();

    SyncAction.ProjectModels models = mock(SyncAction.ProjectModels.class);
    when(mySyncCallback.getModels()).thenReturn(models);

    ProjectSetup projectSetup = mock(ProjectSetup.class);
    when(myProjectSetupFactory.create(project)).thenReturn(projectSetup);

    myResultHandler.onSyncFinished(mySyncCallback, myIndicator, mySyncListener, false);

    verify(mySyncState).setupStarted();
    verify(mySyncState, never()).syncFailed(any());

    verify(projectSetup).setUpProject(same(models), any());
    verify(projectSetup).commit();

    verify(mySyncListener).setupStarted(project);
    verify(mySyncListener).syncSucceeded(project);
    verify(mySyncListener, never()).syncFailed(any(), any());

    verify(myPostSyncProjectSetup).setUpProject(eq(new PostSyncProjectSetup.Request()), any());
  }

  public void testOnSyncFailed() {
    Project project = getProject();

    Throwable error = new Throwable("Test error");
    when(mySyncCallback.getSyncError()).thenReturn(error);

    myResultHandler.onSyncFailed(mySyncCallback, mySyncListener);

    verify(mySyncState).syncFailed("Test error");
    verify(mySyncState, never()).setupStarted();

    verify(mySyncListener, never()).setupStarted(project);
    verify(mySyncListener, never()).syncSucceeded(project);
    verify(mySyncListener).syncFailed(project, "Test error");
  }
}