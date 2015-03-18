/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.intellij.compiler.actions.CompileDirtyAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

public class AndroidMakeProjectAction extends AndroidBuildProjectAction {
  public AndroidMakeProjectAction() {
    super(new CompileDirtyAction(), "Make Project");
  }

  @Override
  protected void buildGradleProject(@NotNull Project project, @NotNull DataContext dataContext) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      // Reset info from the previous runs (if any).
      statusBar.setInfo(" ");
    }
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    GradleInvoker.getInstance(project).compileJava(moduleManager.getModules(), GradleInvoker.TestCompileType.NONE);
  }
}
