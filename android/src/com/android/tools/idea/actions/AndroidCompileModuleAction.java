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
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.compiler.actions.CompileAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * This action compiles Java code only if the Android/Gradle model provides the appropriate Java task. If the task is not provided, this
 * action will be invisible for Gradle-based Android projects. For non-Gradle projects, this action will simply delegate to the original
 * "Compile" action in IDEA.
 */
public class AndroidCompileModuleAction extends AndroidBuildModuleAction {
  public AndroidCompileModuleAction() {
    super(new CompileAction(), "Compile Module(s)", "Compile");
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && Projects.isGradleProject(project)) {
      updatePresentation(e);
    }
    else {
      // For non-Gradle projects, the "Compile" action offers to compile individual files, something that we don't support for Gradle
      // projects.
      super.update(e);
    }
  }

  @Override
  protected void buildGradleProject(@NotNull Project project, @NotNull DataContext dataContext) {
    Module[] modules = Projects.getModulesToBuildFromSelection(project, dataContext);
    GradleInvoker.getInstance(project).compileJava(modules);
  }
}
