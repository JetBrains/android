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
package com.google.idea.blaze.cpp.includes;

import com.google.auto.value.AutoValue;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.cidr.lang.psi.OCIncludeDirective.Delimiters;

/** Information extracted from a #include directive. */
@AutoValue
abstract class IncludePath {
  abstract String headerPath();

  abstract Delimiters headerDelim();

  static IncludePath create(String headerPath, Delimiters headerDelim) {
    return new AutoValue_IncludePath(headerPath, headerDelim);
  }

  static IncludePath create(String rawHeaderPath) {
    if (rawHeaderPath.startsWith(Delimiters.QUOTES.getBeforeText())
        && rawHeaderPath.endsWith(Delimiters.QUOTES.getAfterText())) {
      return new AutoValue_IncludePath(
          StringUtil.trimEnd(
              StringUtil.trimStart(rawHeaderPath, Delimiters.QUOTES.getBeforeText()),
              Delimiters.QUOTES.getAfterText()),
          Delimiters.QUOTES);
    }
    if (rawHeaderPath.startsWith(Delimiters.ANGLE_BRACKETS.getBeforeText())
        && rawHeaderPath.endsWith(Delimiters.ANGLE_BRACKETS.getAfterText())) {
      return new AutoValue_IncludePath(
          StringUtil.trimEnd(
              StringUtil.trimStart(rawHeaderPath, Delimiters.ANGLE_BRACKETS.getBeforeText()),
              Delimiters.ANGLE_BRACKETS.getAfterText()),
          Delimiters.ANGLE_BRACKETS);
    }
    return new AutoValue_IncludePath(rawHeaderPath, Delimiters.NONE);
  }
}
