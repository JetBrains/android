/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.capture;

import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a binary file that can be imported into in Android Profiler.
 * <p>
 * Implements {@link INativeFileType} to support opening traces from Device File Manager.
 */
public abstract class AndroidProfilerCaptureFileType implements INativeFileType {

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return StudioIcons.Profiler.Sessions.ALLOCATIONS;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, byte[] content) {
    return null;
  }

  @Override
  public boolean openFileInAssociatedApplication(Project project,
                                                 @NotNull VirtualFile file) {
    AndroidProfilerCaptureEditorProvider provider = new AndroidProfilerCaptureEditorProvider();
    if (provider.accept(project, file)) {
      provider.createEditor(project, file);
      return true;
    }
    return false;
  }

  @Override
  public boolean useNativeIcon() {
    return false;
  }
}
