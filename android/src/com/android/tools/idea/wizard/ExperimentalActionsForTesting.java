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
package com.android.tools.idea.wizard;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class ExperimentalActionsForTesting {
  public static class ClearPrefsAction extends AnAction {

    public ClearPrefsAction() {
      super("Clear Saved Values...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      PropertiesComponent properties = PropertiesComponent.getInstance();
      properties.unsetValue(FormFactorUtils.getPropertiesComponentMinSdkKey(FormFactorUtils.FormFactor.MOBILE));
      properties.unsetValue(FormFactorUtils.getPropertiesComponentMinSdkKey(FormFactorUtils.FormFactor.GLASS));
      properties.unsetValue(FormFactorUtils.getPropertiesComponentMinSdkKey(FormFactorUtils.FormFactor.TV));
      properties.unsetValue(FormFactorUtils.getPropertiesComponentMinSdkKey(FormFactorUtils.FormFactor.WEAR));
      properties.unsetValue(ConfigureAndroidProjectStep.SAVED_COMPANY_DOMAIN);
    }
  }

  public static class NewNewModuleWizardAction extends AnAction {
    public NewNewModuleWizardAction() {
      super("Test New Module Wizard");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
      NewModuleWizardDynamic wizard = new NewModuleWizardDynamic(project, null);
      wizard.init();
      wizard.show();
    }


  }
}

