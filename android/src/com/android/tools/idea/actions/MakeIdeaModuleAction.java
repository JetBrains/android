/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.compiler.actions.MakeModuleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.util.GradleProjects.getModulesToBuildFromSelection;
import static com.intellij.openapi.actionSystem.ActionPlaces.PROJECT_VIEW_POPUP;

/**
 * Builds a module belonging to an IDEA project that is not an Android-model-based project.
 */
public class MakeIdeaModuleAction extends AndroidStudioActionRemover {
  public MakeIdeaModuleAction() {
    super(new MakeModuleAction(), "Make Module(s)");
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
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && AndroidProjectInfo.getInstance(project).requiresAndroidModel()) {
      // Projects that require a Android model have their own action to build modules and projects.
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    // We don't call the delegate's (MakeModuleAction) update method, even for non-Gradle projects. The reason is that
    // MakeModuleAction#update throws an NPE when being wrapped by this action.
    // The method 'updatePresentation' does exactly the same thing as MakeModuleAction#update, but without throwing NPE for non-Gradle
    // projects.
    updatePresentation(e, project);
  }
}
