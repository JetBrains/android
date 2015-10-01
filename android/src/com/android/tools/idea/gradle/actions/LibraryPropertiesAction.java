/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.library.LibraryPropertiesDialog;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.openapi.actionSystem.CommonDataKeys.NAVIGATABLE_ARRAY;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class LibraryPropertiesAction extends AnAction {
  public LibraryPropertiesAction() {
    super("Library Properties...", null, AllIcons.Actions.Properties);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(findLibrary(e) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Library library = findLibrary(e);
    if (library != null) {
      Project project = e.getProject();
      assert project != null;
      LibraryPropertiesDialog dialog = new LibraryPropertiesDialog(project, library);
      if (dialog.showAndGet()) {
        dialog.applyChanges();
      }
    }
  }

  @Nullable
  private static Library findLibrary(@NotNull AnActionEvent e) {
    if (isAndroidStudio()) {
      Project project = e.getProject();
      if (project != null) {
        NamedLibraryElementNode node = findLibraryNode(e.getDataContext());
        if (node != null) {
          String libraryName = node.getName();
          if (isNotEmpty(libraryName)) {
            LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
            return libraryTable.getLibraryByName(libraryName);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static NamedLibraryElementNode findLibraryNode(@NotNull DataContext dataContext) {
    Navigatable[] navigatables = NAVIGATABLE_ARRAY.getData(dataContext);
    if (navigatables != null && navigatables.length == 1) {
      Navigatable navigatable = navigatables[0];
      if (navigatable instanceof NamedLibraryElementNode) {
        return (NamedLibraryElementNode)navigatable;
      }
    }
    return null;
  }
}
