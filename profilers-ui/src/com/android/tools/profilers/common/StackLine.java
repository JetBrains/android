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
package com.android.tools.profilers.common;

import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.profilers.common.CodeLocation.INVALID_LINE_NUMBER;

public class StackLine {
  @NotNull
  private String myDisplayLine;

  @NotNull
  private CodeLocation myCodeLocation;

  public StackLine(@NotNull String stackString) {
    this(stackString, getClassName(stackString), null, null, getLineNumber(stackString));
  }

  public StackLine(@NotNull String displayLine,
                   @Nullable String className,
                   @Nullable String fileName,
                   @Nullable String methodName,
                   int line) {
    myCodeLocation = new CodeLocation(className, fileName, methodName, line);
    myDisplayLine = displayLine;
  }

  @NotNull
  public String getDisplayLine() {
    return myDisplayLine;
  }

  @NotNull
  public CodeLocation getCodeLocation() {
    return myCodeLocation;
  }

  @VisibleForTesting
  @Nullable
  static String getClassName(@NotNull String line) {
    int lastDot = getLastDot(line);
    if (lastDot == -1) {
      return null;
    }
    String name = line.substring(0, lastDot);
    int dollarIndex = name.indexOf('$');
    return (dollarIndex != -1) ? name.substring(0, dollarIndex) : name;
  }

  @VisibleForTesting
  static int getLineNumber(@NotNull String line) {
    int start = line.lastIndexOf(':');
    int end = getCloseParen(line);
    if (start >= end || start == -1) {
      return INVALID_LINE_NUMBER;
    }

    try {
      return Integer.parseInt(line.substring(start + 1, end)) - 1;
    }
    catch (Exception e) {
      return INVALID_LINE_NUMBER;
    }
  }

  private static int getLastDot(@NotNull String line) {
    return line.lastIndexOf('.', getOpenParen(line));
  }

  private static int getOpenParen(@NotNull String line) {
    return line.indexOf('(');
  }

  private static int getCloseParen(@NotNull String line) {
    return line.indexOf(')');
  }
}
