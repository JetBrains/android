/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.api.stacktrace;

import static com.android.tools.idea.codenavigation.CodeLocation.INVALID_LINE_NUMBER;

import com.android.tools.idea.codenavigation.CodeLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Class which wraps a single stack frame in a Java stack trace.
 * <p>
 * E.g. "a.b.FooClass.someFunc(FooClass.java:123
 */
public final class StackFrameParser {
  private StackFrameParser() {
    // No-op
  }

  public static CodeLocation tryParseFrame(String line) {
    String className = getClassName(line);
    if (className == null) {
      return null;
    }

    var builder = new CodeLocation.Builder(className);
    builder.setFileName(getFileName(line));
    builder.setMethodName(getMethodName(line));

    // Make sure we don't do INVALID_LINE_NUMBER - 1 by checking the line number value.
    var lineNumber = getLineNumber(line);
    builder.setLineNumber(lineNumber == INVALID_LINE_NUMBER ? INVALID_LINE_NUMBER : lineNumber - 1);

    return builder.build();
  }

  public static CodeLocation parseFrame(String line) {
    var location = tryParseFrame(line);

    if (location == null) {
      throw new IllegalStateException(String.format(
        "Trying to create CodeLocation from an incomplete StackFrameParser. Line contents: '%s'", line));
    }

    return location;
  }

  @Nullable
  private static String getClassName(String line) {
    int lastDot = getLastDot(line);
    if (lastDot == -1) {
      return null;
    }
    return line.substring(0, lastDot);
  }

  @Nullable
  private static String getFileName(String line) {
    int start = getOpenParen(line);
    int end = getLastColon(line);
    if (start == -1 || start >= end) {
      return null;
    }
    return line.substring(start + 1, end);
  }

  @Nullable
  private static String getMethodName(String line) {
    int start = getLastDot(line);
    int end = getOpenParen(line);
    if (start == -1 || start >= end) {
      return null;
    }
    return line.substring(start + 1, end);
  }

  private static int getLineNumber(String line) {
    int start = getLastColon(line);
    int end = getCloseParen(line);
    if (start >= end || start == -1) {
      return INVALID_LINE_NUMBER;
    }

    try {
      return Integer.parseInt(line.substring(start + 1, end));
    }
    catch (Exception e) {
      return INVALID_LINE_NUMBER;
    }
  }

  private static int getLastColon(String line) {
    return line.lastIndexOf(':');
  }

  private static int getLastDot(String line) {
    return line.lastIndexOf('.', getOpenParen(line));
  }

  private static int getOpenParen(String line) {
    return line.indexOf('(');
  }

  private static int getCloseParen(String line) {
    return line.indexOf(')');
  }
}
