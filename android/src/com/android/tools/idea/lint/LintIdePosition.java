/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Position;

/**
 * Custom position class used in the IDE. Normally we only store offsets.
 * Line numbers and columns are computed lazily.
 */
public abstract class LintIdePosition extends Position {
  /** The character offset */
  private final int myOffset;

  /** The line number (0-based where the first line is line 0) */
  protected int myLine;

  /**
   * The column number (where the first character on the line is 0), or -1 if
   * unknown
   */
  protected int myColumn;

  /** Whether we've attempted to initialize the line and column */
  private boolean myInitialized;

  public LintIdePosition(int offset) {
    myOffset = offset;
  }

  @Override
  public int getOffset() {
    return myOffset;
  }

  @Override
  public int getLine() {
    ensureLineInitialized();
    return myLine;
  }

  @Override
  public int getColumn() {
    ensureLineInitialized();
    return myColumn;
  }

  private void ensureLineInitialized() {
    if (myInitialized) {
      return;
    }
    myInitialized = true;
    initializeLineColumn();
  }

  /** Computes the line and column given the offset and updates the {@link #myLine} and {@link #myColumn} protected members */
  protected abstract void initializeLineColumn();

  protected void initializeFromText(@NonNull String contents) {
    int offset = myOffset;
    myLine = 0;
    offset = Math.min(offset, contents.length());
    int lineOffset = 0;
    char prev = 0;
    for (int currentOffset = 0; currentOffset < offset; currentOffset++) {
      char c = contents.charAt(currentOffset);
      if (c == '\n') {
        lineOffset = currentOffset + 1;
        if (prev != '\r') {
          myLine++;
        }
      }
      else if (c == '\r') {
        myLine++;
        lineOffset = currentOffset + 1;
      }
      prev = c;
    }
    myColumn = offset - lineOffset;
  }
}
