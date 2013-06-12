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
package com.android.tools.idea.jps.output.parser.androidPlugin;

import com.android.tools.idea.jps.AndroidGradleJps;
import com.android.tools.idea.jps.output.parser.CompilerOutputParser;
import com.android.tools.idea.jps.output.parser.OutputLineReader;
import com.android.tools.idea.jps.output.parser.ParsingFailedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for Gradle's final error message on failed builds. It is of the form:
 *
 *
   FAILURE: Build failed with an exception.

   * What went wrong:
   Execution failed for task 'TASK_PATH'.

   * Where:
   Build file 'PATHNAME' line: LINE_NUM
   > ERROR_MESSAGE

   * Try:
   Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.

   BUILD FAILED

 * The Where section may not appear (it usually only shows up if there's a problem in the build.gradle file itself). We parse this
 * out to get the failure message and module, and the where output if it appears.
 */
public class GradleBuildFailureParser implements CompilerOutputParser {
  private static final Pattern[] BEGINNING_PATTERNS = {
    Pattern.compile("^FAILURE: Build failed with an exception."),
    Pattern.compile("^\\* What went wrong:")
  };

  private static final Pattern WHERE_LINE_1 = Pattern.compile("^\\* Where:");
  private static final Pattern WHERE_LINE_2 = Pattern.compile("^Build file '(.+)' line: (\\d+)");

  private static final Pattern[] ENDING_PATTERNS = {
    Pattern.compile("^\\* Try:"),
    Pattern.compile("^Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.")
  };

  private enum State {
    BEGINNING,
    WHERE,
    MESSAGE,
    ENDING
  }

  State myState;

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull Collection<CompilerMessage> messages)
    throws ParsingFailedException {
    myState = State.BEGINNING;
    int pos = 0;
    String currentLine = line;
    String file = null;
    int lineNum = 0;
    StringBuilder errorMessage = new StringBuilder();
    while (true) {
      switch(myState) {
        case BEGINNING:
          if (WHERE_LINE_1.matcher(currentLine).matches()) {
            myState = State.WHERE;
          } else if (!BEGINNING_PATTERNS[pos].matcher(currentLine).matches()) {
            return false;
          } else if (++pos >= BEGINNING_PATTERNS.length) {
            myState = State.MESSAGE;
          }
          break;
        case WHERE:
          Matcher matcher = WHERE_LINE_2.matcher(currentLine);
          if (!matcher.matches()) {
            return false;
          }
          file = matcher.group(1);
          lineNum = Integer.parseInt(matcher.group(2));
          myState = State.BEGINNING;
          break;
        case MESSAGE:
          if (ENDING_PATTERNS[0].matcher(currentLine).matches()) {
            myState = State.ENDING;
            pos = 1;
          } else {
            if (errorMessage.length() > 0) {
              errorMessage.append("\n");
            }
            errorMessage.append(currentLine);
          }
          break;
        case ENDING:
          if (!ENDING_PATTERNS[pos].matcher(currentLine).matches()) {
            return false;
          } else if (++pos >= ENDING_PATTERNS.length) {
            if (file != null) {
              messages.add(AndroidGradleJps.createCompilerMessage(BuildMessage.Kind.ERROR, errorMessage.toString(), file, lineNum, 0));
            } else {
              messages.add(AndroidGradleJps.createCompilerMessage(BuildMessage.Kind.ERROR, errorMessage.toString()));
            }
            return true;
          }
          break;
      }
      while (true) {
        currentLine = reader.readLine();
        if (currentLine == null) {
          return false;
        }
        if (!currentLine.trim().isEmpty()) {
          break;
        }
      }
    }
  }
}
