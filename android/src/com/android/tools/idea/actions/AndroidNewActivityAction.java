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

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.wizard.NewTemplateObjectWizard;
import com.intellij.facet.FacetManager;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;

public class AndroidNewActivityAction extends JavaSourceAction implements DumbAware {

  public AndroidNewActivityAction() {
    super("Activity", "Create a new Android activity", AndroidIcons.Android);
  }

  protected static boolean isAvailable(DataContext dataContext) {
    if (!JavaSourceAction.isAvailable(dataContext)) {
      return false;
    }
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    return module != null && FacetManager.getInstance(module).getFacetByType(AndroidGradleFacet.TYPE_ID) != null;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(isAvailable(e.getDataContext()));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    NewTemplateObjectWizard dialog = new NewTemplateObjectWizard(PlatformDataKeys.PROJECT.getData(e.getDataContext()),
                                                                 LangDataKeys.MODULE.getData(e.getDataContext()), CATEGORY_ACTIVITIES);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    dialog.createTemplateObject();
  }
}
