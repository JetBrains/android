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

import com.android.tools.idea.editors.navigation.NavigationEditor;
import com.android.tools.idea.editors.navigation.NavigationEditorProvider;
import com.android.tools.idea.editors.navigation.NavigationEditorUtils;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

public class AndroidShowNavigationEditor extends AnAction {
  public AndroidShowNavigationEditor() {
    super("Navigation Editor", null, AndroidIcons.NavigationEditor);
  }

  public void showNavigationEditor(@Nullable Project project, @Nullable Module module, final String dir, final String file) {
    if (project == null) {
      return;
    }
    if (module == null) {
      return;
    }
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) { // this happens when we have the 'default' project; can't launch nav editor from here
      return;
    }
    VirtualFile navFile = NavigationEditorUtils.getNavigationFile(baseDir, module.getName(), dir, file);
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, navFile, 0);
    FileEditorManager manager = FileEditorManager.getInstance(project);
    manager.openEditor(descriptor, true);
    manager.setSelectedEditor(navFile, NavigationEditorProvider.ID);
  }

  private void showNavigationEditor(@Nullable Project project, String dir, String file) {
    if (project != null) {
      Module[] androidModules = NavigationEditorUtils.getAndroidModules(project);
      if (androidModules.length > 0) {
        showNavigationEditor(project, androidModules[0], dir, file);
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    showNavigationEditor(e.getProject(), NavigationEditor.DEFAULT_RESOURCE_FOLDER, NavigationEditor.NAVIGATION_FILE_NAME);
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(
      project != null && !ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).isEmpty());
  }
}
