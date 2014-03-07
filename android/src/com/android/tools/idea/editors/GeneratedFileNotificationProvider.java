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
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.customizer.AbstractContentRootModuleCustomizer.BUILD_DIR;
import static org.jetbrains.android.facet.ResourceFolderManager.EXPLODED_AAR;
import static org.jetbrains.android.facet.ResourceFolderManager.EXPLODED_BUNDLES;

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
    if (!file.getPath().contains(BUILD_DIR)) { // fast fail
      return null;
    }
    for (VirtualFile baseDir : ModuleRootManager.getInstance(module).getContentRoots()) {
      VirtualFile build = baseDir.findChild(BUILD_DIR);
      if (build != null && VfsUtilCore.isAncestor(build, file, true)) {
        VirtualFile explodedBundled = build.findChild(EXPLODED_BUNDLES);
        if (explodedBundled == null) {
          // 0.8.2+
          explodedBundled = build.findChild(EXPLODED_AAR);
        }
        boolean inAar = explodedBundled != null && VfsUtilCore.isAncestor(explodedBundled, file, true);
        String text;
        if (inAar) {
          text = "Resource files inside Android library archive files (.aar) should not be edited";
        } else {
          text = "Files under the build folder are generated and should not be edited.";
        }

        EditorNotificationPanel panel = new EditorNotificationPanel();
        panel.setText(text);
        return panel;
      }
    }

    return null;
  }
}
