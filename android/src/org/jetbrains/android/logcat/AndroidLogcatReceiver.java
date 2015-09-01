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

package org.jetbrains.android.logcat;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.tools.idea.logcat.ExceptionFolding;
import com.android.tools.idea.logcat.StackTraceExpander;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link AndroidOutputReceiver} whose output is matched against an expected logcat pattern and,
 * if matched, further processed.
 */
public final class AndroidLogcatReceiver extends AndroidOutputReceiver {
  private static final Logger LOG = Logger.getInstance(AndroidLogcatReceiver.class);
  private static Pattern LOG_PATTERN =
    Pattern.compile("^\\[\\s(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s+(\\d*):\\s*(\\S+)\\s([VDIWEAF])/(.*)\\]$", Pattern.DOTALL);

  /** Prefix to use for all lines without a header. */
  public static final String CONTINUATION_LINE_PREFIX = StringUtil.repeatSymbol(' ', 4);

  /** Prefix to use for stack trace lines. */
  public static final String STACK_TRACE_LINE_PREFIX = CONTINUATION_LINE_PREFIX + StringUtil.repeatSymbol(' ', 8);

  /**
   * Prefix to use for stack trace lines that were expanded in by {@link StackTraceExpander}.
   * The only reason this is different from {@link #STACK_TRACE_LINE_PREFIX} is that we want {@link ExceptionFolding}
   * class to be able to determine and fold just the expanded lines.
   */
  public static final String EXPANDED_STACK_TRACE_LINE_PREFIX = STACK_TRACE_LINE_PREFIX.replace(' ', '\u00A0');

  /** Prefix to use for the stack trace "Caused by:" lines. */
  public static final String STACK_TRACE_CAUSE_LINE_PREFIX = CONTINUATION_LINE_PREFIX + Character.toString(' ');

  private LogMessageHeader myLastMessageHeader;
  private volatile boolean myCanceled = false;
  private final Writer myWriter;
  private final IDevice myDevice;

  private final StackTraceExpander myStackTraceExpander = new StackTraceExpander(CONTINUATION_LINE_PREFIX,
                                                                                 STACK_TRACE_LINE_PREFIX,
                                                                                 EXPANDED_STACK_TRACE_LINE_PREFIX,
                                                                                 STACK_TRACE_CAUSE_LINE_PREFIX);

  public AndroidLogcatReceiver(IDevice device, Writer writer) {
    myDevice = device;
    myWriter = new PrintWriter(writer);
  }

  @Override
  public void processNewLine(String line) {
    Matcher matcher = LOG_PATTERN.matcher(line);
    if (myLastMessageHeader == null && matcher.matches()) {
      myLastMessageHeader = new LogMessageHeader();
      myLastMessageHeader.myTime = matcher.group(1);
      myLastMessageHeader.myPid = Integer.valueOf(matcher.group(2));

      String tid = matcher.group(3).trim();
      long tidValue;
      try {
        // Thread id's may be in hex on some platforms.
        // Decode and store them in radix 10.
        tidValue = Long.decode(tid.trim());
      } catch (NumberFormatException e) {
        tidValue = -1;
      }
      myLastMessageHeader.myTid = Long.toString(tidValue);

      myLastMessageHeader.myAppPackage =
        myDevice == null ? "" : myDevice.getClientName(myLastMessageHeader.myPid);
      myLastMessageHeader.myLogLevel = getByLetterString(matcher.group(4));
      myLastMessageHeader.myTag = matcher.group(5).trim().replace(" ", "");
    }
    else {
      if (line.length() == 0) return;
      String text;
      if (myLastMessageHeader == null) {
        text = myStackTraceExpander.expand(line);
      } else {
        text = getFullMessage(myLastMessageHeader, line);
      }
      try {
        myWriter.write(text + '\n');
      }
      catch (IOException ignored) {
        LOG.info(ignored);
      }
      myLastMessageHeader = null;
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
