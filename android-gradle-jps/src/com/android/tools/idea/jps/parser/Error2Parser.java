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

class Error2Parser extends ProblemParser {
  /**
   * First and second line of dual-line aapt error.
   * <pre>
   * ERROR: &lt;error&gt;
   * Defined at file &lt;path&gt; line &lt;line&gt;
   * </pre>
   */
  private static final Pattern MSG_LINE_1_PATTERN = Pattern.compile("^ERROR:\\s+(.+)$");

  private static final Pattern MSG_LINE_2_PATTERN = Pattern.compile("Defined\\s+at\\s+file\\s+(.+)\\s+line\\s+(\\d+)");

  @NotNull private final ProblemMessageFactory myMessageFactory;

  Error2Parser(@NotNull AaptProblemMessageFactory messageFactory) {
    myMessageFactory = messageFactory;
  }

  @NotNull
  @Override
  ParsingResult parse(@NotNull String line) {
    Matcher m = MSG_LINE_1_PATTERN.matcher(line);
    if (!m.matches()) {
      return ParsingResult.NO_MATCH;
    }
    String msgText = m.group(1);
    m = getNextLineMatcher(myMessageFactory.getOutputReader(), MSG_LINE_2_PATTERN);
    if (m == null) {
      return ParsingResult.FAILED;
    }
    String sourcePath = m.group(1);
    String lineNumber = m.group(2);
    CompilerMessage msg = myMessageFactory.createErrorMessage(msgText, sourcePath, lineNumber);
    return new ParsingResult(msg);
  }
}
