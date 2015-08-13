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

import com.android.tools.idea.structure.AndroidProjectStructureConfigurable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.newEditor.OptionsEditorDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;

/**
 * Displays the "Default Project Structure" dialog.
 */
public class AndroidTemplateProjectStructureAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(final AnActionEvent e) {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    AndroidProjectStructureConfigurable configurable = AndroidProjectStructureConfigurable.getInstance(defaultProject);
    ShowSettingsUtil.getInstance().editConfigurable(defaultProject, OptionsEditorDialog.DIMENSION_KEY, configurable, true);
  }
}
