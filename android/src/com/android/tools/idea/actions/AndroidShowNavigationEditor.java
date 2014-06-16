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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class AndroidShowNavigationEditor extends AnAction {
  public AndroidShowNavigationEditor() {
    super("Navigation Editor", null, AndroidIcons.NavigationEditor);
  }

  private static VirtualFile mkDirs(VirtualFile dir, String path) throws IOException {
    for(String dirName : path.split("/")) {
      VirtualFile existingDir = dir.findFileByRelativePath(dirName);
      dir = (existingDir != null) ? existingDir : dir.createChildDirectory(null, dirName);
    }
    return dir;
  }

  public void showNavigationEditor(@Nullable Project project, final String dir, final String file) {
    if (project == null) {
      return;
    }
    final VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) { // this happens when we have the 'default' project, we can't launch nav editor here
      return;
    }
    final String relativePathOfNavDir = ".navigation" + "/" + dir;
    VirtualFile navFile = baseDir.findFileByRelativePath(relativePathOfNavDir + "/" + file);
    if (navFile == null) {
      navFile = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
        @Override
        public VirtualFile compute() {
          try {
            VirtualFile dir = mkDirs(baseDir, relativePathOfNavDir);
            return dir.createChildData(null, file);
          }
          catch (IOException e) {
            assert false;
            return null;
          }

        }
      });
    }
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
