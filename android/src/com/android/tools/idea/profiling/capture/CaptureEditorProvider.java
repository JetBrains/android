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
package com.android.tools.idea.profiling.capture;

import com.android.tools.idea.stats.UsageTracker;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CaptureEditorProvider implements FileEditorProvider, DumbAware {
  @NonNls private static final String ID = "capture-editor";

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    CaptureType type = CaptureTypeService.getInstance().getTypeFor(file);
    return type != null && type.accept(file);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    CaptureType type = CaptureTypeService.getInstance().getTypeFor(file);
    if (type == null) {
      throw new IllegalStateException("Type has been removed between accept and createEditor");
    }

    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_PROFILING, UsageTracker.ACTION_PROFILING_OPEN, type.getName(), null);

    return type.createEditor(project, file);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
