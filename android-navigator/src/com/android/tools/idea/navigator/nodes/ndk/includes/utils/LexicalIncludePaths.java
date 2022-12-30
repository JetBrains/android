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
package com.android.tools.idea.navigator.nodes.ndk.includes.utils;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Methods for dealing with file paths in a lexical (string-oriented) manner for display of compact
 * include paths to the user. Requires that paths in strings use *nix separator.
 */
public final class LexicalIncludePaths {
  @NotNull
  public static final ImmutableSet<String> HEADER_FILE_EXTENSIONS = ImmutableSet.of("", "h", "hpp", "hh", "h++", "hxx", "inl", "tcc", "pch");

  @NotNull
  private final static String UNIX_SEPARATOR = "/";

  /**
   * Check whether the given filename has one of the header extensions (including no extension)
   */
  public static boolean hasHeaderExtension(@NotNull String filename) {
    String extension = FilenameUtils.getExtension(filename);
    return HEADER_FILE_EXTENSIONS.contains(extension);
  }

  /**
   * Given paths like:
   * sysroot/a/d
   * sysroot/a/b
   * sysroot/a/b/c
   * return the longest common path on the left:
   * sysroot/a
   *
   * Works with full and relative paths.
   */
  @NotNull
  public static String findCommonParentFolder(@NotNull Collection<String> folders) {
    if (folders.isEmpty()) {
      return "";
    }

    List<String[]> splits = new ArrayList<>();
    int minSize = Integer.MAX_VALUE;
    for (String folder : folders) {
      String[] split = folder.split(UNIX_SEPARATOR);
      splits.add(split);
      minSize = Math.min(minSize, split.length);
    }

    StringBuilder buildUp = null;
    for (int i = 0; i < minSize; ++i) {
      String prior = null;
      for (String[] split : splits) {
        if (prior == null) {
          prior = split[i];
          continue;
        }
        if (!prior.equals(split[i])) {
          return buildUp == null ? "" : buildUp.toString();
        }
      }
      if (buildUp != null) {
        buildUp.append(UNIX_SEPARATOR);
      }
      if (buildUp == null) {
        buildUp = new StringBuilder(prior == null ? "" : prior);
      }
      else {
        buildUp.append(prior);
      }
    }
    return buildUp == null ? "" : buildUp.toString();
  }

  /**
   * Remove leading and trailing UNIX path separator from paths and path segments
   *
   * D:/hello/ -> D:/hello
   * /path/to/file/ -> path/to/file
   * path/to/file/ -> path/to/file
   */
  @NotNull
  public static String trimPathSeparators(@NotNull String path) {
    String result = path;
    if (result.endsWith(UNIX_SEPARATOR)) {
      result = result.substring(0, result.length() - 1);
    }
    if (result.startsWith(UNIX_SEPARATOR)) {
      result = result.substring(1);
    }
    return result;
  }

  /**
   * Match a regex against a file path. The regex is expected to be written of unix forward-slash separator on all platforms.
   * The regex can expect the path to have a trailing separator
   *
   * @param pattern a regex pattern for matching a file path
   * @param folder  the folder
   * @return The regex matcher
   */
  @NotNull
  public static Matcher matchFolderToRegex(@NotNull Pattern pattern, @NotNull File folder) {
    return pattern.matcher(FilenameUtils.separatorsToUnix(folder.getPath()) + UNIX_SEPARATOR);
  }
}
