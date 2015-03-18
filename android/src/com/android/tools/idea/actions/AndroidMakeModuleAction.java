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
import com.intellij.compiler.actions.MakeModuleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AndroidMakeModuleAction extends AndroidBuildModuleAction {
  public AndroidMakeModuleAction() {
    super(new MakeModuleAction(), "Make Module(s)", "Make");
  }

  @Override
  public void update(AnActionEvent e) {
    // We don't call the delegate's (MakeModuleAction) update method, even for non-Gradle projects. The reason is that
    // MakeModuleAction#update throws an NPE when being wrapped by this action.
    // The method 'updatePresentation' does exactly the same thing as MakeModuleAction#update, but without throwing NPE for non-Gradle
    // projects.
    updatePresentation(e);
  }

  @Override
  protected void buildGradleProject(@NotNull Project project, @NotNull DataContext dataContext) {
    Module[] modules = Projects.getModulesToBuildFromSelection(project, dataContext);
    GradleInvoker.getInstance(project).compileJava(modules, GradleInvoker.TestCompileType.NONE);
  }
}
