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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

public class OpenFileHyperlink extends NotificationHyperlink {
  @NotNull private final String myFilePath;
  private final int myLineNumber;
  private final int myColumn;

  public OpenFileHyperlink(@NotNull final String filePath) {
    this(filePath, -1);
  }

  /**
   * Creates a file hyperlink. The line number should be 0-based. The file path should be a file system dependent path.
   */
  public OpenFileHyperlink(@NotNull String filePath, int lineNumber) {
    this(filePath, "Open File", lineNumber, -1);
  }

  /**
   * Creates a file hyperlink. The line and column numbers should be 0-based. The file path should be a file system dependent path.
   */
  public OpenFileHyperlink(@NotNull String filePath, @NotNull String text, int lineNumber, int column) {
    super("openFile:" + filePath, text);
    myFilePath = FileUtil.toSystemIndependentName(filePath);
    myLineNumber = lineNumber;
    myColumn = column;
  }

  @Override
  protected void execute(@NotNull Project project) {
    VirtualFile projectFile = project.getProjectFile();
    if (projectFile == null) {
      // This is the default project. This will NEVER happen.
      return;
    }
    VirtualFile file = projectFile.getParent().getFileSystem().findFileByPath(myFilePath);
    if (file != null) {
      Navigatable openFile = new OpenFileDescriptor(project, file, myLineNumber, myColumn, false);
      if (openFile.canNavigate()) {
        openFile.navigate(true);
      }
    }
  }

  @VisibleForTesting
  @NotNull
  public String getFilePath() {
    return myFilePath;
  }

  @VisibleForTesting
  public int getLineNumber() {
    return myLineNumber;
  }
}
