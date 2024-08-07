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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.common.base.Objects;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import java.io.Serializable;

/** An entry in the directory section. */
public class DirectoryEntry implements Serializable {
  private static final long serialVersionUID = 1L;
  public final WorkspacePath directory;
  public final boolean included;

  private DirectoryEntry(WorkspacePath directory, boolean included) {
    this.directory = directory;
    this.included = included;
  }

  public static DirectoryEntry include(WorkspacePath directory) {
    return new DirectoryEntry(directory, /* included= */ true);
  }

  public static DirectoryEntry exclude(WorkspacePath directory) {
    return new DirectoryEntry(directory, /* included= */ false);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DirectoryEntry that = (DirectoryEntry) o;
    return Objects.equal(included, that.included) && Objects.equal(directory, that.directory);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(directory, included);
  }

  @Override
  public String toString() {
    return (included ? "" : "-") + directoryString();
  }

  private String directoryString() {
    if (directory.isWorkspaceRoot()) {
      return ".";
    }
    return directory.relativePath();
  }
}
