/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.importmodel;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Action to import machine learning model to Android project
 */
public class AndroidImportMlModelAction extends AnAction {
  public AndroidImportMlModelAction() {
    super("TFLite Model", null, StudioIcons.Shell.Filetree.ANDROID_FILE);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String title = "Import TFLite model";
    ModelWizard wizard =
      new ModelWizard.Builder().addStep(new ChooseMlModelStep(new MlModel(e.getProject()), title)).build();

    new StudioWizardDialogBuilder(wizard, title).build().show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());

    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    if (AndroidFacet.getInstance(module) == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabledAndVisible(true);
  }
}
