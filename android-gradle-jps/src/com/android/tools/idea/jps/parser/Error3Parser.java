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
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Error3Parser extends ProblemParser {
  /**
   * Single-line aapt error
   * <pre>
   * &lt;path&gt; line &lt;line&gt;: &lt;error&gt;
   * </pre>
   */
  private static final Pattern MSG_PATTERN = Pattern.compile("^(.+)\\sline\\s(\\d+):\\s(.+)$");

  @NotNull private final ProblemMessageFactory myMessageFactory;

  Error3Parser(@NotNull AaptProblemMessageFactory messageFactory) {
    myMessageFactory = messageFactory;
  }

  @NotNull
  @Override
  ParsingResult parse(@NotNull String line) {
    Matcher m = MSG_PATTERN.matcher(line);
    if (!m.matches()) {
      return ParsingResult.NO_MATCH;
    }
    String sourcePath = m.group(1);
    String lineNumber = m.group(2);
    String msgText = m.group(3);
    CompilerMessage msg = myMessageFactory.createErrorMessage(msgText, sourcePath, lineNumber);
    return new ParsingResult(msg);
  }
}
