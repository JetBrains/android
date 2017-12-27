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

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.util.Projects.getModulesToBuildFromSelection;
import static com.intellij.openapi.actionSystem.ActionPlaces.PROJECT_VIEW_POPUP;

public class MakeGradleModuleAction extends AndroidStudioGradleAction {
  public MakeGradleModuleAction() {
    super("Make Module(s)");
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    updatePresentation(e, project);
  }

  public static void updatePresentation(@NotNull AnActionEvent e, @NotNull Project project) {
    DataContext dataContext = e.getDataContext();

    Module[] modules = getModulesToBuildFromSelection(project, dataContext);
    int moduleCount = modules.length;

    Presentation presentation = e.getPresentation();
    presentation.setEnabled(moduleCount > 0);

    String presentationText;
    if (moduleCount > 0) {
      String text = "Make Module";
      if (moduleCount > 1) {
        text += "s";
      }
      for (int i = 0; i < moduleCount; i++) {
        if (text.length() > 30) {
          text = "Make Selected Modules";
          break;
        }
        Module toMake = modules[i];
        if (i != 0) {
          text += ",";
        }
        text += " '" + toMake.getName() + "'";
      }
      presentationText = text;
    }
    else {
      presentationText = "Make";
    }
    presentation.setText(presentationText);
    presentation.setVisible(moduleCount > 0 || !PROJECT_VIEW_POPUP.equals(e.getPlace()));
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    Module[] modules = getModulesToBuildFromSelection(project, e.getDataContext());
    GradleBuildInvoker.getInstance(project).compileJava(modules, TestCompileType.ALL);
  }
}
