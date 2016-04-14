/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class CaptureType {
  @NotNull
  abstract public String getName();

  @NotNull
  abstract public Icon getIcon();

  public abstract boolean isValidCapture(@NotNull VirtualFile file);

  /**
   * getCaptureExtension returns the extension used for this capture type.
   * The extension should include the '.' prefix.
   */
  @NotNull
  public abstract String getCaptureExtension();

  @NotNull
  protected abstract Capture createCapture(@NotNull VirtualFile file);

  @NotNull
  public abstract FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file);

  public boolean accept(@NotNull VirtualFile file) {
    return true;
  }
}
