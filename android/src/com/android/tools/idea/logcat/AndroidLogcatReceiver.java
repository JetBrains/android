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
import com.android.ddmlib.logcat.LogCatMessage;
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
final class AndroidLogcatReceiver extends AndroidOutputReceiver implements Disposable {

  private final LogCatMessageParser myParser = new LogCatMessageParser();

  /** Prefix to use for stack trace lines. */
  private static final String STACK_TRACE_LINE_PREFIX = StringUtil.repeatSymbol(' ', 4);

  /** Prefix to use for the stack trace "Caused by:" lines. */
  private static final String STACK_TRACE_CAUSE_LINE_PREFIX = Character.toString(' ');

  private volatile boolean myCanceled = false;
  private final AndroidLogcatService.LogLineListener myLogLineListener;
  private final IDevice myDevice;

  /**
   * We don't always want to add a newline when we get one, as we can't tell if it came from the
   * user or from logcat. We'll flush any enqueued newlines to the log if we get more context that
   * verifies the newlines came from a user.
   */
  private int myDelayedNewlineCount;
  private final StackTraceExpander myStackTraceExpander;
  @Nullable private LogCatHeader myActiveHeader;
  private int myLineIndex;

  public AndroidLogcatReceiver(@NotNull IDevice device, @NotNull AndroidLogcatService.LogLineListener logLineListener) {
    myDevice = device;
    myLogLineListener = logLineListener;
    myStackTraceExpander = new StackTraceExpander(STACK_TRACE_LINE_PREFIX, STACK_TRACE_CAUSE_LINE_PREFIX);
  }

  @Override
  public void processNewLine(@NotNull String line) {
    // Really, the user's log should never put any system characters in it ever - that will cause
    // it to get filtered by our strict regex patterns (see AndroidLogcatFormatter). The reason
    // this might happen in practice is due to a bug where either adb or logcat (not sure which)
    // is too aggressive about converting \n's to \r\n's, including those that are quoted. This
    // means that a user's log, if it uses \r\n itself, is converted to \r\r\n. Then, when
    // MultiLineReceiver, which expects valid input, strips out \r\n, it leaves behind an extra \r.
    //
    // Unfortunately this isn't a case where we can fix the root cause because adb and logcat are
    // both external to Android Studio. In fact, the latest adb/logcat versions have already fixed
    // this issue! But we still need to run properly with older versions. Also, putting this fix in
    // MultilineReceiver isn't right either because it is used for more than just receiving logcat.
    line = line.replaceAll("\\r", "");

    if (line.isEmpty()) {
      myDelayedNewlineCount++;
      return;
    }

    LogCatHeader header = myParser.processLogHeader(line, myDevice);
    if (header != null) {
      myStackTraceExpander.reset();
      myActiveHeader = header;
      myLineIndex = 0;
      // Intentionally drop any trailing newlines once we hit a new header. Usually, logcat
      // separates log entries with a single newline but sometimes it outputs more than one. As we
      // can't know which is user newlines vs. system newlines, just drop all of them.
      myDelayedNewlineCount = 0;
    }
    else if (myActiveHeader != null) {
      if (myDelayedNewlineCount > 0 && myLineIndex == 0) {
        // Note: Since we trim trailing newlines, we trim leading newlines too. Most users won't
        // use them intentionally and they don't look great, anyway.
        myDelayedNewlineCount = 0;
      }
      else {
        processAnyDelayedNewlines(myActiveHeader);
      }
      for (String processedLine : myStackTraceExpander.process(line)) {
        notifyLine(myActiveHeader, processedLine);
      }
    }
  }

  private void notifyLine(@NotNull LogCatHeader header, @NotNull String line) {
    myLogLineListener.receiveLogLine(new LogCatMessage(header, line));
    myLineIndex++;
  }

  private void processAnyDelayedNewlines(@NotNull LogCatHeader header) {
    if (myDelayedNewlineCount == 0) {
      return;
    }
    for (int i = 0; i < myDelayedNewlineCount; i++) {
      notifyLine(header, "");
    }
    myDelayedNewlineCount = 0;
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
