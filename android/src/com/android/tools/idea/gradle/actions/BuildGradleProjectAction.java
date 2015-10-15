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

import com.android.tools.idea.actions.AndroidStudioActionRemover;
import com.intellij.compiler.actions.CompileActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.android.tools.idea.gradle.util.Projects.isDirectGradleInvocationEnabled;

public abstract class BuildGradleProjectAction extends AndroidStudioActionRemover {
  protected BuildGradleProjectAction(@NotNull CompileActionBase delegate, @NotNull String backupText) {
    super(delegate, backupText);
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && isBuildWithGradle(project) && isDirectGradleInvocationEnabled(project)) {
      buildGradleProject(project, e.getDataContext());
      return;
    }
    super.actionPerformed(e);
  }

  protected abstract void buildGradleProject(@NotNull Project project, @NotNull DataContext dataContext);

  @Override
  public void update(AnActionEvent e) {
    myDelegate.update(e);
  }
}
