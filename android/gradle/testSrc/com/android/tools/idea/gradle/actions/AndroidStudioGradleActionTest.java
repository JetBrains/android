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

import com.android.tools.idea.gradle.project.Info;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidStudioGradleAction}.
 */
public class AndroidStudioGradleActionTest extends PlatformTestCase {
  @Mock private AnActionEvent myEvent;
  @Mock private Info myProjectInfo;

  private Presentation myPresentation;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myPresentation = new Presentation();
    when(myEvent.getPresentation()).thenReturn(myPresentation);
    when(myEvent.getProject()).thenReturn(myProject);

    new IdeComponents(getProject()).replaceProjectService(Info.class, myProjectInfo);
  }

  public void testUpdateWithAndroidStudioAndGradleProject() {
    when(myProjectInfo.isBuildWithGradle()).thenReturn(true);

    AndroidStudioGradleActionStub action = new AndroidStudioGradleActionStub();
    action.update(myEvent);

    verifyWasPopulated(action.updateInvocationParameters);
    assertNull(action.actionPerformedInvocationParameters);
    assertTrue(myPresentation.isEnabledAndVisible());
  }

  public void testUpdateWithAndroidStudioAndNonGradleProject() {
    when(myProjectInfo.isBuildWithGradle()).thenReturn(false);

    AndroidStudioGradleActionStub action = new AndroidStudioGradleActionStub();
    action.update(myEvent);

    assertNull(action.updateInvocationParameters);
    assertNull(action.actionPerformedInvocationParameters);
    assertFalse(myPresentation.isEnabledAndVisible());
  }

  public void testActionPerformedWithAndroidStudioAndGradleProject() {
    when(myProjectInfo.isBuildWithGradle()).thenReturn(true);

    AndroidStudioGradleActionStub action = new AndroidStudioGradleActionStub();
    action.actionPerformed(myEvent);

    verifyWasPopulated(action.actionPerformedInvocationParameters);
    assertNull(action.updateInvocationParameters);
  }

  private void verifyWasPopulated(ActionInvocationParameters parameters) {
    assertNotNull(parameters);
    assertSame(myEvent, parameters.event);
    assertSame(getProject(), parameters.project);
  }

  public void testActionPerformedWithAndroidStudioAndNonGradleProject() {
    when(myProjectInfo.isBuildWithGradle()).thenReturn(false);

    AndroidStudioGradleActionStub action = new AndroidStudioGradleActionStub();
    action.actionPerformed(myEvent);

    assertNull(action.updateInvocationParameters);
    assertNull(action.actionPerformedInvocationParameters);
  }

  private static class AndroidStudioGradleActionStub extends AndroidStudioGradleAction {
    @Nullable ActionInvocationParameters updateInvocationParameters;
    @Nullable ActionInvocationParameters actionPerformedInvocationParameters;

    AndroidStudioGradleActionStub() {
      super("Test");
    }

    @Override
    protected void doUpdate(@NotNull AnActionEvent event, @NotNull Project project) {
      updateInvocationParameters = new ActionInvocationParameters(event, project);
    }

    @Override
    protected void doPerform(@NotNull AnActionEvent event, @NotNull Project project) {
      actionPerformedInvocationParameters = new ActionInvocationParameters(event, project);
    }
  }

  private static class ActionInvocationParameters {
    @NotNull final AnActionEvent event;
    @NotNull final Project project;

    ActionInvocationParameters(@NotNull AnActionEvent event, @NotNull Project project) {
      this.event = event;
      this.project = project;
    }
  }
}
