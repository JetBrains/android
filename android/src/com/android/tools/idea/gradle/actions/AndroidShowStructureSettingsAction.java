/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.AndroidProjectStructureConfigurable;
import com.intellij.ide.actions.ShowStructureSettingsAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.android.tools.idea.gradle.util.Projects.requiresAndroidModel;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;

/**
 * Displays the "Project Structure" dialog.
 */
public class AndroidShowStructureSettingsAction extends ShowStructureSettingsAction {
  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && requiresAndroidModel(project)) {
      e.getPresentation().setEnabledAndVisible(isBuildWithGradle(project));
    }
    super.update(e);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null && isAndroidStudio()) {
      project = ProjectManager.getInstance().getDefaultProject();
      showAndroidProjectStructure(project);
      return;
    }

    if (project != null && isBuildWithGradle(project)) {
      showAndroidProjectStructure(project);
      return;
    }

    super.actionPerformed(e);
  }

  private static void showAndroidProjectStructure(@NotNull Project project) {
    AndroidProjectStructureConfigurable.getInstance(project).showDialog();
  }
}
