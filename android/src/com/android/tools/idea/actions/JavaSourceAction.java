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

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;

/**
 * An action that is only visible within the context of a Java source directory.
 */
public abstract class JavaSourceAction extends AnAction {
  public JavaSourceAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  protected static boolean isAvailable(DataContext dataContext) {
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
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
          dirService.getPackage(dir) != null &&
          !dirService.getPackage(dir).getQualifiedName().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(isAvailable(e.getDataContext()));
  }

  @Override
  public abstract void actionPerformed(@NotNull AnActionEvent e);
}
