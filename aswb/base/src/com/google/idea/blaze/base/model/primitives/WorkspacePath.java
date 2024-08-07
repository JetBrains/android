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
package com.google.idea.blaze.base.model.primitives;

import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Represents a path relative to the workspace root. The path component separator is Blaze specific.
 *
 * <p>A {@link WorkspacePath} is *not* necessarily a valid package name/path. The primary reason is
 * because it could represent a file and files don't have to follow the same conventions as package
 * names.
 */
@Immutable
public class WorkspacePath implements ProtoWrapper<String>, Serializable {
  // still Serializable as part of ProjectViewSet
  public static final long serialVersionUID = 1L;

  /** Silently returns null if this is not a valid workspace path. */
  @Nullable
  public static WorkspacePath createIfValid(String relativePath) {
    return isValid(relativePath) ? new WorkspacePath(relativePath) : null;
  }

  private static final char BLAZE_COMPONENT_SEPARATOR = '/';

  private final String relativePath;

  /**
   * @param relativePath relative path that must use the Blaze specific separator char to separate
   *     path components
   * @throws IllegalArgumentException if the path is invalid
   */
  public WorkspacePath(String relativePath) {
    String error = validate(relativePath);
    if (error != null) {
      throw new IllegalArgumentException(
          String.format("Invalid workspace path '%s': %s", relativePath, error));
    }
    this.relativePath = relativePath;
  }

  public WorkspacePath(WorkspacePath parentPath, String childPath) {
    this(
        parentPath.isWorkspaceRoot()
            ? childPath
            : parentPath.relativePath() + BLAZE_COMPONENT_SEPARATOR + childPath);
  }

  public static boolean isValid(String relativePath) {
    return validate(relativePath) == null;
  }

  /** Validates a workspace path. Returns null on success or an error message otherwise. */
  @Nullable
  public static String validate(String relativePath) {
    if (relativePath.startsWith("/")) {
      return "Workspace path must be relative; cannot start with '/': " + relativePath;
    }
    if (relativePath.startsWith("../")) {
      return "Workspace path must be inside the workspace; cannot start with '../': "
          + relativePath;
    }
    if (relativePath.endsWith("/")) {
      return "Workspace path may not end with '/': " + relativePath;
    }

    if (relativePath.indexOf(':') >= 0) {
      return "Workspace path may not contain ':': " + relativePath;
    }

    return null;
  }

  /**
   * Returns the workspace path of this path's parent directory. Returns null if this is the
   * workspace root.
   */
  @Nullable
  public WorkspacePath getParent() {
    if (isWorkspaceRoot()) {
      return null;
    }
    int lastSeparatorIndex = relativePath.lastIndexOf('/');
    String parentPath =
        lastSeparatorIndex == -1 ? "" : relativePath.substring(0, lastSeparatorIndex);
    return new WorkspacePath(parentPath);
  }

  /** Returns this workspace path, relative to the workspace root. */
  public Path asPath() {
    return Paths.get(relativePath);
  }

  public boolean isWorkspaceRoot() {
    return relativePath.isEmpty() || relativePath.equals(".");
  }

  @Override
  public String toString() {
    return relativePath;
  }

  public String relativePath() {
    return relativePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }

    WorkspacePath that = (WorkspacePath) o;
    return relativePath.equals(that.relativePath);
  }

  @Override
  public int hashCode() {
    return relativePath.hashCode();
  }

  public static WorkspacePath fromProto(String proto) {
    return new WorkspacePath(proto);
  }

  @Override
  public String toProto() {
    return relativePath;
  }
}
