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

import com.intellij.execution.actions.EditRunConfigurationsAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.TemplateProjectPropertiesAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;

/**
 * "Project Defaults" group displayed in the "Welcome" page.
 */
public class AndroidTemplateProjectSettingsGroup extends DefaultActionGroup {
  public AndroidTemplateProjectSettingsGroup() {
    setPopup(true);
    Presentation presentation = getTemplatePresentation();

    presentation.setText("Project Defaults");
    presentation.setIcon(AllIcons.General.TemplateProjectSettings);

    add(new AndroidTemplateSettingsAction());
    add(new AndroidTemplateProjectStructureAction());
    add(new AndroidEditRunConfigurationsAction());
  }

  private static class AndroidTemplateSettingsAction extends TemplateProjectPropertiesAction {
    AndroidTemplateSettingsAction() {
      Presentation p = getTemplatePresentation();
      p.setText("Settings");
      p.setIcon(AllIcons.General.TemplateProjectSettings);
    }
  }

  private static class AndroidEditRunConfigurationsAction extends EditRunConfigurationsAction {
    AndroidEditRunConfigurationsAction() {
      Presentation p = getTemplatePresentation();
      p.setText("Run Configurations");
      p.setIcon(AllIcons.General.CreateNewProjectfromExistingFiles);
    }
  }
}
