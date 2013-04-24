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

import com.android.SdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Error5Parser extends ProblemParser {
  /**
   * Single-line aapt error.
   * <pre>
   * &lt;path&gt;:&lt;line&gt;: error: Error: &lt;error&gt;
   * </pre>
   */
  private static final Pattern MSG_PATTERN_1 = Pattern.compile("^(.+?):(\\d+): error: Error:\\s+(.+)$");

  /**
   * Single-line aapt error.
   * <pre>
   * &lt;path&gt;:&lt;line&gt;: error: &lt;error&gt;
   * </pre>
   */
  private static final Pattern MSG_PATTERN_2 = Pattern.compile("^(.+?):(\\d+): error:\\s+(.+)$");

  /**
   * Single-line aapt error.
   * <pre>
   * &lt;path&gt;:&lt;line&gt;: &lt;error&gt;
   * </pre>
   */
  private static final Pattern MSG_PATTERN_3 = Pattern.compile("^(.+?):(\\d+):\\s+(.+)$");

  @NotNull private final ProblemMessageFactory myAaptMessageFactory;
  @NotNull private final ProblemMessageFactory myJavaMessageFactory;

  Error5Parser(@NotNull AaptProblemMessageFactory aaptMessageFactory, @NotNull JavaProblemMessageFactory javaMessageFactory) {
    myAaptMessageFactory = aaptMessageFactory;
    myJavaMessageFactory = javaMessageFactory;
  }

  @NotNull
  @Override
  ParsingResult parse(@NotNull String line) {
    Matcher m = MSG_PATTERN_1.matcher(line);
    if (m.matches()) {
      CompilerMessage msg = createCompilerMessage(m);
      return new ParsingResult(msg);
    }
    m = MSG_PATTERN_2.matcher(line);
    if (m.matches()) {
      CompilerMessage msg = createCompilerMessage(m);
      return new ParsingResult(msg);
    }
    m = MSG_PATTERN_3.matcher(line);
    if (m.matches()) {
      CompilerMessage msg = createCompilerMessage(m);
      return new ParsingResult(msg);
    }
    return ParsingResult.NO_MATCH;
  }

  @Nullable
  private CompilerMessage createCompilerMessage(@NotNull Matcher m) {
    String sourcePath = m.group(1);
    String lineNumber = m.group(2);
    String msgText = m.group(3);
    ProblemMessageFactory factory = myAaptMessageFactory;
    if (sourcePath.endsWith(SdkConstants.DOT_JAVA)) {
      factory = myJavaMessageFactory;
    }
    return factory.createErrorMessage(msgText, sourcePath, lineNumber);
  }
}
