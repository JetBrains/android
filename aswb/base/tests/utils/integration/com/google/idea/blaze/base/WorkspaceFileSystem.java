/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;

/** Creates test files in the workspace */
public class WorkspaceFileSystem {
  private final WorkspaceRoot workspaceRoot;
  private final TestFileSystem testFileSystem;

  public WorkspaceFileSystem(WorkspaceRoot workspaceRoot, TestFileSystem testFileSystem) {
    this.workspaceRoot = workspaceRoot;
    this.testFileSystem = testFileSystem;
  }

  /** Creates an empty file in the workspace */
  public VirtualFile createFile(WorkspacePath workspacePath) {
    return testFileSystem.createFile(workspaceRoot.fileForPath(workspacePath).getPath());
  }

  /** Creates a file with the specified contents in the workspace */
  public VirtualFile createFile(WorkspacePath workspacePath, String... contentLines) {
    return testFileSystem.createFile(
        workspaceRoot.fileForPath(workspacePath).getPath(), contentLines);
  }

  /** Creates a file with the specified contents in the workspace */
  public VirtualFile createFile(WorkspacePath workspacePath, String contents) {
    return testFileSystem.createFile(workspaceRoot.fileForPath(workspacePath).getPath(), contents);
  }

  /** Creates a directory in the workspace */
  public VirtualFile createDirectory(WorkspacePath workspacePath) {
    return testFileSystem.createDirectory(workspaceRoot.fileForPath(workspacePath).getPath());
  }

  /** Creates an empty psi file in the workspace */
  public PsiFile createPsiFile(WorkspacePath workspacePath) {
    return testFileSystem.createPsiFile(workspaceRoot.fileForPath(workspacePath).getPath());
  }

  /** Creates a psi file with the specified contents in the workspace */
  public PsiFile createPsiFile(WorkspacePath workspacePath, String... contentLines) {
    return testFileSystem.createPsiFile(
        workspaceRoot.fileForPath(workspacePath).getPath(), contentLines);
  }

  /** Creates a psi directory in the workspace */
  public PsiDirectory createPsiDirectory(WorkspacePath workspacePath) {
    return testFileSystem.getPsiDirectory(createDirectory(workspacePath));
  }
}
