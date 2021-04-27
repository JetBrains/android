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

import static com.android.ddmlib.Log.LogLevel.ERROR;
import static com.android.ddmlib.Log.LogLevel.INFO;
import static com.android.ddmlib.Log.LogLevel.WARN;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.Map;
import org.easymock.EasyMock;
import org.junit.Test;

public class AndroidLogcatReceiverTest {
  private static final String LOG_LINE_1 = "Log line 1";
  private static final String LOG_LINE_2 = "Log line 2";
  private static final String LOG_LINE_3 = "Log line 3";
  private static final String TAG_1 = "Tag1";
  private static final String TAG_2 = "Tag2";
  private static final int SECONDS_1 = 1534635551;
  private static final int SECONDS_2 = 1516739696;
  private static final int MILLISECONDS_1 = 439;
  private static final int MILLISECONDS_2 = 789;
  private static final int PID_1 = 1493;
  private static final int PID_2 = 11698;
  private static final int PID_UNKNOWN = 99;
  private static final int TID_1 = 1595;
  private static final int TID_2 = 231;
  private static final String APP_NAME_1 = "com.client.name";
  private static final String APP_NAME_2 = "com.android.chattylogger";

  private final static ImmutableMap<Integer, String> APPS = new ImmutableMap.Builder<Integer, String>()
    .put(PID_1, APP_NAME_1)
    .put(PID_2, APP_NAME_2)
    .build();

  private final TestFormattedLogcatReceiver myLogcatListener = new TestFormattedLogcatReceiver();
  private final AndroidLogcatReceiver myReceiver = new AndroidLogcatReceiver(createMockDevice(), myLogcatListener);

  @Test
  public void processNewLines_simpleLogEntry() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      LOG_LINE_1,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_1));
  }

  @Test
  public void processNewLines_unknownPid_appIsQuestionMark() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_UNKNOWN, String.valueOf(TID_1), WARN, TAG_1),
      LOG_LINE_1,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_UNKNOWN, TID_1, "?", TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_1));
  }

  @Test
  public void processNewLines_multiLine() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      LOG_LINE_1,
      LOG_LINE_2,
      LOG_LINE_3,
      "",
      formatLogHeader(SECONDS_2, MILLISECONDS_2, PID_2, String.valueOf(TID_2), ERROR, TAG_2),
      LOG_LINE_1,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        String.join("\n", LOG_LINE_1, LOG_LINE_2, LOG_LINE_3)),
      new LogCatMessage(
        new LogCatHeader(ERROR, PID_2, TID_2, APP_NAME_2, TAG_2, Instant.ofEpochSecond(SECONDS_2, MILLISECONDS.toNanos(MILLISECONDS_2))),
        LOG_LINE_1));
  }

  @Test
  public void processNewLines_embeddedEmptyLines() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      LOG_LINE_1,
      "",
      LOG_LINE_2,
      "",
      "",
      LOG_LINE_3,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        String.join("\n", LOG_LINE_1, "", LOG_LINE_2, "", "", LOG_LINE_3)));
  }

  @Test
  public void processNewLines_trimOuterEmptyLines() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      "",
      "",
      "",
      LOG_LINE_1,
      "",
      "",
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_1));
  }

  @Test
  public void processNewLines_withHexTid() {
    String hexTid = "0x1ef";
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, hexTid, WARN, TAG_1),
      LOG_LINE_1,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(
          WARN, PID_1,
          Integer.parseInt(hexTid.substring(2), 16),
          APP_NAME_1,
          TAG_1,
          Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_1));
  }

  @Test
  public void processNewLines_allLogLevels() {
    for (LogLevel logLevel : LogLevel.values()) {
      myReceiver.processNewLines(new String[]{
        formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), logLevel, TAG_1),
        LOG_LINE_1,
        "",
      });

      assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
        new LogCatMessage(
          new LogCatHeader(
            logLevel,
            PID_1,
            TID_1,
            APP_NAME_1,
            TAG_1,
            Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
          LOG_LINE_1));
      myLogcatListener.onCleared();
    }
  }

  @Test
  public void processNewLines_ignoreLinesBeforeFirstHeader() {
    myReceiver.processNewLines(new String[]{
      "ignore this line",
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      LOG_LINE_1,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_1));
  }

  @Test
  public void processNewLines_twoBatches_messagesAligned() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      LOG_LINE_1,
      "",
    });

    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_2, MILLISECONDS_2, PID_2, String.valueOf(TID_2), INFO, TAG_2),
      LOG_LINE_2,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_1),
      new LogCatMessage(
        new LogCatHeader(INFO, PID_2, TID_2, APP_NAME_2, TAG_2, Instant.ofEpochSecond(SECONDS_2, MILLISECONDS.toNanos(MILLISECONDS_2))),
        LOG_LINE_2)
    );
  }

  @Test
  public void processNewLines_twoBatches_messagesSplitOnHeader() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
    });

    myReceiver.processNewLines(new String[]{
      LOG_LINE_1,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_1));
  }

  @Test
  public void processNewLines_twoBatches_messagesSplitBetweenLines() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      LOG_LINE_1,
    });

    myReceiver.processNewLines(new String[]{
      LOG_LINE_2,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        String.join("\n", LOG_LINE_1, LOG_LINE_2)));
  }

  @Test
  public void processNewLines_twoBatches_messagesSplitBeforeEmptyLine() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      LOG_LINE_1,
      LOG_LINE_2,
    });

    myReceiver.processNewLines(new String[]{
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        String.join("\n", LOG_LINE_1, LOG_LINE_2)));
  }

  /**
   * This is a compromise on a very rare use case where a user logs an empty line (already rare) and that line falls
   * exactly at the end of a batch. This forces us to log the partial message assuming it's done only to find later
   * on that there's more lines.
   */
  @Test
  public void processNewLines_twoBatches_messagesWithEmptyLine() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      LOG_LINE_1,
      "",
    });

    myReceiver.processNewLines(new String[]{
      LOG_LINE_2,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_1),
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_2)
    );
  }

  /**
   * If the first line of a split message is empty, we can assume it's a user emmited line.
   */
  @Test
  public void processNewLines_twoBatches_messagesWithFirstLineEmpty() {
    myReceiver.processNewLines(new String[]{
      formatLogHeader(SECONDS_1, MILLISECONDS_1, PID_1, String.valueOf(TID_1), WARN, TAG_1),
      "",
    });

    myReceiver.processNewLines(new String[]{
      LOG_LINE_1,
      "",
    });

    assertThat(myLogcatListener.getLogCatMessages()).containsExactly(
      new LogCatMessage(
        new LogCatHeader(WARN, PID_1, TID_1, APP_NAME_1, TAG_1, Instant.ofEpochSecond(SECONDS_1, MILLISECONDS.toNanos(MILLISECONDS_1))),
        LOG_LINE_1)
    );
  }

  // TODO(aalbert): Add a test for stack trace with "... n more"

  /**
   * Create a header as generated by logcat -v long,epoch. For example:
   * <p>
   * [ 1534635551.439 1493:1595 W/EDMNativeHelper     ]
   */
  private static String formatLogHeader(int seconds, int milliseconds, int pid, String tid, LogLevel level, String tag) {
    return String.format("[ %d.%d %d:%s %s/%-20s]", seconds, milliseconds, pid, tid, level.getPriorityLetter(), tag);
  }

  /**
   * Helper method that creates a mock device.
   */
  private static IDevice createMockDevice() {
    IDevice d = EasyMock.createMock(IDevice.class);
    for (Map.Entry<Integer, String> entry : APPS.entrySet()) {
      EasyMock.expect(d.getClientName(entry.getKey())).andStubReturn(entry.getValue());
    }
    EasyMock.expect(d.getClientName(EasyMock.anyInt())).andStubReturn("?");
    EasyMock.replay(d);
    return d;
  }
}
