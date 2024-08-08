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

import com.google.common.base.Objects;
import com.google.idea.blaze.base.ideinfo.ProjectDataInterner;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import javax.annotation.Nullable;

/**
 * An absolute or relative path returned from Blaze. If it is a relative path, it is relative to the
 * execution root.
 */
public final class ExecutionRootPath implements ProtoWrapper<String> {
  private final File path;

  public ExecutionRootPath(String path) {
    this.path = new File(path);
  }

  public ExecutionRootPath(File path) {
    this.path = path;
  }

  public File getAbsoluteOrRelativeFile() {
    return path;
  }

  public boolean isAbsolute() {
    return path.isAbsolute();
  }

  public File getFileRootedAt(File absoluteRoot) {
    if (path.isAbsolute()) {
      return path;
    }
    return new File(absoluteRoot, path.getPath());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExecutionRootPath that = (ExecutionRootPath) o;
    return Objects.equal(path, that.path);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(path);
  }

  @Override
  public String toString() {
    return "ExecutionRootPath{" + "path='" + path + '\'' + '}';
  }

  /**
   * Returns the relative {@link ExecutionRootPath} if {@code root} is an ancestor of {@code path}
   * otherwise returns null.
   */
  @Nullable
  public static ExecutionRootPath createAncestorRelativePath(File root, File path) {
    // We cannot find the relative path between an absolute and relative path.
    // The underlying code will make the relative path absolute
    // by rooting it at the current working directory which is almost never what you want.
    if (root.isAbsolute() != path.isAbsolute()) {
      return null;
    }
    if (!isAncestor(root.getPath(), path.getPath(), /* strict= */ false)) {
      return null;
    }
    String relativePath =
        FileUtil.getRelativePath(
            root.getAbsolutePath(), path.getAbsolutePath(), File.separatorChar);
    if (relativePath == null) {
      return null;
    }
    return ProjectDataInterner.intern(new ExecutionRootPath(new File(relativePath)));
  }

  /**
   * @param possibleParent
   * @param possibleChild
   * @param strict if {@code false} then this method returns {@code true} if {@code possibleParent}
   *     equals to {@code possibleChild}.
   */
  public static boolean isAncestor(
      ExecutionRootPath possibleParent, ExecutionRootPath possibleChild, boolean strict) {
    return isAncestor(
        possibleParent.getAbsoluteOrRelativeFile().getPath(),
        possibleChild.getAbsoluteOrRelativeFile().getPath(),
        strict);
  }

  /**
   * @param possibleParentPath
   * @param possibleChild
   * @param strict if {@code false} then this method returns {@code true} if {@code possibleParent}
   *     equals to {@code possibleChild}.
   */
  public static boolean isAncestor(
      String possibleParentPath, ExecutionRootPath possibleChild, boolean strict) {
    return isAncestor(
        possibleParentPath, possibleChild.getAbsoluteOrRelativeFile().getPath(), strict);
  }

  /**
   * @param possibleParent
   * @param possibleChildPath
   * @param strict if {@code false} then this method returns {@code true} if {@code possibleParent}
   *     equals to {@code possibleChild}.
   */
  public static boolean isAncestor(
      ExecutionRootPath possibleParent, String possibleChildPath, boolean strict) {
    return isAncestor(
        possibleParent.getAbsoluteOrRelativeFile().getPath(), possibleChildPath, strict);
  }

  /**
   * @param possibleParentPath
   * @param possibleChildPath
   * @param strict if {@code false} then this method returns {@code true} if {@code possibleParent}
   *     equals to {@code possibleChild}.
   */
  public static boolean isAncestor(
      String possibleParentPath, String possibleChildPath, boolean strict) {
    return FileUtil.isAncestor(possibleParentPath, possibleChildPath, strict);
  }

  public static ExecutionRootPath fromProto(String proto) {
    return ProjectDataInterner.intern(new ExecutionRootPath(proto));
  }

  @Override
  public String toProto() {
    return path.getPath();
  }
}
