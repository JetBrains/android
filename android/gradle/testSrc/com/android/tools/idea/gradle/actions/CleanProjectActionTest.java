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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.mockito.Mock;

/**
 * Tests for {@link CleanProjectAction}.
 */
public class CleanProjectActionTest extends HeavyPlatformTestCase {
  private CleanProjectAction myAction;

  @Mock private GradleBuildInvoker myGradleBuildInvoker;
  @Mock private GradleBuildState myGradleBuildState;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myAction = new CleanProjectAction();
    new IdeComponents(myProject).replaceProjectService(GradleBuildInvoker.class, myGradleBuildInvoker);
    new IdeComponents(myProject).replaceProjectService(GradleBuildState.class, myGradleBuildState);
  }

  public void testCleanPerformed() {
    when(myGradleBuildState.isBuildInProgress()).thenReturn(false);
    AnActionEvent event = mock(AnActionEvent.class);
    Project project = getProject();
    myAction.doPerform(event, project);
    verify(myGradleBuildInvoker).cleanProject();
  }

  public void testNotCleanWhileBuild() {
    when(myGradleBuildState.isBuildInProgress()).thenReturn(true);
    AnActionEvent event = mock(AnActionEvent.class);
    Project project = getProject();
    myAction.doPerform(event, project);
    verify(myGradleBuildInvoker, never()).cleanProject();
  }
}
