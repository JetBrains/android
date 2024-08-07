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
package com.google.idea.blaze.base.lang.buildfile.validation;

import com.google.common.base.Splitter;
import javax.annotation.Nullable;

/**
 * Support for resolving globs.
 */
public class GlobPatternValidator {

  /**
   * Validate a single glob pattern. If it's invalid, returns an error message. Otherwise, returns
   * null.
   */
  @Nullable
  public static String validate(String pattern) {
    String error = checkPatternForError(pattern);
    if (error != null) {
      return "Invalid glob pattern: " + error;
    }
    return null;
  }

  @Nullable
  private static String checkPatternForError(String pattern) {
    if (pattern.isEmpty()) {
      return "pattern cannot be empty";
    }
    if (pattern.charAt(0) == '/') {
      return "pattern cannot be absolute";
    }
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      switch (c) {
        case '(':
        case ')':
        case '{':
        case '}':
        case '[':
        case ']':
          return "illegal character '" + c + "'";
        default: // fall out
      }
    }
    Iterable<String> segments = Splitter.on('/').split(pattern);
    for (String segment : segments) {
      if (segment.isEmpty()) {
        return "empty segment not permitted";
      }
      if (segment.equals(".") || segment.equals("..")) {
        return "segment '" + segment + "' not permitted";
      }
      if (segment.contains("**") && !segment.equals("**")) {
        return "recursive wildcard must be its own segment";
      }
    }
    return null;
  }
}
