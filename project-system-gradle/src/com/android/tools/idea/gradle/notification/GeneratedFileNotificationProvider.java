/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.idea.gradle.notification;

import static com.android.tools.idea.FileEditorUtil.DISABLE_GENERATED_FILE_NOTIFICATION_KEY;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.GeneratedSourceFileChangeTracker;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import java.io.File;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GeneratedFileNotificationProvider implements EditorNotificationProvider {

  @Nullable
  @Override
  public Function<FileEditor, EditorNotificationPanel> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
    GeneratedSourceFileChangeTracker generatedSourceFileChangeTracker = GeneratedSourceFileChangeTracker.getInstance(project);
    GradleAndroidModel androidModel =
      GradleProjectSystemUtil.findAndroidModelInModule(project, file, false /* include excluded files */);
    if (androidModel == null) {
      return null;
    }
    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    VirtualFile buildFolder = findFileByIoFile(buildFolderPath, false /* do not refresh */);
    if (buildFolder == null || !buildFolder.isDirectory()) {
      return null;
    }
    if (!isAncestor(buildFolder, file, false /* not strict */)) {
      return null;
    }
    if (generatedSourceFileChangeTracker.isEditedGeneratedFile(file)) {
      // A warning is already being displayed by GeneratedFileEditingNotificationProvider
      return null;
    }
    return fileEditor -> {
      if (DISABLE_GENERATED_FILE_NOTIFICATION_KEY.get(fileEditor, false)) {
        return null;
      }
      return new MyEditorNotificationPanel(fileEditor, "Files under the \"build\" folder are generated and should not be edited.");
    };
  }

  @VisibleForTesting
  static class MyEditorNotificationPanel extends EditorNotificationPanel {
    MyEditorNotificationPanel(@NotNull FileEditor fileEditor, @NotNull String text) {
      super(fileEditor, Status.Warning);
      setText(text);
    }
  }
}
