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

import java.util.regex.Pattern;

/**
 * Reads text line by line.
 */
class LineReader {
  private static final Pattern LINE_BREAK = Pattern.compile("\\r?\\n");

  @NotNull private final String[] myLines;

  private final int myLineCount;
  private int myPosition;

  /**
   * Creates a new {@link LineReader}.
   *
   * @param text the text to read.
   */
  LineReader(@NotNull String text) {
    myLines = LINE_BREAK.split(text);
    myLineCount = myLines.length;
  }

  int getLineCount() {
    return myLineCount;
  }

  /**
   * Reads the next line of text, moving the line pointer to the next one.
   *
   * @return the contents of the next line, or {@code null} if we reached the end of the text.
   */
  @Nullable
  String readLine() {
    if (myPosition >= 0 && myPosition < myLineCount) {
      return myLines[myPosition++];
    }
    return null;
  }

  /**
   * Reads the text of one the line at the given position, without moving the line pointer.
   *
   * @param lineToSkipCount the number of lines to skip from the line pointer.
   * @return the contents of the specified line, or {@code null} if the specified position is greater than the end of the text.
   */
  @Nullable
  String peek(int lineToSkipCount) {
    int tempPosition = lineToSkipCount + myPosition;
    if (tempPosition >= 0 && tempPosition < myLineCount) {
      return myLines[tempPosition];
    }
    return null;
  }

  boolean hasNextLine() {
    return myPosition < myLineCount - 1;
  }

  void skipNextLine() {
    myPosition++;
  }
}
