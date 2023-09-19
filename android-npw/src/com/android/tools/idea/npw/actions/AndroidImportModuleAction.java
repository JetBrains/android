/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.actions;

import com.android.tools.idea.npw.importing.SourceToGradleModuleModel;
import com.android.tools.idea.npw.importing.SourceToGradleModuleStep;
import com.android.tools.idea.npw.model.ProjectSyncInvoker;
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Action for importing existing sources as an Android project modules.
 */
public class AndroidImportModuleAction extends AnAction implements DumbAware {
  public AndroidImportModuleAction() {
    super("Import Module...");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      new StudioWizardDialogBuilder(new SourceToGradleModuleStep(
        new SourceToGradleModuleModel(
          project, new ProjectSyncInvoker.DefaultProjectSyncInvoker())), "Import module from source")
        .build().show();
    }
  }


  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null);
  }
}
