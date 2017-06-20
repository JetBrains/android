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

import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link CleanProjectAction}.
 */
public class CleanProjectActionTest extends IdeaTestCase {
  private CleanProjectAction myAction;
  @Mock private GradleProjectBuilder myGradleProjectBuilder;
  @Mock private GradleBuildState myGradleBuildState;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myAction = new CleanProjectAction();
    IdeComponents.replaceService(myProject, GradleProjectBuilder.class, myGradleProjectBuilder);
    IdeComponents.replaceService(myProject, GradleBuildState.class, myGradleBuildState);
  }

  public void testCleanPerformed() {
    when(myGradleBuildState.isBuildInProgress()).thenReturn(false);
    AnActionEvent event = mock(AnActionEvent.class);
    Project project = getProject();
    myAction.doPerform(event, project);
    verify(myGradleProjectBuilder).clean();
  }

  public void testNotCleanWhileBuild() {
    when(myGradleBuildState.isBuildInProgress()).thenReturn(true);
    AnActionEvent event = mock(AnActionEvent.class);
    Project project = getProject();
    myAction.doPerform(event, project);
    verify(myGradleProjectBuilder, never()).clean();
  }
}
