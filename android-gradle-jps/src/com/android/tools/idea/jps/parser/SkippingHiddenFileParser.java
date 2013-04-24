/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.jps.parser;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SkippingHiddenFileParser extends ProblemParser {
  /**
   * Single-line aapt warning for skipping files.
   * <pre>
   *   (skipping hidden file '&lt;file path&gt;')
   * </pre>
   */
  private static final Pattern MSG_PATTERN = Pattern.compile("^\\s+\\(skipping hidden file\\s'(.*)'\\)$");

  @NotNull
  @Override
  ParsingResult parse(@NotNull String line) {
    Matcher m = MSG_PATTERN.matcher(line);
    if (!m.matches()) {
      return ParsingResult.NO_MATCH;
    }
    // we ignored those (as this is an ignored message from aapt)
    return ParsingResult.IGNORE;
  }
}
