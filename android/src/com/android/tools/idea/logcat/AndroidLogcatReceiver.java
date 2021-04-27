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
import com.android.ddmlib.logcat.LogCatLongEpochMessageParser;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatMessageParser;
import com.android.tools.idea.logcat.AndroidLogcatService.LogcatListener;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link AndroidOutputReceiver} which receives output from logcat and processes each line,
 * searching for callstacks and reformatting the final output before it is printed to the
 * logcat console.
 *
 * <p>This class expects the logcat format to be 'logcat -v long' (which prints out a header and then
 * 1+ lines of log text below, for each log message).
 */
public final class AndroidLogcatReceiver extends AndroidOutputReceiver implements Disposable {
  /**
   * Prefix to use for stack trace lines.
   */
  private static final String STACK_TRACE_LINE_PREFIX = StringUtil.repeatSymbol(' ', 4);

  /**
   * Prefix to use for the stack trace "Caused by:" lines.
   */
  private static final String STACK_TRACE_CAUSE_LINE_PREFIX = Character.toString(' ');

  private static final Pattern CARRIAGE_RETURN = Pattern.compile("\r", Pattern.LITERAL);

  private final LogCatMessageParser myLongEpochParser;
  private final LogCatMessageParser myLongParser;
  private final IDevice myDevice;
  private final StackTraceExpander myStackTraceExpander;
  private final LogcatListener myLogcatListener;
  private volatile boolean myCanceled;

  // Save the last header of the batch in case a log entry was split into 2 batches.
  @Nullable private LogCatHeader myPreviousHeader;
  @Nullable private List<@NotNull String> myPreviousLines;

  AndroidLogcatReceiver(@NotNull IDevice device, @NotNull LogcatListener listener) {
    myLongEpochParser = new LogCatLongEpochMessageParser();
    myLongParser = new LogCatMessageParser();
    myDevice = device;
    myStackTraceExpander = new StackTraceExpander(STACK_TRACE_LINE_PREFIX, STACK_TRACE_CAUSE_LINE_PREFIX);
    myLogcatListener = listener;
  }

  /**
   * Parse a batch of new lines.
   * <p>
   * Lines are expected to arrive in the following structure:
   * <pre>
   *   [ header 1 ]
   *   Line 1
   *   Line 2
   *   ...
   *
   *   [ header 2]
   *   Line 1
   *   Line 2
   *   ...
   * </pre>
   * <p>
   * However, there are 2 special cases that need to be handled:
   * <ol>
   * <li>The log will (always?) start with a "--------- beginning of ..." line which doesn't have a header. We need to be able to ignore
   * this line.
   * <li>A log entry can be split into 2 batches so we need to keep a state field of the last processed header.
   * </ol>
   * <p>
   * Handing case #1 seems simple. We can simply ignore lines that arrive before we have a valid header.
   * <p>
   * Handling case #2 is harder. We need to keep 2 state fields. One field will keep the last header of a batch and a second field will keep
   * any log lines from an incomplete log entry from the last batch.
   * <p>
   * An incomplete log entry is detected by the lack of an empty line at the end.
   * <p>
   * This leaves a rare edge case where a user emits an empty line to the log AND that empty lines happens to fall exactly between batches.
   * <p>
   * It's very hard to handle this case. The previous implementation of this code handled it by pushing the responsibility to downstream
   * code and requiring it to keep its own state. Since the downstream code was not thread safe, this resulted in state contamination and
   * caused crashes.
   * <p>
   * As a Compromise, this code will split a log entry that has a user emitted empty line into 2 log entries, but only if the empty line
   * happens to fall exactly at the end of a batch, which should be extremely rare.
   * <p>
   * Note that we can't just hold on the last log entry and handle it on the next batch because there may not be a next batch for a while.
   * <p>
   * Possible ways to handle this edge case:
   * <ul>
   * <li>Post a delayed task that would flush the saved lines.
   * <li>Replace the last log entry in {@link LogConsoleBase#getOriginalDocument()} with an updated one if the header is the same. This
   * would require adding a unique id to the header.
   * </ul>
   */
  @Override
  protected void processNewLines(@NotNull List<@NotNull String> newLines) {
    LogCatHeader activeHeader = null;
    List<String> activeLines = new ArrayList<>();
    for (String line : newLines) {
      line = fixLine(line);

      LogCatHeader header = tryParseHeader(line);

      if (header != null) {
        // It's a header, flush active lines.
        myStackTraceExpander.reset();
        if (!activeLines.isEmpty()) {
          notifyLogcatMessage(activeHeader, activeLines);
          activeLines.clear();
        }
        activeHeader = header;

        // Save for next batch.
        myPreviousHeader = header;
      }
      else {
        // It's a message line. Collect it if we have an active header
        if (activeHeader == null) {
          if (myPreviousHeader == null) {
            // This should only happen once when the log starts and the line should start with "-------"
            // TODO(aalbert): Should we check if the the line conforms? Should we throw or log if not?
            continue;
          }
          activeHeader = myPreviousHeader;
          if (myPreviousLines != null) {
            activeLines.addAll(myPreviousLines);
            myPreviousLines = null;
          }
        }
        activeLines.addAll(myStackTraceExpander.process(line));
      }
    }
    if (!activeLines.isEmpty()) {
      int size = activeLines.size();
      if (size >= 2 && activeLines.get(size - 1).isEmpty()) {
        // If theres at least 2 lines and the last line is empty, this is probably a complete log entry so send it.
        notifyLogcatMessage(activeHeader, activeLines);
      }
      else {
        // Otherwise, it's a partial entry so save it for next batch which will arrive shortly.
        myPreviousLines = activeLines;
      }
    }
  }

  // This method is package protected so other Logcat components can feed receiver processed log lines if they need to
  void notifyLogcatMessage(@NotNull LogCatHeader header, @NotNull String msg) {
    myLogcatListener.onLogLineReceived(new LogCatMessage(header, msg));
  }

  private void notifyLogcatMessage(@NotNull LogCatHeader header, @NotNull List<@NotNull String> messageLines) {
    String message = StringUtil.trim(String.join("\n", messageLines), ch -> ch != '\n');
    notifyLogcatMessage(header, message);
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

  @Nullable
  private LogCatHeader tryParseHeader(@NotNull String line) {
    LogCatHeader header = myLongEpochParser.processLogHeader(line, myDevice);

    if (header == null) {
      header = myLongParser.processLogHeader(line, myDevice);
    }
    return header;
  }

  /**
   * Really, the user's log should never put any system characters in it ever - that will cause
   * it to get filtered by our strict regex patterns (see AndroidLogcatFormatter). The reason
   * this might happen in practice is due to a bug where either adb or logcat (not sure which)
   * is too aggressive about converting \n's to \r\n's, including those that are quoted. This
   * means that a user's log, if it uses \r\n itself, is converted to \r\r\n. Then, when
   * MultiLineReceiver, which expects valid input, strips out \r\n, it leaves behind an extra \r.
   * <p>
   * Unfortunately this isn't a case where we can fix the root cause because adb and logcat are
   * both external to Android Studio. In fact, the latest adb/logcat versions have already fixed
   * this issue! But we still need to run properly with older versions. Also, putting this fix in
   * MultiLineReceiver isn't right either because it is used for more than just receiving logcat.
   */
  @NotNull
  private static String fixLine(@NotNull String line) {
    return CARRIAGE_RETURN.matcher(line).replaceAll("");
  }
}
