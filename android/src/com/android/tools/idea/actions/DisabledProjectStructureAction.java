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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.newEditor.OptionsEditorDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;

/**
 * Action that notifies the user that the UI for configuring a project is disabled.
 */
public class DisabledProjectStructureAction extends AnAction implements DumbAware {
  public DisabledProjectStructureAction() {
    super("Project Structure...");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null) {
      showDisabledProjectStructureDialogMessage();
      return;
    }
    project = ProjectManager.getInstance().getDefaultProject();
    ShowSettingsUtil settings = ShowSettingsUtil.getInstance();
    settings.editConfigurable(project, OptionsEditorDialog.DIMENSION_KEY, ProjectStructureConfigurable.getInstance(project));
  }

  public static void showDisabledProjectStructureDialogMessage() {
    String msg = "We will provide a UI to configure project settings later. Until then, please manually edit your build.gradle file(s.)";
    Messages.showInfoMessage(msg, "Project Structure");
  }
}
