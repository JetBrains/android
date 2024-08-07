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
package com.google.idea.blaze.base.sync.workspace;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation;
import java.io.Serializable;

/** Computes the working set of files of directories from source control. */
public class WorkingSet implements Serializable {
  private static final long serialVersionUID = 2L;

  public final ImmutableList<WorkspacePath> addedFiles;
  public final ImmutableList<WorkspacePath> modifiedFiles;
  public final ImmutableList<WorkspacePath> deletedFiles;

  public WorkingSet(
      ImmutableList<WorkspacePath> addedFiles,
      ImmutableList<WorkspacePath> modifiedFiles,
      ImmutableList<WorkspacePath> deletedFiles) {
    this.addedFiles = addedFiles;
    this.modifiedFiles = modifiedFiles;
    this.deletedFiles = deletedFiles;
  }

  public boolean isEmpty() {
    return addedFiles.isEmpty() && modifiedFiles.isEmpty() && deletedFiles.isEmpty();
  }

  public ImmutableSet<WorkspaceFileChange> toWorkspaceFileChanges() {
    ImmutableSet.Builder<WorkspaceFileChange> builder = ImmutableSet.builder();
    addedFiles.stream()
        .map(WorkspacePath::asPath)
        .map(path -> new WorkspaceFileChange(Operation.ADD, path))
        .forEach(builder::add);
    modifiedFiles.stream()
        .map(WorkspacePath::asPath)
        .map(path -> new WorkspaceFileChange(Operation.MODIFY, path))
        .forEach(builder::add);
    deletedFiles.stream()
        .map(WorkspacePath::asPath)
        .map(path -> new WorkspaceFileChange(Operation.DELETE, path))
        .forEach(builder::add);
    return builder.build();
  }
}
