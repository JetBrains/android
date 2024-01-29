/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer;

import com.android.tools.idea.util.CommonAndroidUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class ApkEditorProvider implements FileEditorProvider, DumbAware {
  private static final String ID = "apk-viewer";

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    if (!CommonAndroidUtil.getInstance().isAndroidProject(project)) {
      // b/182906226
      return false;
    }

    return ApkFileSystem.EXTENSIONS.contains(file.getExtension()) &&
           ApkFileSystem.getInstance().getRootByLocal(file) != null;
  }

  @Override
  public boolean acceptRequiresReadAction() {
    return true;
  }

  @Override
  public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    VirtualFile root = ApkFileSystem.getInstance().getRootByLocal(file);
    assert root != null; // see accept above
    return new ApkEditor(project, file, root);
  }

  @Override
  public @NotNull String getEditorTypeId() {
    return ID;
  }

  @Override
  public @NotNull FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }
}
