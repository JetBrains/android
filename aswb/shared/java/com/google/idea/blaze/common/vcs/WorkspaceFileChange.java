/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common.vcs;

import java.nio.file.Path;
import java.util.Objects;

/** Represents an edit to a file in the user's workspace. */
public final class WorkspaceFileChange {

  /** Type of change that affected the file. */
  public enum Operation {
    DELETE,
    ADD,
    MODIFY,
  }

  public final Operation operation;
  public final Path workspaceRelativePath;

  public WorkspaceFileChange(Operation operation, Path workspaceRelativePath) {
    this.operation = operation;
    this.workspaceRelativePath = workspaceRelativePath;
  }

  /**
   * Invert this change. For an add, returns a corresponding delete, and for a delete returns a
   * corresponding add. For a modify, return this.
   *
   * <p>This is used when performing delta updates to correctly handle files that have been
   * reverted, i.e. are no longer in the working set.
   */
  public WorkspaceFileChange invert() {
    switch (operation) {
      case DELETE:
        return new WorkspaceFileChange(Operation.ADD, workspaceRelativePath);
      case ADD:
        return new WorkspaceFileChange(Operation.DELETE, workspaceRelativePath);
      case MODIFY:
        return this;
    }
    throw new IllegalStateException(
        "Invalid operation " + operation + " for " + workspaceRelativePath);
  }

  @Override
  public String toString() {
    return "WorkspaceFileChange{" + operation + ' ' + workspaceRelativePath + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WorkspaceFileChange)) {
      return false;
    }
    WorkspaceFileChange that = (WorkspaceFileChange) o;
    return operation == that.operation && workspaceRelativePath.equals(that.workspaceRelativePath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(operation, workspaceRelativePath);
  }
}
