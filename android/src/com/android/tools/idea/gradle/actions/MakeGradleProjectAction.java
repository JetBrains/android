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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MakeGradleProjectAction extends AndroidStudioGradleAction {
  public MakeGradleProjectAction() {
    super("Make Project");
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    boolean isCompilationActive = CompilerManager.getInstance(project).isCompilationActive();
    // We don't want to display the icon if we do not have a module to build as we won't know which tasks to run
    boolean haveModulesToBuild = !ProjectStructure.getInstance(project).getLeafModules().isEmpty();
    e.getPresentation().setEnabled(!isCompilationActive && haveModulesToBuild);
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      // Reset info from the previous runs (if any).
      statusBar.setInfo(" ");
    }
    // When building the project, we only invoke the build tasks for app modules and the modules no one depends on.
    // The modules that are not in this list will be built anyway because they can be transitively reached by the "leaf" modules.
    // This is necessary to avoid unnecessary work by attempting to build a module twice.
    // See: http://b/68723121
    List<Module> modules = ProjectStructure.getInstance(project).getLeafModules();
    GradleBuildInvoker.getInstance(project).assemble(modules.toArray(Module.EMPTY_ARRAY), TestCompileType.ALL);
  }
}
