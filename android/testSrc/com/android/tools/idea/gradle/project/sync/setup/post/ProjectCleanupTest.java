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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProjectCleanup}.
 */
public class ProjectCleanupTest extends IdeaTestCase {
  @Mock private GradleSyncState mySyncState;
  @Mock private IdeModifiableModelsProvider myModelsProvider;
  @Mock private ProjectCleanupStep myStep1;
  @Mock private ProjectCleanupStep myStep2;

  private ProgressIndicator myIndicator;
  private ProjectCleanup myProjectCleanup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    IdeComponents.replaceService(project, GradleSyncState.class, mySyncState);

    myIndicator = new EmptyProgressIndicator();
    myProjectCleanup = new ProjectCleanup(myStep1, myStep2);
  }

  public void testCleanUpProjectWithFailedSync() {
    // last sync failed.
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(true);

    Project project = getProject();
    myProjectCleanup.cleanUpProject(project, myModelsProvider, myIndicator);

    // If last sync failed, project should be cleaned up.
    verify(myStep1, never()).cleanUpProject(project, myModelsProvider, myIndicator);
    verify(myStep2, never()).cleanUpProject(project, myModelsProvider, myIndicator);
  }

  public void testCleanUpProjectWithSyncSkipped() {
    // Only step2 can run when sync is skipped.
    when(myStep2.invokeOnSkippedSync()).thenReturn(true);

    // sync successful.
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(false);
    // sync skipped.
    when(mySyncState.isSyncSkipped()).thenReturn(true);

    Project project = getProject();
    myProjectCleanup.cleanUpProject(project, myModelsProvider, myIndicator);

    // Only step2 should have called.
    verify(myStep1, never()).cleanUpProject(project, myModelsProvider, myIndicator);
    verify(myStep2, times(1)).cleanUpProject(project, myModelsProvider, myIndicator);
  }

  public void testCleanUpProject() {
    // sync successful.
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(false);
    // sync not skipped.
    when(mySyncState.isSyncSkipped()).thenReturn(false);

    Project project = getProject();
    myProjectCleanup.cleanUpProject(project, myModelsProvider, myIndicator);

    // All steps should have called.
    verify(myStep1, times(1)).cleanUpProject(project, myModelsProvider, myIndicator);
    verify(myStep2, times(1)).cleanUpProject(project, myModelsProvider, myIndicator);
  }
}