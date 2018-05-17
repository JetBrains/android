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

import static com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatHeaderParser;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.logcat.AndroidLogcatService.LogcatListener;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

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
   * Last message in batch will be posted after a delay, to allow for more log lines if another batch is pending.
   */
  @VisibleForTesting
  static final int DELAY_MILLIS = 100;

  /**
   * Prefix to use for stack trace lines.
   */
  private static final String STACK_TRACE_LINE_PREFIX = StringUtil.repeatSymbol(' ', 4);

  /**
   * Prefix to use for the stack trace "Caused by:" lines.
   */
  private static final String STACK_TRACE_CAUSE_LINE_PREFIX = Character.toString(' ');

  private static final Pattern CARRIAGE_RETURN = Pattern.compile("\r", Pattern.LITERAL);

  @NotNull
  private final LogCatHeaderParser myLogCatHeaderParser;
  @NotNull
  private final IDevice myDevice;
  @NotNull
  private final StackTraceExpander myStackTraceExpander;
  @NotNull
  private final LogcatListener myLogcatListener;
  private volatile boolean myCanceled;
  @SuppressWarnings("UnstableApiUsage")
  @NotNull
  private final Executor mySequentialExecutor =
    MoreExecutors.newSequentialExecutor(AppExecutorUtil.getAppExecutorService());
  @NotNull
  private final SafeAlarm myAlarm;

  // Save the last header and lines of the batch in case a log entry was split into 2 batches.
  @Nullable private LogCatHeader myPreviousHeader;
  @NotNull private List<String> myPreviousLines = new ArrayList<>();

  // Hold on to the last message in a batch to send if no new batch arrives in a reasonable time.
  @Nullable private LogCatMessage myPendingMessage;

  AndroidLogcatReceiver(@NotNull IDevice device, @NotNull LogcatListener listener) {
    myLogCatHeaderParser = new LogCatHeaderParser();
    myDevice = device;
    myStackTraceExpander = new StackTraceExpander(STACK_TRACE_LINE_PREFIX, STACK_TRACE_CAUSE_LINE_PREFIX);
    myLogcatListener = listener;
    myAlarm = new SafeAlarm(POOLED_THREAD, this);
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
   * <p>
   * There seems to be no way to deterministically detect the end of a log entry because the EOM is indicated by an empty line but an empty
   * line can be also be a valid part of a log entry. A valid header is a good indication that the previous entry has ended but we can't
   * just hold on to an entry until we get a new header because there could be a long pause between entries.
   * <p>
   * We address this issue by posting a delayed task that will flush the last entry in a batch. If we get another batch before the delayed
   * task executes, we cancel it.
   * <p>
   * To avoid multithreading issues, we serialize the processing of the batch with the delayed task by handling both in an {@link Alarm}
   * which runs on a sequentialized thread pool using {@code AppExecutorUtil.createBoundedScheduledExecutorService("...", 1)}
   */
  @Override
  protected void processNewLines(@NotNull List<String> newLines) {
    // Since we are eventually bound by the UI thread, we need to block in order to throttle the caller.
    executeAndWait(mySequentialExecutor, () -> {
      // New batch arrived so effectively cancel the pending request by resetting myPendingMessage
      myPendingMessage = null;

      // Parse new lines and send log messages
      Batch batch = parseNewLines(myPreviousHeader, myPreviousLines, newLines);
      for (LogCatMessage message : batch.myMessages) {
        myLogcatListener.onLogLineReceived(message);
      }

      // Save for next batch
      myPreviousHeader = batch.myLastHeader;
      myPreviousLines = batch.myLastLines;

      // If there is a valid last message in the batch, queue it for sending in case there is no imminent next batch coming
      if (myPreviousHeader != null && !myPreviousLines.isEmpty()) {
        myPendingMessage = new LogCatMessage(myPreviousHeader, joinLines(myPreviousLines));
        myAlarm.addRequest(this::processPendingMessage, DELAY_MILLIS);
      }
    });
  }

  private static void executeAndWait(Executor executor, Runnable action) {
    CountDownLatch latch = new CountDownLatch(1);
    executor.execute(() -> {
      try {
        action.run();
      }
      finally {
        latch.countDown();
      }
    });
    try {
      latch.await();
    }
    catch (InterruptedException ignored) {
    }
  }

  private void processPendingMessage() {
    mySequentialExecutor.execute(() -> {
      if (myPendingMessage != null) {
        myLogcatListener.onLogLineReceived(myPendingMessage);

        // reset all state variables.
        myPendingMessage = null;
        myPreviousHeader = null;
        myPreviousLines.clear();
      }
    });
  }

  /**
   * Parse a batch of new log lines into a {@link Batch}. A batch of lines can start and end with a partial log entry so this method
   * can be handed the header and lines of the last entry from the previous batch. The returned Batch object will all but the last log entry
   * in this batch as a list and the last entry as a header and lines.
   *
   * @param activeHeader the header of the last entry in the previous batch or null if this is the first batch
   * @param activeLines  the initial lines of the last entry from the previous batch
   * @param newLines     the lines in the batch to be parsed
   */
  @VisibleForTesting
  Batch parseNewLines(@Nullable LogCatHeader activeHeader, @NotNull List<String> activeLines, @NotNull List<String> newLines) {
    List<LogCatMessage> batchMessages = new ArrayList<>();
    for (String line : newLines) {
      line = fixLine(line);

      LogCatHeader header = myLogCatHeaderParser.parseHeader(line, myDevice);

      if (header != null) {
        // It's a header, flush active lines.
        myStackTraceExpander.reset();
        if (!activeLines.isEmpty()) {
          if (activeHeader != null) {
            batchMessages.add(new LogCatMessage(activeHeader, joinLines(activeLines)));
          }
          activeLines.clear();
        }
        activeHeader = header;
      }
      else {
        activeLines.addAll(myStackTraceExpander.process(line));
      }
    }
    return new Batch(batchMessages, activeHeader, activeLines);
  }

  // This method is package protected so other Logcat components can feed receiver processed log lines if they need to
  void notifyLogcatMessage(@NotNull LogCatHeader header, @NotNull String msg) {
    myLogcatListener.onLogLineReceived(new LogCatMessage(header, msg));
  }

  @NotNull
  private static String joinLines(@NotNull List<String> activeLines) {
    return StringUtil.trim(String.join("\n", activeLines), ch -> ch != '\n');
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

  /**
   * A batch consists of the first n-1 log entries in a batch. The last entry can be incomplete and is stored as a header and a list of
   * lines.
   */
  @VisibleForTesting
  static final class Batch {
    final List<LogCatMessage> myMessages;
    final LogCatHeader myLastHeader;
    final List<String> myLastLines;

    private Batch(List<LogCatMessage> messages, LogCatHeader header, List<String> lines) {
      myMessages = messages;
      myLastHeader = header;
      myLastLines = lines;
    }
  }

  @TestOnly
  void waitForIdle() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    mySequentialExecutor.execute(() -> myAlarm.addRequest(() -> mySequentialExecutor.execute(latch::countDown), DELAY_MILLIS));
    latch.await();
  }
}
