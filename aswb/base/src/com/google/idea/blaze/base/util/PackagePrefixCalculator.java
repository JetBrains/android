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
package com.google.idea.blaze.base.util;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;

/** Calculates package prefix from workspace paths. */
public final class PackagePrefixCalculator {

  public static String packagePrefixOf(WorkspacePath workspacePath) {
    return workspacePath.relativePath().substring(getSkipIndex(workspacePath)).replace('/', '.');
  }

  /**
   * Returns true if the given workspace path looks likely to be a source root (e.g. it starts with
   * a common set of directories, such as "src/main/java").
   */
  public static boolean looksLikeSourceRoot(WorkspacePath workspacePath) {
    return getSkipIndex(workspacePath) != 0;
  }

  private static int getSkipIndex(WorkspacePath workspacePath) {
    int skipIndex = 0;

    // For Bazel-style projects.
    skipIndex = skipIndex == 0 ? skip(workspacePath, "java/") : skipIndex;
    skipIndex = skipIndex == 0 ? skip(workspacePath, "javatests/") : skipIndex;

    // For Maven-style projects.
    skipIndex = skipIndex == 0 ? skip(workspacePath, "src/main/java/") : skipIndex;
    skipIndex = skipIndex == 0 ? skip(workspacePath, "src/test/java/") : skipIndex;
    skipIndex = skipIndex == 0 ? skip(workspacePath, "src/main/scala/") : skipIndex;
    skipIndex = skipIndex == 0 ? skip(workspacePath, "src/test/scala/") : skipIndex;
    skipIndex = skipIndex == 0 ? skip(workspacePath, "src/main/kotlin/") : skipIndex;
    skipIndex = skipIndex == 0 ? skip(workspacePath, "src/test/kotlin/") : skipIndex;

    return skipIndex;
  }

  private static int skip(WorkspacePath workspacePath, String skipString) {
    if (workspacePath.relativePath().startsWith(skipString)) {
      return skipString.length();
    }
    return 0;
  }
}
