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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link AndroidOutputReceiver} which receives output from logcat and processes each line,
 * searching for callstacks and reformatting the final output before it is printed to the
 * logcat console.
 *
 * This class expects the logcat format to be 'logcat -v long' (which prints out a header and then
 * 1+ lines of log text below, for each log message).
 */
public final class AndroidLogcatReceiver extends AndroidOutputReceiver implements Disposable {

  // Pattern for logcat -v long header ([ MM-DD HH:MM:SS.mmm PID:TID LEVEL/TAG ])
  // Ex: [ 08-18 16:39:11.760  2977: 2988 D/PhoneInterfaceManager ]
  // Group 1: Date + Time
  // Group 2: PID
  // Group 3: TID (hex on some systems!)
  // Group 4: Log Level character
  // Group 5: Tag
  private static Pattern HEADER_PATTERN =
    Pattern.compile("^\\[\\s(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s+(\\d+):\\s*(\\S+)\\s([VDIWEAF])/(.*)\\s\\]$",
                    Pattern.DOTALL);

  /** Prefix to use for stack trace lines. */
  public static final String STACK_TRACE_LINE_PREFIX = StringUtil.repeatSymbol(' ', 4);

  /** Prefix to use for the stack trace "Caused by:" lines. */
  public static final String STACK_TRACE_CAUSE_LINE_PREFIX = Character.toString(' ');

  private volatile boolean myCanceled = false;
  private final AndroidConsoleWriter myWriter;
  private final IDevice myDevice;

  private final StackTraceExpander myStackTraceExpander;
  @Nullable private LogMessageHeader myActiveHeader;

  public AndroidLogcatReceiver(@NotNull IDevice device, @NotNull AndroidConsoleWriter writer) {
    this(device, writer, new StackTraceExpander(STACK_TRACE_LINE_PREFIX, STACK_TRACE_CAUSE_LINE_PREFIX));
  }

  @VisibleForTesting
  AndroidLogcatReceiver(@NotNull IDevice device, @NotNull AndroidConsoleWriter writer, StackTraceExpander expander) {
    myDevice = device;
    myWriter = writer;
    myStackTraceExpander = expander;
  }

  @Override
  public void processNewLine(@NotNull String line) {
    if (line.isEmpty()) {
      myStackTraceExpander.reset();
      myActiveHeader = null;
      return;
    }

    Matcher matcher = HEADER_PATTERN.matcher(line);
    if (matcher.matches()) {
      myActiveHeader = new LogMessageHeader();
      myActiveHeader.myTime = matcher.group(1);
      myActiveHeader.myPid = Integer.valueOf(matcher.group(2));

      String tid = matcher.group(3);
      long tidValue;
      try {
        // Thread id's may be in hex on some platforms.
        // Decode and store them in radix 10.
        tidValue = Long.decode(tid);
      }
      catch (NumberFormatException e) {
        tidValue = -1;
      }
      myActiveHeader.myTid = Long.toString(tidValue);

      myActiveHeader.myAppPackage = myDevice.getClientName(myActiveHeader.myPid);
      myActiveHeader.myLogLevel = getByLetterString(matcher.group(4));

      // For parsing later, tags should not have spaces in them. Replace spaces with
      // "no break" spaces, which looks like whitespace but doesn't act like it.
      myActiveHeader.myTag = matcher.group(5).trim().replace(' ', '\u00A0');
    }
    else if (myActiveHeader != null) {
      for (String processedLine : myStackTraceExpander.process(line.trim())) {
        processedLine = getFullMessage(myActiveHeader, processedLine);
        myWriter.addMessage(processedLine);
      }
    }
  }

  @Nullable
  private static Log.LogLevel getByLetterString(@Nullable String s) {
    if (s == null) {
      return null;
    }
    final Log.LogLevel logLevel = Log.LogLevel.getByLetterString(s);

    /* LogLevel doesn't support messages with severity "F". Log.wtf() is supposed
     * to generate "A", but generates "F" */
    if (logLevel == null && s.equals("F")) {
      return Log.LogLevel.ASSERT;
    }
    return logLevel;
  }

  @Override
  public boolean isCancelled() {
    return myCanceled;
  }

  @Override
  public void dispose() {
    cancel();
  }

  static final class LogMessageHeader {
    String myTime;
    Log.LogLevel myLogLevel;
    int myPid;
    String myTid;
    String myAppPackage;
    String myTag;
  }

  private static String getFullMessage(LogMessageHeader header, String message) {
    return AndroidLogcatFormatter.formatMessage(header, message);
  }

  public void cancel() {
    myCanceled = true;
  }
}
