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
package com.android.tools.idea.actions;

import com.android.tools.idea.gradle.structure.AndroidProjectStructureConfigurable;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.intellij.ide.actions.ShowStructureSettingsAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;

/**
 * Displays the "Project Structure" dialog.
 */
public class AndroidShowStructureSettingsAction extends AndroidActionRemover {
  public AndroidShowStructureSettingsAction() {
    super(new ShowStructureSettingsAction(), ActionsBundle.message("action.ShowProjectStructureSettings.text"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (AndroidStudioSpecificInitializer.isAndroidStudio() && project != null && Projects.isGradleProject(project)) {
      AndroidProjectStructureConfigurable.getInstance(project).showDialog();
    }
    else {
      super.actionPerformed(e);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    myDelegate.update(e);
  }
}
