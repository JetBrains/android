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
  private final String myContinuationPrefix;
  private final String myStackTracePrefix;
  private final String myExpandedStackTracePrefix;
  private final String myCauseLinePrefix;

  public StackTraceExpander(String continuationLinePrefix, String stackTraceLinePrefix, String expandedStackTracePrefix,
                            String stackTraceCauseLinePrefix) {
    myContinuationPrefix = continuationLinePrefix;
    myStackTracePrefix = stackTraceLinePrefix;
    myExpandedStackTracePrefix = expandedStackTracePrefix;
    myCauseLinePrefix = stackTraceCauseLinePrefix;
  }

  /** Regex to match a stack trace line. E.g.: "at com.foo.Class.method(FileName.extension:10)" */
  private static final Pattern EXCEPTION_LINE_PATTERN = Pattern.compile("^at .*(.*)$");

  /** Regex to match an the excluded frames line i.e. line of form "... N more" */
  private static final Pattern ELIDED_LINE_PATTERN = Pattern.compile("^... (\\d+) more$");

  private final List<String> myPreviousStack = new ArrayList<String>();
  private final List<String> myCurrentStack = new ArrayList<String>();

  public String expand(String line) {
    line = line.trim();

    // are we in the middle of a stack trace?
    boolean isInTrace = !myCurrentStack.isEmpty() || !myPreviousStack.isEmpty();

    // is this a stack frame line?
    boolean isStackTrace = isStackFrame(line);

    // most lines are not related to stack traces, quit early in such cases
    if (!isStackTrace && !isInTrace) {
      return myContinuationPrefix + line;
    }

    // if it is a stack frame, then just add to current stack
    if (isStackTrace) {
      myCurrentStack.add(line);
      return myStackTracePrefix + line;
    }

    // Now we know that this is not a stack trace line, but we are in the middle
    // of parsing a stack trace, so it is one of: "Caused By:", "...N more", or the end of the trace

    // if it is a "Caused by:" line, then we move the stack we've seen till now to be the
    // outer stack
    if (isCauseLine(line)) {
      myPreviousStack.clear();
      for (String s : myCurrentStack) {
        myPreviousStack.add(s);
      }
      myCurrentStack.clear();
      return myCauseLinePrefix + line;
    }

    // if it is the "...N more", we replace that line with the last N frames from the outer stack
    int elidedFrameCount = getElidedFrameCount(line);
    if (elidedFrameCount > 0) {
      if (elidedFrameCount <= myPreviousStack.size()) {
      StringBuilder sb = new StringBuilder();

        for (int i = myPreviousStack.size() - elidedFrameCount; i < myPreviousStack.size(); i++) {
          String frame = myPreviousStack.get(i);

          sb.append(myExpandedStackTracePrefix);
          sb.append(frame);

          myCurrentStack.add(frame);

          if (i != myPreviousStack.size() - 1) {
            sb.append('\n');
          }
        }

        return sb.toString();
      } else {
        // something went wrong: we don't actually have the required number of frames in the outer stack
        // in this case, we don't expand the frames
        return myStackTracePrefix + line;
      }
    }

    // otherwise we've reached the end of a stack trace that we don't need to retain anymore
    myCurrentStack.clear();
    myPreviousStack.clear();
    return myContinuationPrefix + line;
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
  static boolean isStackFrame(String line) {
    return EXCEPTION_LINE_PATTERN.matcher(line).matches();
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
  static int getElidedFrameCount(String line) {
    Matcher matcher = ELIDED_LINE_PATTERN.matcher(line);
    return matcher.matches() ? StringUtil.parseInt(matcher.group(1), -1) : -1;
  }

  private static boolean isCauseLine(String line) {
    return line.startsWith("Caused by:");
  }
}
