/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import javax.annotation.Nullable;

/** Validates package paths in blaze labels / target expressions. */
public class PackagePathValidator {

  /** Matches characters allowed in package name. */
  private static final CharMatcher ALLOWED_CHARACTERS_IN_PACKAGE_NAME =
      CharMatcher.inRange('0', '9')
          .or(CharMatcher.inRange('a', 'z'))
          .or(CharMatcher.inRange('A', 'Z'))
          .or(CharMatcher.anyOf("/-._ $()"))
          .precomputed();

  @VisibleForTesting
  static final String PACKAGE_NAME_ERROR =
      "package names may contain only A-Z, a-z, 0-9, '/', '-', '.', ' ', '$', '(', ')' and '_'";

  @VisibleForTesting
  static final String PACKAGE_NAME_DOT_ERROR =
      "package name component contains only '.' characters";

  /**
   * Performs validity checking of the specified package name. Returns null on success or an error
   * message otherwise.
   */
  @Nullable
  static String validatePackageName(String packageName) {
    int len = packageName.length();
    if (len == 0) {
      // Empty package name (//:foo).
      return null;
    }

    if (packageName.charAt(0) == '/') {
      return wrapError(packageName, "package names may not start with '/'");
    }

    if (!ALLOWED_CHARACTERS_IN_PACKAGE_NAME.matchesAllOf(packageName)) {
      return wrapError(packageName, PACKAGE_NAME_ERROR);
    }

    if (packageName.charAt(len - 1) == '/') {
      return wrapError(packageName, "package names may not end with '/'");
    }
    // Check for empty or dot-only package segment
    boolean nonDot = false;
    boolean lastSlash = true;
    // Going backward and marking the last character as being a / so we detect
    // '.' only package segment.
    for (int i = len - 1; i >= -1; --i) {
      char c = (i >= 0) ? packageName.charAt(i) : '/';
      if (c == '/') {
        if (lastSlash) {
          return wrapError(packageName, "package names may not contain '//' path separators");
        }
        if (!nonDot) {
          return wrapError(packageName, PACKAGE_NAME_DOT_ERROR);
        }
        nonDot = false;
        lastSlash = true;
      } else {
        if (c != '.') {
          nonDot = true;
        }
        lastSlash = false;
      }
    }
    return null;
  }

  private static String wrapError(String packageName, String error) {
    return String.format("Invalid package name '%s': %s", packageName, error);
  }
}
