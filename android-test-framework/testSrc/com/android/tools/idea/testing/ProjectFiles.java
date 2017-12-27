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
package com.android.tools.idea.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

public final class ProjectFiles {
  private ProjectFiles() {
  }

  @NotNull
  public static VirtualFile createFolderInProjectRoot(@NotNull Project project, @NotNull String folderName) throws IOException {
    return createFolder(project.getBaseDir(), folderName);
  }

  @NotNull
  public static VirtualFile createFolder(@NotNull VirtualFile parent, @NotNull String folderName) throws IOException {
    VirtualFile folder = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return parent.createChildDirectory(this, folderName);
      }
    });
    assertNotNull(folder);
    return folder;
  }

  @NotNull
  public static VirtualFile createFileInProjectRoot(@NotNull Project project, @NotNull String fileName) throws IOException {
    VirtualFile parent = project.getBaseDir();
    return createFile(parent, fileName);
  }

  @NotNull
  public static VirtualFile createFile(@NotNull VirtualFile parent, @NotNull String fileName) throws IOException {
    VirtualFile file = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return parent.createChildData(this, fileName);
      }
    });
    assertNotNull(file);
    return file;
  }
}
