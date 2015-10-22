/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL_PREFIX;
import static com.intellij.openapi.vfs.VfsUtilCore.pathToUrl;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static com.intellij.util.io.URLUtil.JAR_SEPARATOR;

public final class FilePaths {
  private FilePaths() {
  }

  @Nullable
  public static ContentEntry findParentContentEntry(@NotNull File path, @NotNull ContentEntry[] contentEntries) {
    for (ContentEntry contentEntry : contentEntries) {
      if (isPathInContentEntry(path, contentEntry)) {
        return contentEntry;
      }
    }
    return null;
  }

  @Nullable
  public static ContentEntry findParentContentEntry(@NotNull File path, @NotNull Collection<ContentEntry> contentEntries) {
    for (ContentEntry contentEntry : contentEntries) {
      if (isPathInContentEntry(path, contentEntry)) {
        return contentEntry;
      }
    }
    return null;
  }

  public static boolean isPathInContentEntry(@NotNull File path, @NotNull ContentEntry contentEntry) {
    VirtualFile rootFile = contentEntry.getFile();
    File rootFilePath;
    if (rootFile == null) {
      String s = urlToPath(contentEntry.getUrl());
      rootFilePath = new File(s);
    }
    else {
      rootFilePath = VfsUtilCore.virtualToIoFile(rootFile);
    }
    return isAncestor(rootFilePath, path, false);
  }

  /**
   * Converts the given path to an URL. The underlying implementation "cheats": it doesn't encode spaces and it just adds the "file"
   * protocol at the beginning of this path. We use this method when creating URLs for file paths that will be included in a module's
   * content root, because converting a URL back to a path expects the path to be constructed the way this method does. To obtain a real
   * URL from a file path, use {@link com.android.utils.SdkUtils#fileToUrl(File)}.
   *
   * @param path the given path.
   * @return the created URL.
   */
  @NotNull
  public static String pathToIdeaUrl(@NotNull File path) {
    return pathToUrl(toSystemIndependentName(path.getPath()));
  }

  @Nullable
  public static File getJarFromJarUrl(@NotNull String url) {
    // URLs for jar file start with "jar://" and end with "!/".
    if (!url.startsWith(JAR_PROTOCOL_PREFIX)) {
      return null;
    }
    String path = url.substring(JAR_PROTOCOL_PREFIX.length());
    int index = path.lastIndexOf(JAR_SEPARATOR);
    if (index != -1) {
      path = path.substring(0, index);
    }
    return new File(toSystemDependentName(path));
  }
}
