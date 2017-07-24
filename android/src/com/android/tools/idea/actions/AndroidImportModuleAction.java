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

import com.android.tools.idea.npw.importing.SourceToGradleModuleModel;
import com.android.tools.idea.npw.importing.SourceToGradleModuleStep;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * Action for importing existing sources as an Android project modules.
 */
public class AndroidImportModuleAction extends AnAction implements DumbAware {
  public AndroidImportModuleAction() {
    super("Import Module...");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      new StudioWizardDialogBuilder(new SourceToGradleModuleStep(new SourceToGradleModuleModel(project)), "Import module from source")
        .build().show();
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null);
  }
}
