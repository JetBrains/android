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

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BuildApkAction}.
 */
public class BuildApkActionTest extends AndroidGradleTestCase {
  private GradleBuildInvokerStub myBuildInvoker;
  private BuildApkAction myAction;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    Project project = getProject();

    myBuildInvoker = new GradleBuildInvokerStub(project);
    IdeComponents.replaceService(project, GradleBuildInvoker.class, myBuildInvoker);

    myAction = new BuildApkAction();
  }

  public void testActionPerformed() throws Exception {
    loadProject(PROJECT_WITH_APPAND_LIB);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    myAction.actionPerformed(event);

    // "Build APK(s)" action should only invoke "assemble" on app module.
    Module appModule = myModules.getAppModule();
    myModules.getModule("lib"); // Just to verify that there is a "lib" module.

    Module[] modules = myBuildInvoker.getModulesToAssemble();
    assertThat(modules).asList().containsExactly(appModule);
  }

  private static class GradleBuildInvokerStub extends GradleBuildInvoker {
    @Nullable private Module[] myModulesToAssemble;

    GradleBuildInvokerStub(@NotNull Project project) {
      super(project);
    }

    @Override
    public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
      myModulesToAssemble = modules;
    }

    @Nullable
    Module[] getModulesToAssemble() {
      return myModulesToAssemble;
    }
  }
}