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

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.compiler.actions.CompileAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

/**
 * This action compiles Java code only if the Android/Gradle model provides the appropriate Java task. If the task is not provided, this
 * action will be invisible for Gradle-based Android projects. For non-Gradle projects, this action will simply delegate to the original
 * "Compile" action in IDEA.
 */
public class AndroidCompileAction extends AndroidActionRemover {
  public AndroidCompileAction() {
    super(new CompileAction(), "Compile");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && Projects.isGradleProject(project)) {
      Projects.compileJava(project);
      return;
    }
    super.actionPerformed(e);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project != null && Projects.isGradleProject(project)) {
      presentation.setEnabledAndVisible(true);
      presentation.setText("Compile Project");
      return;
    }
    super.update(e);
  }
}
