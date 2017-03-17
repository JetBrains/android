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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;

import static com.android.SdkConstants.EXT_JAR;
import static com.android.SdkConstants.EXT_ZIP;
import static com.intellij.openapi.util.io.FileUtil.extensionEquals;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.vfs.StandardFileSystems.*;
import static com.intellij.openapi.vfs.VirtualFileManager.constructUrl;
import static com.intellij.util.PathUtil.getParentPath;
import static com.intellij.util.io.URLUtil.JAR_SEPARATOR;

public final class FilePaths {
  private FilePaths() {
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
    String name = path.getName();
    boolean isJarFile = extensionEquals(name, EXT_JAR) || extensionEquals(name, EXT_ZIP);
    // .jar files require an URL with "jar" protocol.
    String protocol = isJarFile ? JAR_PROTOCOL : FILE_PROTOCOL;
    String url = constructUrl(protocol, toSystemIndependentName(path.getPath()));
    if (isJarFile) {
      url += JAR_SEPARATOR;
    }
    return url;
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
    return toSystemDependentPath(path);
  }

  /**
   * Converts the given {@code String} path to a system-dependent path (as {@link File}.)
   */
  @Contract("!null -> !null")
  @Nullable
  public static File toSystemDependentPath(@Nullable String path) {
    return path != null ? new File(toSystemDependentName(path)) : null;
  }

  @NotNull
  public static List<String> computeRootPathsForFiles(@NotNull Stream<String> filePaths) {
    List<String> folderPaths = new ArrayList<>();
    filePaths.forEach(filePath -> {
      String parentPath = getParentPath(filePath);
      folderPaths.add(parentPath);
    });
    return mergePaths(folderPaths);
  }

  /**
   * If both a parent folder and a subfolder exist in {@code paths}, only keep he parent folder, e.g. if both "/a" and "/a/b/c" exists, keep
   * "/a" and get rid of "/a/b/c".
   *
   * TODO not just merge with parent, but also at some degree, merge the roots that have shallow common parents, e.g.
   * /a/b/c, /a/b/d -> /a/b
   */
  @NotNull
  public static List<String> mergePaths(@NotNull Collection<String> paths) {
    if (paths.isEmpty()) {
      return Collections.emptyList();
    }
    NavigableSet<String> sortedPaths = new TreeSet<>(paths);

    List<String> result = new ArrayList<>(paths.size());

    String current = sortedPaths.first();
    for (String folder : sortedPaths) {
      if (!folder.startsWith(current)) {
        result.add(current);
        current = folder;
      }
    }
    result.add(current);
    return result;
  }

}
