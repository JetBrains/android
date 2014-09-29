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

import com.android.tools.idea.editors.navigation.NavigationEditorProvider;
import com.android.tools.idea.editors.navigation.Utilities;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.Nullable;

public class AndroidShowNavigationEditor extends AnAction {
  public AndroidShowNavigationEditor() {
    super("Navigation Editor", null, AndroidIcons.NavigationEditor);
  }

  public void showNavigationEditor(@Nullable Project project, final String dir, final String file) {
    if (project == null) {
      return;
    }
    final VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) { // this happens when we have the 'default' project, we can't launch nav editor here
      return;
    }
    VirtualFile navFile = Utilities.getNavigationFile(baseDir, dir, file);
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, navFile, 0);
    FileEditorManager manager = FileEditorManager.getInstance(project);
    manager.openEditor(descriptor, true);
    manager.setSelectedEditor(navFile, NavigationEditorProvider.ID);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    showNavigationEditor(project, "raw", "main.nvg.xml");
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(true);
  }
}
