/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.stacktrace;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.profilers.stacktrace.CodeLocation.INVALID_LINE_NUMBER;

/**
 * Class which wraps a single stack frame in a Java stack trace.
 *
 * E.g. "a.b.FooClass.someFunc(FooClass.java:123
 */
public final class StackFrameParser {
  @NotNull private final String myLine;

  public StackFrameParser(@NotNull String line) {
    myLine = line;
  }

  @Nullable
  public String getClassName() {
    int lastDot = getLastDot();
    if (lastDot == -1) {
      return null;
    }
    return myLine.substring(0, lastDot);
  }

  @Nullable
  public String getFileName() {
    int start = getOpenParen();
    int end = getLastColon();
    if (start == -1 || start >= end) {
      return null;
    }
    return myLine.substring(start + 1, end);
  }

  @Nullable
  public String getMethodName() {
    int start = getLastDot();
    int end = getOpenParen();
    if (start == -1 || start >= end) {
      return null;
    }
    return myLine.substring(start + 1, end);
  }

  public int getLineNumber() {
    int start = getLastColon();
    int end = getCloseParen();
    if (start >= end || start == -1) {
      return INVALID_LINE_NUMBER;
    }

    try {
      return Integer.parseInt(myLine.substring(start + 1, end));
    }
    catch (Exception e) {
      return INVALID_LINE_NUMBER;
    }
  }

  public int getLastColon() {
    return myLine.lastIndexOf(':');
  }

  public int getLastDot() {
    return myLine.lastIndexOf('.', getOpenParen());
  }

  public int getOpenParen() {
    return myLine.indexOf('(');
  }

  public int getCloseParen() {
    return myLine.indexOf(')');
  }

  @NotNull
  public CodeLocation toCodeLocation() {
    String className = getClassName();
    if (className == null) {
      throw new IllegalStateException("Trying to create CodeLocation from an incomplete StackFrameParser");
    }

    return new CodeLocation.Builder(className).
      setFileName(getFileName()).
      setMethodName(getMethodName()).
      setLineNumber(getLineNumber() - 1).build();
  }
}
