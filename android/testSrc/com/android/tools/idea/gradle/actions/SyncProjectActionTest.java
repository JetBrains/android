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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_REQUEST;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SyncProjectAction}.
 */
public class SyncProjectActionTest extends IdeaTestCase {
  @Mock private GradleSyncInvoker mySyncInvoker;
  @Mock GradleSyncState mySyncState;
  @Mock private AnActionEvent myEvent;

  private Presentation myPresentation;
  private SyncProjectAction myAction;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myPresentation = new Presentation();
    when(myEvent.getPresentation()).thenReturn(myPresentation);

    myAction = new SyncProjectAction("Test", mySyncInvoker);
  }

  public void testDoPerform() {
    Project project = getProject();
    BuildVariantView buildVariantView = mock(BuildVariantView.class);
    IdeComponents.replaceService(project, BuildVariantView.class, buildVariantView);

    myAction.doPerform(myEvent, project);

    assertTrue(myPresentation.isEnabled());
    verify(mySyncInvoker).requestProjectSyncAndSourceGeneration(project, TRIGGER_USER_REQUEST);
    verify(buildVariantView).projectImportStarted();
  }

  public void testDoUpdateWithSyncInProgress() {
    Project project = getProject();
    IdeComponents.replaceService(project, GradleSyncState.class, mySyncState);
    when(mySyncState.isSyncInProgress()).thenReturn(true);

    myAction.doUpdate(myEvent, project);

    assertFalse(myPresentation.isEnabled());
  }

  public void testDoUpdateWithSyncNotInProgress() {
    Project project = getProject();
    IdeComponents.replaceService(project, GradleSyncState.class, mySyncState);
    when(mySyncState.isSyncInProgress()).thenReturn(false);

    myAction.doUpdate(myEvent, project);

    assertTrue(myPresentation.isEnabled());
  }
}