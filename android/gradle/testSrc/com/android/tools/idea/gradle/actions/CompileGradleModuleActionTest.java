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

import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.Info;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleMultiInvocationResult;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.io.File;
import org.mockito.Mock;

/**
 * Tests for {@link CompileGradleModuleAction}.
 */
public class CompileGradleModuleActionTest extends HeavyPlatformTestCase {
  @Mock private Info myInfo;
  @Mock private GradleBuildInvoker myBuildInvoker;
  @Mock private AnActionEvent myActionEvent;
  @Mock private DataContext myDataContext;

  private CompileGradleModuleAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    new IdeComponents(project).replaceProjectService(Info.class, myInfo);
    new IdeComponents(project).replaceProjectService(GradleBuildInvoker.class, myBuildInvoker);

    when(myBuildInvoker.compileJava(any()))
      .thenReturn(
        Futures.immediateFuture(
            new GradleMultiInvocationResult(
              ImmutableList.of(
                new GradleInvocationResult(new File("/root"), emptyList(), null)
              )
            )));

    when(myActionEvent.getDataContext()).thenReturn(myDataContext);

    myAction = new CompileGradleModuleAction();
  }

  public void testDoPerform() {
    Module module = getModule();
    Module[] selectedModules = {module};
    when(myInfo.getModulesToBuildFromSelection(myDataContext)).thenReturn(selectedModules);

    // Method to test:
    myAction.doPerform(myActionEvent, getProject());

    // Verify "compile" was invoked.
    verify(myBuildInvoker).compileJava(selectedModules);
  }

  public void testNoDefaultSelection() {
    when(myInfo.getModulesToBuildFromSelection(myDataContext)).thenReturn(new Module[]{});
    myAction.doPerform(myActionEvent, getProject());

    // Verify "compile" was invoked.
    verify(myBuildInvoker).compileJava(new Module[]{});
  }

  public void testDoRememberPreviousSelection() {
    Module[] selectedModules = new Module[]{getModule()};
    when(myInfo.getModulesToBuildFromSelection(myDataContext)).thenReturn(selectedModules);

    myAction.doPerform(myActionEvent, getProject());
    verify(myBuildInvoker).compileJava(selectedModules);
    reset(myBuildInvoker);

    when(myInfo.getModulesToBuildFromSelection(myDataContext)).thenReturn(new Module[]{});
    myAction.doPerform(myActionEvent, getProject());

    // Verify previous selection is stored
    verify(myBuildInvoker).compileJava(selectedModules);
    reset(myBuildInvoker);

    Module[] myapplication = new Module[]{createModule("myapplication")};
    when(myInfo.getModulesToBuildFromSelection(myDataContext)).thenReturn(myapplication);
    myAction.doPerform(myActionEvent, getProject());

    // Verify previous selection is updated after new module is selected
    verify(myBuildInvoker).compileJava(myapplication);
  }
}
