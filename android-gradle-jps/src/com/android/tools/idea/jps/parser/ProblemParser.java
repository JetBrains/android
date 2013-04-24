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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a single line of Gradle output.
 */
abstract class ProblemParser {
  @NotNull
  abstract ParsingResult parse(@NotNull String line);

  @Nullable
  final Matcher getNextLineMatcher(@NotNull LineReader lineReader, @NotNull Pattern pattern) {
    // unless we can't, because we reached the last line
    String line = lineReader.readLine();
    if (line == null) {
      // we expected a 2nd line, so we flag as error and we bail
      return null;
    }
    Matcher m = pattern.matcher(line);
    return m.matches() ? m : null;
  }

  static class ParsingResult {
    static final ParsingResult IGNORE = new ParsingResult(true, true, null);
    static final ParsingResult FAILED = new ParsingResult(null);
    static final ParsingResult NO_MATCH = new ParsingResult(false, false, null);
    final boolean matched;
    final boolean ignored;
    @Nullable final CompilerMessage message;

    ParsingResult(@Nullable CompilerMessage message) {
      this(true, false, message);
    }

    private ParsingResult(boolean matched, boolean ignored, @Nullable CompilerMessage message) {
      this.matched = matched;
      this.ignored = ignored;
      this.message = message;
    }
  }
}
