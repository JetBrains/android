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

import com.android.tools.idea.wizard.NewTemplateObjectWizard;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.Set;

import static com.android.tools.idea.templates.Template.CATEGORY_OTHER;

/**
 *
 */
public class NewAndroidComponentAction extends AnAction {

  // The new notification template relies on support for invoking the asset studio. This is not currently supported
  // in AS.
  private static final Set<String> EXCLUDED = ImmutableSet.of("New Notification");

  protected NewAndroidComponentAction() {
    super(AndroidBundle.message("android.new.component.action.title"), AndroidBundle.message("android.new.component.action.description"),
          AndroidIcons.Android);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(isAvailable(e.getDataContext()));
  }

  private static boolean isAvailable(DataContext dataContext) {
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);

    if (module == null ||
        view == null ||
        view.getDirectories().length == 0 ||
        AndroidFacet.getInstance(module) == null) {
      return false;
    }
    final ProjectFileIndex projectIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    final JavaDirectoryService dirService = JavaDirectoryService.getInstance();

    for (PsiDirectory dir : view.getDirectories()) {
      if (projectIndex.isUnderSourceRootOfType(dir.getVirtualFile(), JavaModuleSourceRootTypes.SOURCES) &&
          dirService.getPackage(dir) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Module module = LangDataKeys.MODULE.getData(dataContext);

    if (module == null) return;

    final PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null) return;

    NewTemplateObjectWizard dialog = new NewTemplateObjectWizard(CommonDataKeys.PROJECT.getData(dataContext),
                                                                 LangDataKeys.MODULE.getData(dataContext),
                                                                 CommonDataKeys.VIRTUAL_FILE.getData(dataContext),
                                                                 CATEGORY_OTHER, EXCLUDED);

    dialog.show();
    if (dialog.isOK()) {
      dialog.createTemplateObject();
    }

    /*
    // TODO: Implement the getCreatedElements call for the wizard
    final PsiElement[] createdElements = dialog.getCreatedElements();

    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
    */
  }
}
