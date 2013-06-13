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

package com.android.tools.idea.editors;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

public class GeneratedFileNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.generated.file.ro");
  private final Project myProject;

  public GeneratedFileNotificationProvider(Project project) {
    myProject = project;
  }

  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(VirtualFile file, FileEditor fileEditor) {
    final Module module = ModuleUtilCore.findModuleForFile(file, myProject);
    if (module == null) {
      return null;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    if (!facet.isGradleProject()) {
      return null;
    }

    // TODO: Look up build folder via Gradle project metadata.
    if (!file.getPath().contains("build")) { // fast fail
      return null;
    }
    for (VirtualFile baseDir : ModuleRootManager.getInstance(module).getContentRoots()) {
      VirtualFile build = baseDir.findChild("build");
      if (build != null && VfsUtilCore.isAncestor(build, file, true)) {
        EditorNotificationPanel panel = new EditorNotificationPanel();
        panel.setText("Files under the build folder are generated and should not be edited.");
        return panel;
      }
    }

    return null;
  }
}
