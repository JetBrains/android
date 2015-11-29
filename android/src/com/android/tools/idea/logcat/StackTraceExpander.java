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

package com.android.tools.idea.logcat;

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When printing out exceptions, Java collapses frames that match those of the enclosing exception, and just says "... N more".
 * This class parses a sequence of lines from logcat, and maintains a knowledge of the current stack trace.
 * If it ever sees the pattern "... N more", it then tries to see if that can be fully expanded with the correct frames
 * from the enclosing exception. The logcat view then folds these frames back and displays "...N more", except now users can
 * unfold it to view the full trace.
 *
 * @see <a href="http://docs.oracle.com/javase/7/docs/api/java/lang/Throwable.html#printStackTrace%28%29">Description in
 * Throwable.printStackTrace</a>
 */
class StackTraceExpander {

  /** Regex to match a stack trace line. E.g.: "at com.foo.Class.method(FileName.extension:10)" */
  private static final Pattern EXCEPTION_LINE_PATTERN = Pattern.compile("^\\s*(at .*\\(.*\\))$");

  /** Regex to match an the excluded frames line i.e. line of form "... N more" */
  private static final Pattern ELIDED_LINE_PATTERN = Pattern.compile("^\\s*... (\\d+) more$");

  /** Regex to match an outer stack trace line. E.g.: "Caused by: java.io.IOException" */
  private static final Pattern CAUSED_BY_LINE_PATTERN = Pattern.compile("^\\s*(Caused by:.*)$");

  /**
   * Marker to indicate stack trace lines that were originally of the form "... 5 more" but
   * expanded inline. If present, it will be found at the end of the line - this keeps it out of
   * the way, but at the same, the parser can check for it quickly.
   *
   * This is ultimately used by {@link ExceptionFolding} to determine which lines it can fold.
   */
  private static final String EXPANDED_STACK_TRACE_MARKER = "\u00A0";

  @NotNull private final String myStackTracePrefix;
  @NotNull private final String myCauseLinePrefix;

  private List<String> myProcessedLines = new ArrayList<String>();
  private List<String> myCurrentStack = new ArrayList<String>();
  private List<String> myPreviousStack = new ArrayList<String>();

  /**
   * True if we've started parsing lines that match the {@link #EXCEPTION_LINE_PATTERN} and
   * {@link #ELIDED_LINE_PATTERN} patterns and haven't yet reached the end.
   */
  private boolean myIsInTrace;

  public StackTraceExpander(@NotNull String stackTraceLinePrefix, @NotNull String stackTraceCauseLinePrefix) {
    myStackTracePrefix = stackTraceLinePrefix;
    myCauseLinePrefix = stackTraceCauseLinePrefix;

    reset();
  }

  public void reset() {
    myIsInTrace = false;
    myProcessedLines.clear();
    myCurrentStack.clear();
    myPreviousStack.clear();
  }

  public static boolean wasLineExpanded(@NotNull String line) {
    return line.endsWith(EXPANDED_STACK_TRACE_MARKER) && line.contains(" at ");
  }

  /**
   * Given a line of output, detect if it's part of a stack trace and, if so, process it. This
   * allows us to keep track of context about outer exceptions as well as prepend lines with
   * prefix indentation. Lines not part of a stack trace are left unmodified.
   *
   * Every time after calling this method, you should check {@link #getProcessedLines()} for the
   * result of processing this line. This list will be cleared every time you call this method.
   *
   * You should process each line of logcat output through this method and echo the result out to
   * the console.
   *
   * @return one or more processed lines. Note that most of the time, one call results in one line
   * of processed output, but occasionally, in the case of elided lines (e.g. "... 3 more"), one
   * input line is expanded into multiple processed lines.
   */
  @NotNull
  public List<String> process(@NotNull String line) {
    myProcessedLines.clear();

    String stackLine = getStackLine(line);
    if (stackLine != null) {
      handleStackTraceLine(stackLine);
      return myProcessedLines;
    }

    if (!myIsInTrace) {
      // If this line isn't the start of a stack trace, and we aren't currently in a stack trace,
      // then this has to be a normal line. Let's save time by avoiding all later checks.
      handleNormalLine(line);
      return myProcessedLines;
    }

    String causeLine = getCauseLine(line);
    if (causeLine != null) {
      handleCausedByLine(causeLine);
      return myProcessedLines;
    }

    int elidedCount = getElidedFrameCount(line);
    if (elidedCount > 0) {
      handleElidedLine(line, elidedCount);
      return myProcessedLines;
    }

    handleNormalLine(line);
    return myProcessedLines;
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
  @Nullable
  static String getStackLine(@NotNull String line) {
    Matcher matcher = EXCEPTION_LINE_PATTERN.matcher(line);
    return matcher.matches() ? matcher.group(1) : null;
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
  @Nullable
  static String getCauseLine(@NotNull String line) {
    Matcher matcher = CAUSED_BY_LINE_PATTERN.matcher(line);
    return matcher.matches() ? matcher.group(1) : null;
  }

  /**
   * Returns the number of stack trace lines that were collapsed, or a value < 0 if this line
   * doesn't match the elided pattern.
   */
  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
  static int getElidedFrameCount(@NotNull String line) {
    Matcher matcher = ELIDED_LINE_PATTERN.matcher(line);
    return matcher.matches() ? StringUtil.parseInt(matcher.group(1), -1) : -1;
  }

  private void handleNormalLine(@NotNull String line) {
    if (myIsInTrace) {
      myIsInTrace = false;

      myCurrentStack.clear();
      myPreviousStack.clear();
    }

    myProcessedLines.add(line);
  }

  private void handleStackTraceLine(@NotNull String line) {
    if (!myIsInTrace) {
      myIsInTrace = true;
    }

    myCurrentStack.add(line);

    myProcessedLines.add(myStackTracePrefix + line);
  }

  private void handleCausedByLine(@NotNull String line) {
    assert myIsInTrace : String.format("Unexpected line while parsing stack trace: %s", line);

    // if it is a "Caused by:" line, then we're starting a new stack, and our current stack becomes
    // our previous (outer) stack.
    List<String> temp = myPreviousStack;
    myPreviousStack = myCurrentStack;
    myCurrentStack = temp;
    myCurrentStack.clear();

    myProcessedLines.add(myCauseLinePrefix + line);
  }

  private void handleElidedLine(@NotNull String line, int elidedCount) {
    assert myIsInTrace : String.format("Unexpected line while parsing stack trace: %s", line);

    assert elidedCount > 0;

    // if it is the "...N more", we replace that line with the last N frames from the outer stack
    int startIndex = myPreviousStack.size() - elidedCount;
    if (startIndex >= 0) {

      for (int i = 0; i < elidedCount; i++) {
        String frame = myPreviousStack.get(startIndex + i);
        myProcessedLines.add(myStackTracePrefix + frame + EXPANDED_STACK_TRACE_MARKER);
        myCurrentStack.add(frame);
      }
    }
    else {
      // something went wrong: we don't actually have the required number of frames in the outer stack
      // in this case, we don't expand the frames
      myProcessedLines.add(myStackTracePrefix + line);
    }
  }
}
