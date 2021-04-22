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
package com.android.tools.idea.logcat;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class AndroidLogcatServiceTest {
  private static final ImmutableList<String> LOG_LINES = ImmutableList.of(
    "[ 1534635551.439 1493:1595 W/DummyFirst     ]",
    "First Line1",
    "First Line2",
    "First Line3",
    "[ 1537486751.439 1493:1595 W/DummySecond     ]",
    "Second Line1"
  );

  private static final ImmutableList<LogCatMessage> EXPECTED_LOG_MESSAGES = ImmutableList.of(
    new LogCatMessage(
      new LogCatHeader(Log.LogLevel.WARN, 1493, 1595, "?", "DummyFirst", Instant.ofEpochMilli(1534635551439L)),
      "First Line1"),
    new LogCatMessage(
      new LogCatHeader(Log.LogLevel.WARN, 1493, 1595, "?", "DummyFirst", Instant.ofEpochMilli(1534635551439L)),
      "First Line2"),
    new LogCatMessage(
      new LogCatHeader(Log.LogLevel.WARN, 1493, 1595, "?", "DummyFirst", Instant.ofEpochMilli(1534635551439L)),
      "First Line3"),
    new LogCatMessage(
      new LogCatHeader(Log.LogLevel.WARN, 1493, 1595, "?", "DummySecond", Instant.ofEpochMilli(1537486751439L)),
      "Second Line1")
  );

  private static class TestLogcatListener implements AndroidLogcatService.LogcatListener {


    private final List<LogCatMessage> myReceivedMessages = new ArrayList<>();
    private boolean myCleared;

    @Override
    public void onLogLineReceived(@NotNull LogCatMessage logCatMessage) {
      myReceivedMessages.add(logCatMessage);
    }

    @Override
    public void onCleared() {
      myCleared = true;
      myReceivedMessages.clear();
    }

    public void reset() {
      myCleared = false;
      myReceivedMessages.clear();
    }

    public void assertAllReceived() {
      assertThat(myReceivedMessages).containsExactlyElementsIn(EXPECTED_LOG_MESSAGES);
    }

    public void assertNothingReceived() {
      assertEquals(0, myReceivedMessages.size());
    }

    private void assertCleared() {
      assertTrue(myCleared);
    }
  }

  private TestLogcatListener myLogcatListener;
  private final IDevice mockDevice = mock(IDevice.class);
  private AndroidLogcatService myLogcatService;
  private volatile CountDownLatch myExecuteShellCommandLatch;

  private String myBufferSize;
  private Project myProject;

  @Before
  public void setUp() throws Exception {
    stubExecuteLogcatHelp();
    stubExecuteLogcatVLongVEpoch();

    myLogcatService = new AndroidLogcatService();
    myLogcatListener = new TestLogcatListener();
    myExecuteShellCommandLatch = new CountDownLatch(2);

    myBufferSize = System.setProperty("idea.cycle.buffer.size", "disabled");
    myProject = mock(Project.class);
  }

  private void stubExecuteLogcatHelp() throws Exception {
    Answer<Void> answer = invocation -> {
      ((MultiLineReceiver)invocation.getArgument(1)).processNewLines(new String[]{"epoch"});
      myExecuteShellCommandLatch.countDown();

      return null;
    };

    doAnswer(answer).when(mockDevice).executeShellCommand(eq("logcat --help"), any(), eq(10L), eq(TimeUnit.SECONDS));
  }

  private void stubExecuteLogcatVLongVEpoch() throws Exception {
    Answer<Void> answer = invocation -> {
      AndroidLogcatReceiver receiver = invocation.getArgument(1);

      for (String line : LOG_LINES) {
        receiver.processNewLine(line);
      }
      receiver.cancel();

      myExecuteShellCommandLatch.countDown();
      return null;
    };

    doAnswer(answer).when(mockDevice).executeShellCommand(eq("logcat -v long -v epoch"), any(), eq(0L), eq(TimeUnit.MILLISECONDS));
  }

  @After
  public void tearDown() {
    Disposer.dispose(myProject);

    if (myBufferSize != null) {
      System.setProperty("idea.cycle.buffer.size", myBufferSize);
    }
    else {
      System.clearProperty("idea.cycle.buffer.size");
    }
  }

  @Test
  public void testDeviceConnectedAfterListening() throws Exception {
    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.addListener(mockDevice, myLogcatListener);
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();
  }

  /**
   * Tests {@link AndroidLogcatService} to make sure that it will receive logs from devices which are connected
   * prior to it's initialization. AndroidLogcatService will start receiving logs in this devices whenever some listener starts listening
   */
  @Test
  public void testDeviceConnectedBeforeListening() throws Exception {
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();
  }

  /**
   * Tests {@link AndroidLogcatService#addListener(IDevice, AndroidLogcatService.LogcatListener, boolean)},
   * to make sure that it adds correctly old logs from buffer
   */
  @Test
  public void testAddOldLogs() throws Exception {
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);

    myExecuteShellCommandLatch.await();

    myLogcatListener.assertAllReceived();
  }

  @Test
  public void testDeviceDisconnectedAndConnected() throws Exception {
    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.addListener(mockDevice, myLogcatListener);

    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.deviceDisconnected(mockDevice);

    myExecuteShellCommandLatch = new CountDownLatch(2);
    myLogcatListener.reset();

    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();
  }

  @Test
  public void testDeviceChanged() throws Exception {

    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);
    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    myExecuteShellCommandLatch = new CountDownLatch(2);
    myLogcatListener.reset();
    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.deviceChanged(mockDevice, 0);
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceChanged(mockDevice, 0);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();
  }

  @Test
  public void testRemoveListener() throws Exception {
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);
    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    myLogcatService.removeListener(mockDevice, myLogcatListener);
    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.deviceDisconnected(mockDevice);

    // Try to reconnect and make sure that it received nothing
    myExecuteShellCommandLatch = new CountDownLatch(2);
    myLogcatListener.reset();
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    TestLogcatListener otherListener = new TestLogcatListener();
    myLogcatService.addListener(mockDevice, otherListener);

    myExecuteShellCommandLatch.await();
    otherListener.assertAllReceived();
    myLogcatListener.assertNothingReceived();
  }

  /**
   * Tests {@link AndroidLogcatService} to verify that when no one is interested in a device logs, AndroidLogcatService should
   * stop receiving logs from the device. Subsequently, if a listener will be interested in the device, it should receive all logs
   */
  @Test
  public void testNoDeviceListener() throws Exception {
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    myLogcatService.removeListener(mockDevice, myLogcatListener);
    myExecuteShellCommandLatch = new CountDownLatch(2);
    myLogcatListener.reset();
    myLogcatService.addListener(mockDevice, myLogcatListener, false);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();
  }

  @Test
  public void consoleGetsClearedWhenDeviceIsDisconnected() {
    myLogcatService.addListener(mockDevice, myLogcatListener);
    myLogcatService.clearLogcat(mockDevice, myProject);

    myLogcatListener.assertCleared();
  }
}
