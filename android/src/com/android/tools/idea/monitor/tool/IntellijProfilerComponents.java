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
package com.android.tools.idea.monitor.tool;

import com.android.tools.profilers.IdeProfilerComponents;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class IntellijProfilerComponents implements IdeProfilerComponents {

  @Nullable
  private Project myProject;

  public IntellijProfilerComponents(@Nullable Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public JComponent getFileViewer(@Nullable File file) {
    VirtualFile virtualFile = file != null ? LocalFileSystem.getInstance().findFileByIoFile(file) : null;
    return getFileViewer(virtualFile, FileEditorProviderManager.getInstance(), myProject);
  }

  @Nullable
  @VisibleForTesting
  static JComponent getFileViewer(@Nullable VirtualFile virtualFile,
                                  @NotNull FileEditorProviderManager fileEditorProviderManager,
                                  @Nullable Project project) {
    if (project != null && virtualFile != null) {
      // TODO: Investigate providers are empty when file download is not finished.
      FileEditorProvider editorProvider = ArrayUtil.getFirstElement(fileEditorProviderManager.getProviders(project, virtualFile));
      return editorProvider != null ? editorProvider.createEditor(project, virtualFile).getComponent() : null;
    }
    return null;
  }
}
