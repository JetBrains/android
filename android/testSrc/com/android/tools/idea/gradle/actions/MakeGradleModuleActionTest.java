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

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import org.mockito.Mock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link MakeGradleModuleAction}.
 */
public class MakeGradleModuleActionTest extends PlatformTestCase {
  @Mock private GradleProjectInfo myProjectInfo;
  @Mock private GradleBuildInvoker myBuildInvoker;
  @Mock private AnActionEvent myActionEvent;
  @Mock private DataContext myDataContext;

  private MakeGradleModuleAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    new IdeComponents(project).replaceProjectService(GradleProjectInfo.class, myProjectInfo);
    new IdeComponents(project).replaceProjectService(GradleBuildInvoker.class, myBuildInvoker);

    when(myActionEvent.getDataContext()).thenReturn(myDataContext);

    myAction = new MakeGradleModuleAction();
  }

  public void testDoPerform() throws Exception {
    Module module = getModule();
    Module[] selectedModules = {module};
    when(myProjectInfo.getModulesToBuildFromSelection(myDataContext)).thenReturn(selectedModules);

    // Method to test:
    myAction.doPerform(myActionEvent, getProject());

    // Verify "assemble" was invoked.
    verify(myBuildInvoker).assemble(selectedModules, TestCompileType.ALL);
  }
}
