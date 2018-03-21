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
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildApkAction}.
 */
public class BuildApkActionTest extends IdeaTestCase {
  @Mock private GradleProjectInfo myGradleProjectInfo;
  @Mock private GradleBuildInvoker myBuildInvoker;
  @Mock private ProjectStructure myProjectStructure;
  private BuildApkAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    IdeComponents.replaceService(myProject, GradleBuildInvoker.class, myBuildInvoker);
    IdeComponents.replaceService(myProject, GradleProjectInfo.class, myGradleProjectInfo);
    IdeComponents.replaceService(myProject, ProjectStructure.class, myProjectStructure);
    myAction = new BuildApkAction();
  }

  public void testActionPerformed() {
    Module app1Module = createModule("app1");
    Module app2Module = createModule("app2");

    Module[] appModules = {app1Module, app2Module};
    when(myProjectStructure.getAppModules()).thenReturn(ImmutableList.copyOf(appModules));
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    myAction.actionPerformed(event);

    verify(myBuildInvoker).assemble(eq(appModules), eq(TestCompileType.ALL), eq(Collections.emptyList()), any(OutputBuildAction.class));
  }
}