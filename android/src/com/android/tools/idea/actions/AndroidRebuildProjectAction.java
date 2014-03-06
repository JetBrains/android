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
import com.intellij.compiler.actions.CompileProjectAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AndroidRebuildProjectAction extends AndroidBuildProjectAction {
  public AndroidRebuildProjectAction() {
    super(new CompileProjectAction(), "Rebuild Project");
  }

  @Override
  protected void buildGradleProject(@NotNull Project project, @NotNull DataContext dataContext) {
    GradleInvoker.getInstance(project).rebuild();
  }
}
