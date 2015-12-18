/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.logcat;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessageParser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link AndroidOutputReceiver} which receives output from logcat and processes each line,
 * searching for callstacks and reformatting the final output before it is printed to the
 * logcat console.
 *
 * This class expects the logcat format to be 'logcat -v long' (which prints out a header and then
 * 1+ lines of log text below, for each log message).
 */
public final class AndroidLogcatReceiver extends AndroidOutputReceiver implements Disposable {

  private final LogCatMessageParser myParser = new LogCatMessageParser();

  /** Prefix to use for stack trace lines. */
  private static final String STACK_TRACE_LINE_PREFIX = StringUtil.repeatSymbol(' ', 4);

  /** Prefix to use for the stack trace "Caused by:" lines. */
  private static final String STACK_TRACE_CAUSE_LINE_PREFIX = Character.toString(' ');

  private volatile boolean myCanceled = false;
  private final AndroidConsoleWriter myWriter;
  private final IDevice myDevice;

  private final StackTraceExpander myStackTraceExpander;
  @Nullable private LogCatHeader myActiveHeader;
  private int myLineIndex;

  public AndroidLogcatReceiver(@NotNull IDevice device, @NotNull AndroidConsoleWriter writer) {
    myDevice = device;
    myWriter = writer;
    myStackTraceExpander = new StackTraceExpander(STACK_TRACE_LINE_PREFIX, STACK_TRACE_CAUSE_LINE_PREFIX);
  }

  @Override
  public void processNewLine(@NotNull String line) {
    if (line.isEmpty()) {
      myStackTraceExpander.reset();
      myActiveHeader = null;
      return;
    }

    LogCatHeader header = myParser.processLogHeader(line, myDevice);
    if (header != null) {
      myActiveHeader = header;
      myLineIndex = 0;
    }
    else if (myActiveHeader != null) {
      myStackTraceExpander.process(line);
      for (String processedLine : myStackTraceExpander.getProcessedLines()) {
        if (myLineIndex == 0) {
          processedLine = AndroidLogcatFormatter.formatMessageFull(myActiveHeader, processedLine);
        }
        else {
          processedLine = AndroidLogcatFormatter.formatContinuation(processedLine);
        }
        myWriter.addMessage(processedLine);
        myLineIndex++;
      }
    }
  }

  @Override
  public boolean isCancelled() {
    return myCanceled;
  }

  @Override
  public void dispose() {
    cancel();
  }

  public void cancel() {
    myCanceled = true;
  }
}
