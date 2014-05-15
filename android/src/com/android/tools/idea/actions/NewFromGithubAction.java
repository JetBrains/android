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

import com.android.tools.idea.wizard.NewFromGithubWizard;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Action to create a new project from a github repository
 */
public class NewFromGithubAction extends AnAction {

  public NewFromGithubAction() {
    super("From GitHub", "Create a new object from a GitHub repository", AndroidIcons.Wizards.GithubIcon);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = getTemplatePresentation();
    final DataContext dataContext = e.getDataContext();
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    presentation.setVisible(facet != null);
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    // Ensure we're running inside an open project
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Module module = LangDataKeys.MODULE.getData(dataContext);

    if (module == null) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return;
    }

    VirtualFile targetFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);

    NewFromGithubWizard dialog = new NewFromGithubWizard(module.getProject(), module, targetFile);

    dialog.show();
  }
}
