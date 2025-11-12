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

import com.android.tools.idea.profilers.capture.unified.UnifiedProfilerEditorProvider;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
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
  public Icon getIcon() {
    return StudioIcons.Profiler.Sessions.ALLOCATIONS;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    return null;
  }

  @Override
  public boolean openFileInAssociatedApplication(Project project,
                                                 @NotNull VirtualFile file) {
    return openFileInAssociatedApplication(project, file,
                                           getLegacyProvider(),
                                           getUnifiedProvider(),
                                           FileEditorManager.getInstance(project));
  }

  @VisibleForTesting
  protected boolean openFileInAssociatedApplication(Project project,
                                          @NotNull VirtualFile file,
                                          FileEditorProvider legacyProvider,
                                          FileEditorProvider unifiedProvider,
                                          FileEditorManager fileEditorManager) {
    if (unifiedProvider.accept(project, file)) {
      fileEditorManager.openFile(file, true);
      return true;
    }
    else if (legacyProvider.accept(project, file)) {
      legacyProvider.createEditor(project, file);
      return true;
    }
    return false;
  }

  @VisibleForTesting
  @NotNull
  FileEditorProvider getLegacyProvider() {
    return new AndroidProfilerCaptureEditorProvider();
  }

  @VisibleForTesting
  @NotNull
  FileEditorProvider getUnifiedProvider() {
    return new UnifiedProfilerEditorProvider();
  }


  @Override
  public boolean useNativeIcon() {
    return false;
  }
}
