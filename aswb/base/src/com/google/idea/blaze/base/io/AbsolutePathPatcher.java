/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.io;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Extension point for fixing absolute paths.
 *
 * <p>Allows extensions to make system-specific adjustments to absolute paths from process output
 * and canonicalized files.
 */
public interface AbsolutePathPatcher {
  ExtensionPointName<AbsolutePathPatcher> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AbsolutePathPatcher");

  /** Fixes all absolute paths found in a string. */
  String fixAllPaths(String line);

  /** Fixes the given path string, if it is an absolute path. */
  String fixPath(String path);

  /**
   * Helper for fixing absolute paths.
   *
   * <p>Applies all {@link AbsolutePathPatcher} extensions to the input string or file.
   */
  class AbsolutePathPatcherUtil {
    private AbsolutePathPatcherUtil() {}

    /** Fixes all absolute paths found in a string */
    public static String fixAllPaths(String line) {
      for (AbsolutePathPatcher pathPatcher : AbsolutePathPatcher.EP_NAME.getExtensions()) {
        line = pathPatcher.fixAllPaths(line);
      }
      return line;
    }

    /** Fixes the given path string, if it is an absolute path. */
    public static String fixPath(String path) {
      for (AbsolutePathPatcher pathPatcher : AbsolutePathPatcher.EP_NAME.getExtensions()) {
        path = pathPatcher.fixPath(path);
      }
      return path;
    }

    /**
     * Fixes the path of a {@link VirtualFile}.
     *
     * @return a new VirtualFile if the path was changed, optionally refreshing if needed.
     *     Otherwise, returns the input VirtualFile.
     */
    @Nullable
    public static VirtualFile fixPath(@Nullable VirtualFile virtualFile, boolean refreshIfNeeded) {
      if (virtualFile == null) {
        return null;
      }
      String path = virtualFile.getPath();
      String fixedPath = fixPath(path);
      return path.equals(fixedPath)
          ? virtualFile
          : VfsUtils.resolveVirtualFile(new File(fixedPath), refreshIfNeeded);
    }

    /**
     * Fixes the path of a {@link File}.
     *
     * @return a new File if the path was changed; otherwise, returns the input File.
     */
    public static File fixPath(File file) {
      String path = file.getPath();
      String fixedPath = fixPath(path);
      return path.equals(fixedPath) ? file : new File(fixedPath);
    }
  }
}
