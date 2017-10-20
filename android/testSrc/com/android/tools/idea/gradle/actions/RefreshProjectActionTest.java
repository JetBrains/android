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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link RefreshProjectAction}.
 */
public class RefreshProjectActionTest extends IdeaTestCase {
  @Mock private ExternalSystemProcessingManager myProcessingManager;
  @Mock private AnActionEvent myEvent;

  private Presentation myPresentation;
  private RefreshProjectAction myAction;
  private IdeComponents myIdeComponents;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myIdeComponents = new IdeComponents(myProject);
    myIdeComponents.replaceService(ExternalSystemProcessingManager.class, myProcessingManager);
    myPresentation = new Presentation();
    when(myEvent.getPresentation()).thenReturn(myPresentation);
    myAction = new RefreshProjectAction();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testDoUpdateWithoutTasksInProgress() {
    when(myProcessingManager.hasTaskOfTypeInProgress(RESOLVE_PROJECT, myProject)).thenReturn(false);
    myAction.doUpdate(myEvent, myProject);
    assertTrue(myPresentation.isEnabled());
  }

  public void testDoUpdateWithTasksInProgress() {
    when(myProcessingManager.hasTaskOfTypeInProgress(RESOLVE_PROJECT, myProject)).thenReturn(true);
    myAction.doUpdate(myEvent, myProject);
    assertFalse(myPresentation.isEnabled());
  }
}
