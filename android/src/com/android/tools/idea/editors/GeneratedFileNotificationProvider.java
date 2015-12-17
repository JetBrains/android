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

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.ide.GeneratedSourceFileChangeTracker;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.AndroidGradleModel.EXPLODED_AAR;
import static com.android.tools.idea.gradle.AndroidGradleModel.EXPLODED_BUNDLES;

public class GeneratedFileNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.generated.file.ro");
  private final Project myProject;
  private final GeneratedSourceFileChangeTracker myGeneratedSourceFileChangeTracker;

  public GeneratedFileNotificationProvider(Project project, GeneratedSourceFileChangeTracker changeTracker) {
    myProject = project;
    myGeneratedSourceFileChangeTracker = changeTracker;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    AndroidProject androidProject = Projects.getAndroidModel(file, myProject);
    if (androidProject == null) {
      return null;
    }
    VirtualFile buildFolder = VfsUtil.findFileByIoFile(androidProject.getBuildFolder(), false);
    if (buildFolder == null || !buildFolder.isDirectory()) {
      return null;
    }
    if (VfsUtilCore.isAncestor(buildFolder, file, false)) {
      if (myGeneratedSourceFileChangeTracker.isEditedGeneratedFile(file)) {
        // A warning is already being displayed by GeneratedFileEditingNotificationProvider
        return null;
      }

      VirtualFile explodedBundled = buildFolder.findChild(EXPLODED_BUNDLES);
      if (explodedBundled == null) {
        // 0.8.2+
        explodedBundled = buildFolder.findChild(EXPLODED_AAR);
      }
      boolean inAar = explodedBundled != null && VfsUtilCore.isAncestor(explodedBundled, file, true);
      String text;
      if (inAar) {
        text = "Resource files inside Android library archive files (.aar) should not be edited";
      }
      else {
        text = "Files under the build folder are generated and should not be edited.";
      }

      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText(text);
      return panel;
    }
    return null;
  }


}
