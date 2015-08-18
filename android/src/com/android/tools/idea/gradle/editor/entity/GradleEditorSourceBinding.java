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
package com.android.tools.idea.gradle.editor.entity;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Intellij uses {@link OpenFileDescriptor} as a handle to particular file location, i.e. it holds information about
 * {@link OpenFileDescriptor#getFile() target file} and {@link OpenFileDescriptor#getRangeMarker() location}.
 * <p/>
 * Enhanced gradle editor model used it as is but intellij v.14 changed it's contract in a way that {@link OpenFileDescriptor}
 * holds not a region but just target offset. However, we still create exact value ranges during enhanced editor model
 * building and it might be useful, e.g. for automatically selecting target text on navigation.
 * <p/>
 * That's why we introduced this utility class which holds file+range and allows to delegate to {@link OpenFileDescriptor} when necessary.
 */
public class GradleEditorSourceBinding implements Disposable {

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myFile;
  @NotNull private final RangeMarker myRangeMarker;

  public GradleEditorSourceBinding(@NotNull Project project, @NotNull VirtualFile file, @NotNull RangeMarker rangeMarker) {
    myProject = project;
    myFile = file;
    myRangeMarker = rangeMarker;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public RangeMarker getRangeMarker() {
    return myRangeMarker;
  }

  @Override
  public void dispose() {
    myRangeMarker.dispose();
  }

  @Override
  public String toString() {
    if (myRangeMarker.isValid()) {
      return String.format("%s: [%d;%d)", myFile.getName(), myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset());
    }
    else {
      return "<disposed>";
    }
  }
}
